package com.simplestructurescanner.structure.pillar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.validation.StructureValidationWorld;
import com.simplestructurescanner.validation.ValidationChunkProvider;
import com.simplestructurescanner.validation.ValidationContextManager;

/**
 * Structure provider for Pillar mod structures.
 * <p>
 * This provider uses predictive generation to locate Pillar structures
 * in ungenerated chunks. It replicates Pillar's generation algorithm
 * to predict where structures will spawn.
 */
public class PillarStructureProvider implements StructureProvider {

    private static final String PILLAR_MODID = "pillar";
    private static final int SEARCH_RADIUS = 3000; // Increased for testing Pillar predictions

    // PERFORMANCE TIME LIMIT to prevent indefinite freeze
    private static final long MAX_SCAN_TIME_MS = 30000; // Maximum time to spend scanning (30 seconds)

    private List<ResourceLocation> structureIds = null;
    private Map<ResourceLocation, PillarSchemaProxy> schemaMap = null;

    @Override
    public String getProviderId() {
        return "pillar";
    }

    @Override
    public String getModName() {
        return "Pillar";
    }

    @Override
    public boolean isAvailable() {
        return PillarIntegration.isPillarLoaded();
    }

    @Override
    public void postInit() {
        if (!isAvailable()) {
            return;
        }

        initializeStructures();
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        if (!isAvailable()) {
            return new ArrayList<>();
        }

        if (structureIds == null) {
            SimpleStructureScanner.LOGGER.warn("Pillar structure IDs requested before postInit was called");
            return new ArrayList<>();
        }

        return structureIds;
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        if (!isAvailable()) {
            return false;
        }

        // Must be from pillar mod
        if (!PILLAR_MODID.equals(structureId.getNamespace())) {
            return false;
        }

        // Check if it exists in our schema map
        return schemaMap != null && schemaMap.containsKey(structureId);
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        if (!isAvailable() || schemaMap == null) {
            return null;
        }

        PillarSchemaProxy schema = schemaMap.get(structureId);
        if (schema == null) {
            return null;
        }

        // Create basic StructureInfo
        // We don't have detailed info like blocks/entities/loot without actually
        // loading the structure, so we provide a minimal info object
        return new StructureInfo(
                structureId,
                schema.structureName,
                "Pillar",
                0, 0, 0  // Unknown size
        );
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount) {
        if (!isAvailable() || schemaMap == null) {
            return null;
        }

        PillarSchemaProxy schema = schemaMap.get(structureId);
        if (schema == null) {
            SimpleStructureScanner.LOGGER.warn("Unknown Pillar structure: {}", structureId);
            return null;
        }

        // Convert ResourceLocation to structure name
        String structureName = structureId.getPath();

        // Get the current dimension ID for dimension-specific cache clearing
        int dimensionId = world.provider.getDimension();

        // Search in expanding rings
        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;
        int maxRadius = SEARCH_RADIUS >> 4; // Convert to chunks

        // Track found positions for minDistance check
        Set<BlockPos> foundPositions = new HashSet<>();

        int chunksSearched = 0;
        int foundCount = 0;
        long startTime = System.currentTimeMillis();

        try {
            // Spiral search outward from player
            for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Skip inner chunks (we've already checked them)
                    if (radius > 0 && dx == -radius && dz == -radius) {
                        continue; // Will be handled in previous iteration
                    }
                    if (radius > 0 && dx > -radius && dx < radius && dz > -radius && dz < radius) {
                        continue; // Skip interior
                    }

                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;

                    // CHUNK GENERATION DISABLED FOR PERFORMANCE
                    // Generating chunks during scan causes significant lag and crashes in heavy modpacks
                    // The validation world can predict terrain without loading real chunks
                    // If you need real world comparison, re-enable this code:
                    /*
                    World serverWorld = getServerWorld(world);
                    if (serverWorld != null && !serverWorld.getChunkProvider().isChunkGeneratedAt(chunkX, chunkZ)) {
                        net.minecraft.world.gen.ChunkProviderServer chunkProviderServer =
                            (net.minecraft.world.gen.ChunkProviderServer) serverWorld.getChunkProvider();
                        chunkProviderServer.loadChunk(chunkX, chunkZ);
                    }
                    */

                    chunksSearched++;

                    // ENFORCE TIME LIMIT - abort scan if taking too long (safety net to prevent indefinite freeze)
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - startTime;
                    if (elapsed > MAX_SCAN_TIME_MS) {
                        SimpleStructureScanner.LOGGER.warn("Search interrupted: Exceeded maximum time limit ({}ms) to prevent game freeze",
                            MAX_SCAN_TIME_MS);
                        SimpleStructureScanner.LOGGER.warn("Searched {} chunks in {}ms before timeout",
                            chunksSearched, elapsed);
                        SimpleStructureScanner.LOGGER.warn("This indicates a performance issue - structure validation is too slow");
                        return null;
                    }

                    // Clear chunk cache periodically to prevent unbounded memory growth
                    if (chunksSearched % 500 == 0) {
                        int cachedChunks = ValidationContextManager.getTotalCachedChunkCount();
                        if (cachedChunks > 500) {
                            ValidationContextManager.clearDimensionCache(dimensionId);
                        }
                    }

                    // Predict if this structure would spawn here
                    BlockPos predictedPos = PillarStructurePredictor.predictStructureInChunk(
                            world, chunkX, chunkZ, structureName, foundPositions);

                    if (predictedPos != null) {
                        if (foundCount >= skipCount) {
                            // This is the one we want
                            StructureLocation location = new StructureLocation(predictedPos, foundCount, foundCount + 1);
                            return location;
                        }

                        // Skip this one, but remember it for minDistance checks
                        foundPositions.add(predictedPos);
                        foundCount++;
                    }
                }
            }
        }

        // No structure found within search radius
        SimpleStructureScanner.LOGGER.warn("NO '{}' found within search radius after searching {} chunks in {}ms",
            structureName, chunksSearched, System.currentTimeMillis() - startTime);
        return null;

        } finally {
            // Clear chunk cache after scan completes
            StructureValidationWorld validationWorld = ValidationContextManager.getValidationWorldByDimension(dimensionId);
            if (validationWorld != null && validationWorld.getChunkProvider() instanceof ValidationChunkProvider) {
                int cachedChunks = ((ValidationChunkProvider) validationWorld.getChunkProvider()).getCachedChunkCount();
                if (cachedChunks > 0) {
                    ValidationContextManager.clearDimensionCache(dimensionId);
                }
            }
        }
    }

    /**
     * Initialize structure list and schema map from Pillar.
     */
    private void initializeStructures() {
        Map<String, PillarSchemaProxy> schemas = PillarIntegration.getSchemas();

        if (schemas == null || schemas.isEmpty()) {
            structureIds = new ArrayList<>();
            schemaMap = new LinkedHashMap<>();
            return;
        }

        structureIds = new ArrayList<>();
        schemaMap = new LinkedHashMap<>();

        for (Map.Entry<String, PillarSchemaProxy> entry : schemas.entrySet()) {
            ResourceLocation id = new ResourceLocation(PILLAR_MODID, entry.getKey());
            structureIds.add(id);
            schemaMap.put(id, entry.getValue());
        }
    }
}
