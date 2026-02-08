package com.simplestructurescanner.structure.pillar;

import com.simplestructurescanner.SimpleStructureScanner;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * A fake world used for validating structure placement with actual terrain data.
 *
 * This world:
 * - Generates real chunks with terrain using the provided chunk generator
 * - Provides all world methods Pillar needs for structure placement validation
 * - Does not save any data to disk
 * - Is isolated from the real game world
 *
 * Use this world to:
 * - Predict accurate Y-coordinates for structures
 * - Validate terrain conditions (liquids, sky visibility, etc.)
 * - Filter out structures that would fail to spawn
 *
 * NOTE: This world must be properly cleaned up when done to prevent memory leaks.
 * Use ValidationContextManager to manage the lifecycle.
 */
public class StructureValidationWorld extends World {

    private static final WorldSettings DEFAULT_SETTINGS = new WorldSettings(
        1L, GameType.CREATIVE, false, false, WorldType.DEFAULT
    );

    private static Field biomeProviderField;

    static {
        // Use reflection to access the biomeProvider field from World
        // Try multiple possible field names (deobfuscated and various obfuscated versions)
        String[] possibleNames = {
            "biomeProvider",  // Deobfuscated/MCP
            "field_72961_K",  // 1.12.2 obfuscated (older mappings)
            "field_201672_v", // 1.12+ obfuscated (newer mappings)
            "field_184135_t"  // Alternative obfuscated name
        };

        for (String fieldName : possibleNames) {
            try {
                biomeProviderField = World.class.getDeclaredField(fieldName);
                biomeProviderField.setAccessible(true);
                SimpleStructureScanner.LOGGER.info("Successfully accessed biomeProvider field as: {}", fieldName);
                break;
            } catch (NoSuchFieldException e) {
                // Try next name
            }
        }

        if (biomeProviderField == null) {
            // If all names fail, log warning but don't crash
            // The validation will still work, just without forced BiomeProvider synchronization
            SimpleStructureScanner.LOGGER.warn("Could not find biomeProvider field in World class using any known name.");
            SimpleStructureScanner.LOGGER.warn("BiomeProvider synchronization will not be available - validation may have biome mismatches.");
        }
    }

    private final IChunkGenerator chunkGenerator;
    private final long worldSeed;

    /**
     * Creates a new structure validation world.
     *
     * IMPORTANT: This constructor copies the real world's WorldInfo to ensure
     * identical terrain generation. Using a new WorldInfo with WorldType.DEFAULT
     * causes terrain height mismatches.
     *
     * @param worldInfo The WorldInfo from the real world (contains terrain generation settings)
     * @param biomeProvider The biome provider from the real world
     * @param worldProvider The world provider from the real world
     * @param chunkGenerator The chunk generator from the real world
     */
    public StructureValidationWorld(
        WorldInfo worldInfo,
        BiomeProvider biomeProvider,
        WorldProvider worldProvider,
        IChunkGenerator chunkGenerator
    ) {
        super(
            new ValidationSaveHandler(),
            worldInfo,  // Use real world's WorldInfo for identical terrain generation
            worldProvider,
            new Profiler(),
            true  // isRemote (client-side)
        );

        // IMPORTANT: Use WorldProvider.getSeed() instead of WorldInfo.getSeed()
        // Mods like Advanced Rocketry override getSeed() to return dimension-specific seeds
        // (e.g., WorldProviderPlanet.getSeed() returns baseSeed + dimensionId)
        // WorldInfo only contains the base world seed, which would cause incorrect
        // terrain generation in the validation world for these dimensions.
        this.worldSeed = worldProvider.getSeed();
        this.chunkGenerator = chunkGenerator;

        // Set up the world with proper initialization
        initializeWorld(biomeProvider, worldProvider);
    }

    /**
     * Initializes the world with the provided providers.
     */
    private void initializeWorld(BiomeProvider biomeProvider, WorldProvider worldProvider) {
        // CRITICAL: Do NOT modify the shared WorldProvider or BiomeProvider!
        // These are shared with the real world, and modifying them would cause
        // the real world to reference the validation world, leading to crashes.

        // Override the BiomeProvider with the real world's BiomeProvider
        // The WorldProvider creates its own BiomeProvider, which causes biome mismatches.
        // We use reflection to set the biomeProvider field to ensure identical biomes.
        if (biomeProviderField != null) {
            try {
                biomeProviderField.set(this, biomeProvider);
            } catch (IllegalAccessException e) {
                SimpleStructureScanner.LOGGER.warn("Failed to set biomeProvider field via reflection: {}", e.getMessage());
                SimpleStructureScanner.LOGGER.warn("BiomeProvider synchronization will not be available - validation may have biome mismatches.");
            }
        } else {
            SimpleStructureScanner.LOGGER.debug("BiomeProvider field not available - using WorldProvider's default BiomeProvider");
        }

        // Create the chunk provider with terrain generation
        this.chunkProvider = new ValidationChunkProvider(this, chunkGenerator);

        // Set world border to a large size
        this.getWorldBorder().setSize(30000000);
    }

