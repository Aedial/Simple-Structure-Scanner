package com.simplestructurescanner.structure.pillar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntSortedSet;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.structure.StructureSearchOverrides;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;


/**
 * Structure provider for Pillar mod structures.
 * <p>
 * This provider uses predictive generation to locate Pillar structures
 * in ungenerated chunks. It replicates Pillar's generation algorithm
 * to predict where structures will spawn.
 * <p>
 * Pillar structures are defined by JSON schema files that control where
 * and how structures generate. This provider reads those schemas via
 * reflection and replicates the generation decisions without touching
 * the real world.
 */
public class PillarStructureProvider implements StructureProvider {

    private static final String PROVIDER_ID = "pillar";
    private static final String MOD_ID = "pillar";
    private static final int CHUNK_COORDINATE_SHIFT = 4;
    private static final int CHUNK_CACHE_MAINTENANCE_INTERVAL = 500;
    private static final int MAX_CACHED_VALIDATION_CHUNKS = 500;

    // Max search radius in blocks (1024 = 64 chunks)
    private static final int SEARCH_RADIUS = 1024;
    // Max time to spend searching in milliseconds before giving up
    private static final long MAX_SCAN_TIME_MS = 10000;

    private List<ResourceLocation> structureIds = null;
    private Map<ResourceLocation, PillarSchemaProxy> schemaMap = null;
    private final Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    /**
     * Schemas in Pillar's natural {@code Object2ObjectOpenHashMap} iteration order.
     * Captured once during {@link #loadSchemas()} so the predictor's shuffle
     * produces the same result as Pillar's {@code WorldGenerator.generate()}.
     */
    private List<PillarSchemaProxy> schemasInOrder = null;

    private float rarityMultiplier = 1.0f;
    private int maxStructuresInOneChunk = 1;

    private static final class ScanOutcome {
        private final List<BlockPos> positions;
        private final int chunksSearched;
        private final boolean timedOut;

        private ScanOutcome(List<BlockPos> positions, int chunksSearched, boolean timedOut) {
            this.positions = positions;
            this.chunksSearched = chunksSearched;
            this.timedOut = timedOut;
        }
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        return "gui.structurescanner.providers.pillar";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        loadPillarConfig();
        loadSchemas();
        populateStructureMetadata();
        populateStructureContents();
    }

    @Nullable
    private PillarSchemaProxy getSchema(ResourceLocation structureId) {
        if (schemaMap == null) return null;

        return schemaMap.get(structureId);
    }

    @Nullable
    private static Set<Biome> resolveBiomes(PillarSchemaProxy schema) {
        if (schema.generateEverywhere) return null;

        Set<Biome> biomes = new HashSet<>();

        // StructureInfo only stores allow-lists, so blacklist-based Pillar rules
        // must be expanded against the live biome registry instead of displayed raw.
        for (Biome biome : Biome.REGISTRY) {
            if (biome != null && schema.canSpawnInBiome(biome)) biomes.add(biome);
        }

        return biomes;
    }

    @Nullable
    private static Set<DimensionInfo> resolveDimensions(PillarSchemaProxy schema) {
        if (schema.generateEverywhere || schema.dimensionSpawns.isEmpty()) return null;

        Set<DimensionInfo> dimensions = new HashSet<>();

        if (!schema.isDimensionSpawnsBlacklist) {
            for (Integer dimId : schema.dimensionSpawns) {
                dimensions.add(new DimensionInfo(dimId));
            }

            return dimensions;
        }

        // Dimension blacklists need the same expansion as biome blacklists so the
        // UI and search gating see the actual allowed dimensions.
        for (IntSortedSet dimensionIds : DimensionManager.getRegisteredDimensions().values()) {
            for (int dimensionId : dimensionIds) {
                if (schema.canSpawnInDimension(dimensionId)) dimensions.add(new DimensionInfo(dimensionId));
            }
        }

        return dimensions;
    }

