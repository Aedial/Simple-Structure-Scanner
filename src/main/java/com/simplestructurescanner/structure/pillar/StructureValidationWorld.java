package com.simplestructurescanner.structure.pillar;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

import javax.annotation.Nonnull;

import com.simplestructurescanner.SimpleStructureScanner;

/**
 * A fake world used for validating structure placement with actual terrain data.
 * <p>
 * Extends {@link World} (not {@code WorldServer}) to avoid the heavy
 * WorldServer constructor side effects (PlayerChunkMap, Teleporter,
 * DimensionManager registration, Forge lifecycle events) that corrupted
 * the running server's state.
 * <p>
 * Uses a {@link ValidationChunkProvider} for in-memory chunk generation.
 * Populate (decoration) is supported via {@link ValidationChunkProvider#populateChunk},
 * which sets {@link com.simplestructurescanner.rcv.RCVPredictionContext} to
 * activate {@link com.simplestructurescanner.mixin.rcv.MixinMapGenStructureHook},
 * which cancels RC's structure hook during prediction to prevent
 * ClassCastExceptions from WorldServer casts.
 */
public class StructureValidationWorld extends World {

    private final IChunkGenerator chunkGenerator;
    private final ValidationChunkProvider validationChunkProvider;

    /**
     * Creates a new structure validation world.
     *
     * @param saveHandler    A no-op save handler
     * @param worldInfo      The WorldInfo from the real world (cloned)
     * @param realProvider   The real world's WorldProvider (shared — saved/restored by caller)
     * @param chunkGenerator The chunk generator from the real world
     */
    public StructureValidationWorld(
        ISaveHandler saveHandler,
        WorldInfo worldInfo,
        WorldProvider realProvider,
        IChunkGenerator chunkGenerator
    ) {
        super(saveHandler, worldInfo, realProvider, new Profiler(), false);

        this.chunkGenerator = chunkGenerator;
        validationChunkProvider = new ValidationChunkProvider(this, chunkGenerator);

        try {
            java.lang.reflect.Field cpWorld = World.class.getDeclaredField("field_73020_y");
            cpWorld.setAccessible(true);
            cpWorld.set(this, validationChunkProvider);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field cpWorld = World.class.getDeclaredField("chunkProvider");
                cpWorld.setAccessible(true);
                cpWorld.set(this, validationChunkProvider);
            } catch (Exception e2) {
                SimpleStructureScanner.LOGGER.error("Failed to set World.chunkProvider", e2);
            }
        }

        SimpleStructureScanner.LOGGER.debug(
            "StructureValidationWorld created as World for dimension {}", realProvider.getDimension());
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

    //================================================================================
    // World Methods for Structure Placement
    //================================================================================

    BlockPos getTopBlock(@Nonnull BlockPos xzPos, @Nonnull Chunk chunk) {
        return new BlockPos(xzPos.getX(), chunk.getTopFilledSegment() + 16, xzPos.getZ());
    }

    @Nonnull
    @Override
    public BlockPos getTopSolidOrLiquidBlock(@Nonnull BlockPos xzPos) {
        Chunk chunk = getChunk(xzPos);
        for (BlockPos pos = getTopBlock(xzPos, chunk); pos.getY() >= 0; pos = pos.down()) {
            IBlockState state = chunk.getBlockState(pos);
            if (!state.getBlock().isAir(state, this, pos)) return pos;
        }
        return xzPos;
    }

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

    //================================================================================
    // No-op Overrides to Prevent Side Effects
    //================================================================================

    @Override
    public boolean checkLightFor(@Nonnull EnumSkyBlock lightType, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightFromNeighborsFor(@Nonnull EnumSkyBlock type, @Nonnull BlockPos pos) {
        return 15;
    }

    @Override
    public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
        return 15 << 20 | 15 << 4;
    }

    @Override
    public void notifyBlockUpdate(@Nonnull BlockPos pos, @Nonnull IBlockState oldState, @Nonnull IBlockState newState, int flags) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(@Nonnull BlockPos rangeMin, @Nonnull BlockPos rangeMax) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    //================================================================================
    // Utility Methods
    //================================================================================

    public void clearChunkCache() {
        if (validationChunkProvider != null) {
            validationChunkProvider.clearCache();
        }
    }

    /**
     * Returns the ValidationChunkProvider instance.
     */
    public ValidationChunkProvider getValidationChunkProvider() {
        return validationChunkProvider;
    }

    /**
     * Populates all chunks in the given block-coordinate range.
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
}