    @Override
    protected void initCapabilities() {
        // Do not trigger forge events for validation world
    }

    @Nonnull
    @Override
    protected IChunkProvider createChunkProvider() {
        return new ValidationChunkProvider(this, chunkGenerator);
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        // Guard against null chunkProvider during initialization or edge cases
        if (chunkProvider == null) return false;

        return chunkProvider.isChunkGeneratedAt(x, z);
    }

    //================================================================================
    // World Methods Pillar Uses for Structure Placement
    //================================================================================

    /**
     * Gets the highest solid or liquid block at the given XZ position.
     * Used by Pillar's SURFACE generator type.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The position of the highest solid or liquid block
     */
    @Nonnull
    @Override
    public BlockPos getTopSolidOrLiquidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        BlockPos pos;

        // Start from the top and work down
        for (pos = new BlockPos(xzPos.getX(), chunk.getTopFilledSegment() + 16, xzPos.getZ());
             pos.getY() >= 0;
             pos = pos.down()) {

            IBlockState state = chunk.getBlockState(pos);
            if (!state.getBlock().isAir(state, this, pos)) {
                return pos;
            }
        }

        return xzPos;
    }

    /**
     * Gets the highest solid block at the given XZ position.
     * Used by Pillar's UNDERWATER generator type.
     * This is a custom Pillar method that finds the ocean floor.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The position of the highest solid block (excluding liquids)
     */
    @Nonnull
    public BlockPos getTopSolidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        BlockPos pos;
        BlockPos nextPos;

        for (pos = new BlockPos(xzPos.getX(), chunk.getTopFilledSegment() + 16, xzPos.getZ());
             pos.getY() >= 0;
             pos = nextPos) {

            nextPos = pos.down();
            IBlockState state = chunk.getBlockState(nextPos);

            if (state.isOpaqueCube() &&
                !state.getBlock().isLeaves(state, this, nextPos) &&
                !state.getBlock().isFoliage(this, nextPos)) {
                return pos;
            }
        }

        return pos;
    }

    /**
     * Gets the highest liquid block at the given XZ position.
     * Used by Pillar's ABOVE_WATER generator type.
     * This is a custom Pillar method that finds the water surface.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The position of the highest liquid block
     */
    @Nonnull
    public BlockPos getTopLiquidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        BlockPos pos;
        BlockPos nextPos;

        for (pos = new BlockPos(xzPos.getX(), chunk.getTopFilledSegment() + 16, xzPos.getZ());
             pos.getY() >= 0;
             pos = nextPos) {

            nextPos = pos.down();
            IBlockState state = chunk.getBlockState(nextPos);

            if (state.getBlock() instanceof BlockLiquid) return pos;
        }

        return pos;
    }

    /**
     * Checks if a block can see the sky.
     * Used by Pillar's UNDERGROUND and SKY generator types.
     * Uses the base World implementation which works correctly with generated chunks.
     */
    @Override
    public boolean canBlockSeeSky(@Nonnull BlockPos pos) {
        // Delegate to base World implementation
        return super.canBlockSeeSky(pos);
    }

    //================================================================================
    // No-op Overrides to Prevent Side Effects
    //================================================================================

    @Override
    public boolean checkLightFor(@Nonnull EnumSkyBlock lightType, @Nonnull BlockPos pos) {
        // No-op - validation world doesn't need light updates
        return true;
    }

    @Override
    public int getLightFromNeighborsFor(@Nonnull EnumSkyBlock type, @Nonnull BlockPos pos) {
        // Return full brightness for simplicity
        return 15;
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        // Return full brightness
        return 15 << 20 | 15 << 4;
    }

    @Override
    public void notifyBlockUpdate(@Nonnull BlockPos pos, @Nonnull IBlockState oldState, @Nonnull IBlockState newState, int flags) {
        // No-op - don't notify blocks in validation world
    }

    @Override
    public void markBlockRangeForRenderUpdate(@Nonnull BlockPos rangeMin, @Nonnull BlockPos rangeMax) {
        // No-op - validation world isn't rendered
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        // No-op - validation world isn't rendered
    }

    //================================================================================
    // Utility Methods
    //================================================================================

    /**
     * Gets the world seed being used for generation.
     */
    public long getWorldSeed() {
        return worldSeed;
    }

    /**
     * Gets the chunk generator being used.
     */
    public IChunkGenerator getChunkGenerator() {
        return chunkGenerator;
    }

    /**
     * Clears the chunk cache to free memory.
     * Call this when done validating structures.
     */
    public void clearChunkCache() {
        if (chunkProvider instanceof ValidationChunkProvider) {
            ((ValidationChunkProvider) chunkProvider).clearCache();
        }
    }
}