    private void populateStructureMetadata() {
        for (Map.Entry<ResourceLocation, PillarSchemaProxy> entry : schemaMap.entrySet()) {
            ResourceLocation id = entry.getKey();
            PillarSchemaProxy schema = entry.getValue();

            StructureInfo info = structureInfos.get(id);
            if (info == null) continue;

            // Set rarity based on schema rarity value
            long rounded = Math.round(schema.rarity);
            info.setRarity(LocalizedText.translatable("gui.structurescanner.rarity",
                LocalizedText.translatable("gui.structurescanner.rarity.one_in_chunks", rounded)));

            Set<Biome> biomes = resolveBiomes(schema);
            if (biomes != null && !biomes.isEmpty()) info.setValidBiomes(biomes);

            Set<DimensionInfo> dimensions = resolveDimensions(schema);
            if (dimensions != null) {
                info.setValidDimensions(dimensions);
            } else if (schema.generatorType == PillarGeneratorType.NONE) {
                // Summon-only Pillar schemas do not have a natural generation dimension,
                // so the scanner should treat missing dimension data as unknown, not Any.
                info.setValidDimensions(Collections.emptySet());
            }

            // TODO: Add generator type info when setNotes() is added to StructureInfo
            // Generator type: schema.generatorType.name().toLowerCase()
        }
    }

    /**
     * Populate structure contents (blocks, layers, entities, loot tables) by parsing NBT files.
     */
    private void populateStructureContents() {
        for (Map.Entry<ResourceLocation, PillarSchemaProxy> entry : schemaMap.entrySet()) {
            ResourceLocation id = entry.getKey();
            PillarSchemaProxy schema = entry.getValue();

            StructureInfo info = structureInfos.get(id);
            if (info == null) continue;

            StructureNBTParser.ParsedStructure parsed = PillarNBTParser.parseStructure(schema.structureName);
            if (parsed == null) {
                SimpleStructureScanner.LOGGER.debug("Failed to parse Pillar structure NBT for: {}", schema.structureName);
                continue;
            }

            // Set blocks, layers, entities, and loot tables
            if (!parsed.blocks.isEmpty()) info.setBlocks(parsed.blocks);
            if (!parsed.layers.isEmpty()) info.setLayers(parsed.layers);
            if (!parsed.entities.isEmpty()) info.setEntities(parsed.entities);
            if (!parsed.lootTables.isEmpty()) info.setLootTables(parsed.lootTables);
        }
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        if (structureIds == null) {
            SimpleStructureScanner.LOGGER.warn("Pillar structure IDs requested before postInit was called");

            return new ArrayList<>();
        }

        return structureIds;
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        PillarSchemaProxy schema = getSchema(structureId);
        if (schema == null) return false;

        return schema.generatorType != PillarGeneratorType.NONE;
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        PillarSchemaProxy schema = getSchema(structureId);
        if (schema == null) {
            SimpleStructureScanner.LOGGER.warn("Unknown Pillar structure: {}", structureId);

            return null;
        }

        ScanOutcome scanOutcome = scanStructure(world, schema, pos, Integer.MAX_VALUE);
        if (scanOutcome.timedOut) {
            SimpleStructureScanner.LOGGER.warn(
                    "Search interrupted: Exceeded maximum time limit ({}ms). Searched {} chunks.",
                    MAX_SCAN_TIME_MS, scanOutcome.chunksSearched);

            return null;
        }

        List<BlockPos> allCandidates = scanOutcome.positions;

        if (allCandidates.isEmpty()) return null;

        PositionHelper.sortByHorizontalDistance(allCandidates, pos);

        int validIndex = 0;
        int totalValid = 0;
        BlockPos targetPos = null;

        for (BlockPos candidate : allCandidates) {
            if (locationFilter != null && !locationFilter.test(candidate)) continue;

            if (validIndex == skipCount && targetPos == null) targetPos = candidate;

            validIndex++;
            totalValid++;
        }

        if (targetPos == null) return null;

        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, totalValid, yAgnostic);
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        PillarSchemaProxy schema = getSchema(structureId);
        if (schema == null) return null;

        ScanOutcome scanOutcome = scanStructure(world, schema, pos, maxResults);
        if (scanOutcome.timedOut) return scanOutcome.positions;

        List<BlockPos> results = scanOutcome.positions;

        PositionHelper.sortByHorizontalDistance(results, pos);

