package com.simplestructurescanner.structure.validation;

import javax.annotation.Nonnull;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.recurrentcomplex.RecurrentComplexStructureSearcher;
import com.simplestructurescanner.structure.util.ReflectionHelper;


/**
 * In-memory world used to test terrain-dependent structure placement.
 * <p>
 * Extends {@link World} so construction does not corrupts the running state
 * with objects such as PlayerChunkMap, Teleporter, DimensionManager registration,
 * or Forge lifecycle events.
 * <p>
 * Use {@link ValidationChunkProvider} to generate and decorate chunks without
 * saving them.
 */
public class StructureValidationWorld extends World {

    private final IChunkGenerator chunkGenerator;
    private final ValidationChunkProvider validationChunkProvider;

    /**
     * Creates a validation world from a source world's generator and settings.
     *
     * @param saveHandler A save handler that discards writes
     * @param worldInfo Cloned settings from the source world
     * @param realProvider The source world's provider, restored by the caller after validation
     * @param chunkGenerator The source world's chunk generator
     */
    public StructureValidationWorld(ISaveHandler saveHandler, WorldInfo worldInfo, WorldProvider realProvider,
            IChunkGenerator chunkGenerator) {

        super(saveHandler, worldInfo, realProvider, new Profiler(), false);

        this.chunkGenerator = chunkGenerator;
        validationChunkProvider = new ValidationChunkProvider(this, chunkGenerator);

        try {
            ReflectionHelper.getAccessibleDeclaredField(World.class, "field_73020_y", "chunkProvider")
                .set(this, validationChunkProvider);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Could not install the validation chunk provider", e);
        }

        SimpleStructureScanner.LOGGER.debug("Created validation world for dimension {}",
            realProvider.getDimension());
    }

    @Override
    protected void initCapabilities() {
    }

    @Override
    protected IChunkProvider createChunkProvider() {
        return validationChunkProvider;
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return validationChunkProvider != null && validationChunkProvider.isChunkGeneratedAt(x, z);
    }

    @Override
    public Biome getBiomeForCoordsBody(BlockPos pos) {
        Biome biome = RecurrentComplexStructureSearcher.getCachedChunkCenterBiome(this, pos);
        if (biome != null) return biome;

        return super.getBiomeForCoordsBody(pos);
    }

    // Terrain height queries

    BlockPos getTopBlock(@Nonnull BlockPos xzPos, @Nonnull Chunk chunk) {
        return new BlockPos(xzPos.getX(), chunk.getTopFilledSegment() + 16, xzPos.getZ());
    }

    /**
     * Gets the highest solid or liquid block at the given XZ position.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The highest non-air block position
     */
    @Nonnull
    @Override
    public BlockPos getTopSolidOrLiquidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);

        // Search downward from the highest generated section
        for (BlockPos pos = getTopBlock(xzPos, chunk); pos.getY() >= 0; pos = pos.down()) {
            IBlockState state = chunk.getBlockState(pos);
            if (!state.getBlock().isAir(state, this, pos)) return pos;
        }

        return xzPos;
    }

    /**
     * Finds the position above the highest opaque block.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The position above the highest solid block
     */
    @Nonnull
    public BlockPos getTopSolidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        BlockPos nextPos;

        BlockPos pos = getTopBlock(xzPos, chunk);
        for (; pos.getY() >= 0; pos = nextPos) {
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
     * Finds the position above the highest liquid block.
     *
     * @param xzPos The XZ position (Y coordinate is ignored)
     * @return The position above the highest liquid block
     */
    @Nonnull
    public BlockPos getTopLiquidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        BlockPos nextPos;

        BlockPos pos = getTopBlock(xzPos, chunk);
        for (; pos.getY() >= 0; pos = nextPos) {
            nextPos = pos.down();
            IBlockState state = chunk.getBlockState(nextPos);

            if (state.getBlock() instanceof BlockLiquid) return pos;
        }

        return pos;
    }

    // Overrides that suppress light, block, and render updates

    @Override
    public boolean checkLightFor(@Nonnull EnumSkyBlock lightType, @Nonnull BlockPos pos) {
        // Validation does not update light
        return true;
    }

    @Override
    public int getLightFromNeighborsFor(@Nonnull EnumSkyBlock type, @Nonnull BlockPos pos) {
        // Always return full block light
        return 15;
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        // Always return full block and sky light
        return 15 << 20 | 15 << 4;
    }

    @Override
    public void notifyBlockUpdate(@Nonnull BlockPos pos, @Nonnull IBlockState oldState, @Nonnull IBlockState newState, int flags) {
        // Validation does not notify blocks
    }

    @Override
    public void markBlockRangeForRenderUpdate(@Nonnull BlockPos rangeMin, @Nonnull BlockPos rangeMax) {
        // Validation worlds are never rendered
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        // Validation worlds are never rendered
    }

    // Chunk generation helpers

    /**
     * Clears generated and decorated chunks after validation.
     */
    public void clearChunkCache() {
        if (validationChunkProvider != null) validationChunkProvider.clearCache();
    }

    public ValidationChunkProvider getValidationChunkProvider() {
        return validationChunkProvider;
    }

    /**
     * Decorates every chunk within the provided range.
     *
     * @return the number of chunks successfully populated
     */
    public int populateChunkRange(int minX, int minZ, int maxX, int maxZ) {
        if (validationChunkProvider == null) return 0;

        int minCX = minX >> 4;
        int maxCX = maxX >> 4;
        int minCZ = minZ >> 4;
        int maxCZ = maxZ >> 4;
        int count = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (validationChunkProvider.populateChunk(cx, cz)) count++;
            }
        }

        return count;
    }

    /**
     * Generates every chunk within the provided range without decoration.
     */
    public void provideChunkRange(int minX, int minZ, int maxX, int maxZ) {
        if (validationChunkProvider == null) return;

        int minCX = minX >> 4;
        int maxCX = maxX >> 4;
        int minCZ = minZ >> 4;
        int maxCZ = maxZ >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) validationChunkProvider.provideChunk(cx, cz);
        }
    }
}