        return results;
    }

    private ScanOutcome scanStructure(World world, PillarSchemaProxy schema, BlockPos pos, int maxResults) {
        World generationWorld = ValidationContextManager.getGenerationWorld(world);

        return scanCandidates(generationWorld, pos, schema.structureName, maxResults);
    }

    /**
     * Scan chunks in the same spiral order used by the public search methods.
     * Timeout handling is returned to the caller so each public API keeps its
     * existing behavior: nearest logs and returns null, while nearby returns
     * the partial unsorted list gathered so far.
     */
    private ScanOutcome scanCandidates(World generationWorld, BlockPos pos, String structureName, int maxResults) {
        int dimensionId = generationWorld.provider.getDimension();
        int playerChunkX = pos.getX() >> CHUNK_COORDINATE_SHIFT;
        int playerChunkZ = pos.getZ() >> CHUNK_COORDINATE_SHIFT;
        int maxRadius = SEARCH_RADIUS >> CHUNK_COORDINATE_SHIFT;

        Set<BlockPos> foundPositions = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        int chunksSearched = 0;

        try {
            for (int radius = 0; radius <= maxRadius && results.size() < maxResults; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (shouldSkipChunk(radius, dx, dz)) continue;

                        int chunkX = playerChunkX + dx;
                        int chunkZ = playerChunkZ + dz;
                        chunksSearched++;

                        if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                            return new ScanOutcome(results, chunksSearched, true);
                        }

                        maintainValidationCache(dimensionId, chunksSearched);

                        BlockPos predictedPos = PillarStructurePredictor.predictStructureInChunk(
                                generationWorld, chunkX, chunkZ, structureName, foundPositions,
                                schemasInOrder, rarityMultiplier, maxStructuresInOneChunk);

                        if (predictedPos == null) continue;

                        foundPositions.add(predictedPos);
                        results.add(predictedPos);
                    }
                }
            }
        } finally {
            ValidationContextManager.clearDimensionCache(dimensionId);
        }

        return new ScanOutcome(results, chunksSearched, false);
    }

    private static boolean shouldSkipChunk(int radius, int dx, int dz) {
        return radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius;
    }

    private static void maintainValidationCache(int dimensionId, int chunksSearched) {
        if (chunksSearched % CHUNK_CACHE_MAINTENANCE_INTERVAL != 0) return;

        int cachedChunks = ValidationContextManager.getTotalCachedChunkCount();
        if (cachedChunks > MAX_CACHED_VALIDATION_CHUNKS) {
            ValidationContextManager.clearDimensionCache(dimensionId);
        }
    }

    // ========== Schema & Config Loading ==========

    /**
     * Load Pillar's global config values via reflection.
     */
    private void loadPillarConfig() {
        try {
            Class<?> pillarClass = ReflectionHelper.loadClassRequired("vazkii.pillar.Pillar");
            rarityMultiplier = ReflectionHelper.getFloatField(null, pillarClass, "rarityMultiplier");
            maxStructuresInOneChunk = ReflectionHelper.getIntField(null, pillarClass, "maxStructuresInOneChunk");
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.error("Failed to load Pillar config", e);
        }
    }

    /**
     * Load all Pillar structure schemas via reflection and build the structure lists.
     * <p>
     * <b>IMPORTANT:</b> This method captures Pillar's natural map iteration order.
     * Pillar uses {@code Object2ObjectOpenHashMap} internally, whose iteration order
     * is deterministic for a given map state but not insertion-ordered. We capture
     * this order once in {@link #schemasInOrder} so that the predictor's shuffle
     * produces the same result as Pillar's {@code WorldGenerator}, which iterates
     * {@code loadedSchemas.values()} and shuffles.
     */
    @SuppressWarnings("unchecked")
    private void loadSchemas() {
        schemasInOrder = new ArrayList<>();
        structureIds = new ArrayList<>();
        schemaMap = new LinkedHashMap<>();
        structureInfos.clear();

        try {
            Class<?> structureLoaderClass = ReflectionHelper.loadClassRequired("vazkii.pillar.StructureLoader");
            Map<String, Object> pillarSchemas = (Map<String, Object>) ReflectionHelper.getStaticField(
                    structureLoaderClass, "loadedSchemas");

            if (pillarSchemas == null) {
                SimpleStructureScanner.LOGGER.warn("Pillar loadedSchemas is null");

                return;
            }

            for (Object schemaObj : pillarSchemas.values()) {
                try {
                    registerSchema(createSchemaProxy(schemaObj));
                } catch (ReflectionException e) {
                    SimpleStructureScanner.LOGGER.error("Failed to create proxy for schema", e);
                }
            }

            SimpleStructureScanner.LOGGER.info("Loaded {} Pillar schemas", schemaMap.size());
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.error("Failed to access Pillar schemas", e);
        }
    }

    private void registerSchema(PillarSchemaProxy schema) {
        ResourceLocation id = new ResourceLocation(MOD_ID, schema.structureName);
        boolean hidden = StructureSearchOverrides.isStructureHidden(PROVIDER_ID, id);

        schemasInOrder.add(schema);
        if (!hidden && !schemaMap.containsKey(id)) structureIds.add(id);

        schemaMap.put(id, schema);
        if (hidden) return;

        structureInfos.put(id, createStructureInfo(id, schema));
    }

    private StructureInfo createStructureInfo(ResourceLocation id, PillarSchemaProxy schema) {
        return new StructureInfo(id, LocalizedText.translatable("structure.pillar." + schema.structureName), PROVIDER_ID, 0, 0, 0);
    }

    // ========== Reflection Helpers ==========

    /**
     * Create a {@link PillarSchemaProxy} from a Pillar StructureSchema instance.
     */
    @SuppressWarnings("unchecked")
    private static PillarSchemaProxy createSchemaProxy(Object pillarSchema) throws ReflectionException {
        Class<?> cls = pillarSchema.getClass();

        String structureName = ReflectionHelper.getStringField(pillarSchema, cls, "structureName");
        Object generatorTypeObj = ReflectionHelper.getField(pillarSchema, cls, "generatorType");
        int maxY = ReflectionHelper.getIntField(pillarSchema, cls, "maxY");
        int minY = ReflectionHelper.getIntField(pillarSchema, cls, "minY");
        int rarity = ReflectionHelper.getIntField(pillarSchema, cls, "rarity");
        int minDistance = ReflectionHelper.getIntField(pillarSchema, cls, "minDistanceToSameTypeStructures");
        boolean generateEverywhere = ReflectionHelper.getBooleanField(pillarSchema, cls, "generateEverywhere");

        List<Integer> dimensionSpawns = ReflectionHelper.getListField(pillarSchema, cls, "dimensionSpawns");
        List<String> biomeNameSpawns = ReflectionHelper.getListField(pillarSchema, cls, "biomeNameSpawns");
        List<String> biomeTagSpawns = ReflectionHelper.getListField(pillarSchema, cls, "biomeTagSpawns");

        boolean isDimBlacklist = ReflectionHelper.getBooleanField(pillarSchema, cls, "isDimensionSpawnsBlacklist");
        boolean isBiomeNameBlacklist = ReflectionHelper.getBooleanField(pillarSchema, cls, "isBiomeNameSpawnsBlacklist");
        boolean isBiomeTagBlacklist = ReflectionHelper.getBooleanField(pillarSchema, cls, "isBiomeTagSpawnsBlacklist");

        PillarGeneratorType generatorType = convertGeneratorType(generatorTypeObj);

        return new PillarSchemaProxy(
                structureName, generatorType,
                maxY, minY, rarity, minDistance,
                dimensionSpawns, biomeNameSpawns, biomeTagSpawns,
                isDimBlacklist, isBiomeNameBlacklist, isBiomeTagBlacklist,
                generateEverywhere);
    }

    /**
     * Convert a Pillar {@code GeneratorType} enum constant to our proxy enum.
     */
    private static PillarGeneratorType convertGeneratorType(Object pillarGeneratorType) {
        if (pillarGeneratorType == null) return PillarGeneratorType.NONE;

        String name = pillarGeneratorType.toString();

        try {
            return PillarGeneratorType.valueOf(name);
        } catch (IllegalArgumentException e) {
            SimpleStructureScanner.LOGGER.warn("Unknown Pillar GeneratorType: {}", name);

            return PillarGeneratorType.NONE;
        }
    }
}
