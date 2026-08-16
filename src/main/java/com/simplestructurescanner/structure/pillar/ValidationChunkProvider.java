package com.simplestructurescanner.structure.pillar;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.block.BlockFalling;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVPredictionContext;

/**
 * A chunk provider for the structure validation world that generates real terrain.
 * <p>
 * Implements {@link IChunkProvider} (not extends {@code ChunkProviderServer})
 * to avoid the ChunkProviderServer constructor which requires a WorldServer.
 * <p>
 * Uses a <b>separate chunk generator instance</b> ({@link #validationGenerator})
 * whose {@code world} field is permanently set to the validation world via its
 * constructor. This avoids the need to swap the real generator's {@code world}
 * field (which caused JIT inlining issues and post-scan crashes on JDK 25).
 * <p>
 * Populate (decoration) runs on the validation generator. The
 * {@link RCVPredictionContext} flag activates {@link MixinMapGenStructureHook}
 * which cancels RC's {@code MapGenStructureHook.generate()} during prediction,
 * preventing ClassCastException from its WorldServer cast.
 */
public class ValidationChunkProvider implements IChunkProvider {

    private final World world;
    private final IChunkGenerator realGenerator;
    private final Long2ObjectMap<Chunk> loadedChunks;
    private final Set<Long> populatedChunks;

    @Nullable
    private IChunkGenerator validationGenerator;

    /**
     * Creates a new validation chunk provider.
     *
     * @param world          The validation World
     * @param chunkGenerator The real chunk generator (used as a template to create the validation generator)
     */
    public ValidationChunkProvider(World world, IChunkGenerator chunkGenerator) {
        this.world = world;
        this.realGenerator = chunkGenerator;
        this.loadedChunks = new Long2ObjectOpenHashMap<>();
        this.populatedChunks = new HashSet<>();
    }

    private static long getChunkKey(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    /**
     * Creates a separate chunk generator instance for the validation world.
     * <p>
     * The new generator's {@code world} field is permanently set to the
     * validation world via its constructor. This avoids modifying the real
     * generator's (potentially {@code final}) {@code world} field.
     * <p>
     * The constructor signature must be {@code (World, long, boolean, String)}.
     *
     * @return the validation generator, or null if creation failed
     */
    @Nullable
    private IChunkGenerator getOrCreateValidationGenerator() {
        if (validationGenerator != null) return validationGenerator;

        long seed = world.getWorldInfo().getSeed();
        boolean mapFeatures = world.getWorldInfo().isMapFeaturesEnabled();
        String genOptions = world.getWorldInfo().getGeneratorOptions();

        try {
            Constructor<?> ctor = realGenerator.getClass().getConstructor(
                World.class, long.class, boolean.class, String.class);
            ctor.setAccessible(true);
            validationGenerator = (IChunkGenerator) ctor.newInstance(
                world, seed, mapFeatures, genOptions);
            SimpleStructureScanner.LOGGER.info(
                "Created validation generator: {} (seed={}, mapFeatures={})",
                validationGenerator.getClass().getSimpleName(), seed, mapFeatures);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                "Failed to create validation generator from {}: {} — populate unavailable",
                realGenerator.getClass().getName(), e.getMessage());
        }

        return validationGenerator;
    }

    @Nullable
    @Override
    public Chunk getLoadedChunk(int x, int z) {
        return loadedChunks.get(getChunkKey(x, z));
    }

    @Nonnull
    @Override
    public Chunk provideChunk(int x, int z) {
        long chunkKey = getChunkKey(x, z);
        Chunk chunk = loadedChunks.get(chunkKey);

        if (chunk != null) return chunk;

        IChunkGenerator vGen = getOrCreateValidationGenerator();
        if (vGen != null) {
            boolean wasPredicting = RCVPredictionContext.isPredicting();
            RCVPredictionContext.setPredicting(true);
            try {
                chunk = vGen.generateChunk(x, z);
            } finally {
                RCVPredictionContext.setPredicting(wasPredicting);
            }
        } else {
            chunk = realGenerator.generateChunk(x, z);
        }
        loadedChunks.put(chunkKey, chunk);

        return chunk;
    }

    @Override
    public boolean tick() {
        return !loadedChunks.isEmpty();
    }

    @Nonnull
    @Override
    public String makeString() {
        return "ValidationChunkProvider";
    }

    @Override
    public boolean isChunkGeneratedAt(int x, int z) {
        return loadedChunks.containsKey(getChunkKey(x, z));
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    public boolean canSave() {
        return false;
    }

    public void flushToDisk() {
    }

    public boolean saveChunks(boolean all) {
        return true;
    }

    /**
     * Gets the number of cached chunks.
     */
    public int getCachedChunkCount() {
        return loadedChunks.size();
    }

    /**
     * Populates a chunk using the validation generator.
     * <p>
     * The validation generator's {@code world} field is permanently set to the
     * validation world, so no field swap is needed. The
     * {@link RCVPredictionContext} flag activates {@link MixinMapGenStructureHook}
     * to cancel RC's structure hook during prediction.
     *
     * @return true if populate succeeded, false if it failed or was already done
     */
    public boolean populateChunk(int x, int z) {
        long key = getChunkKey(x, z);
        if (populatedChunks.contains(key)) return true;

        IChunkGenerator vGen = getOrCreateValidationGenerator();
        if (vGen == null) return false;

        provideChunk(x, z);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                provideChunk(x + dx, z + dz);
            }
        }

        RCVPredictionContext.setPredicting(true);
        try {
            SimpleStructureScanner.LOGGER.debug(
                "Populating chunk ({},{}) on validation world (generator: {})",
                x, z, vGen.getClass().getSimpleName());

            Chunk chunk = loadedChunks.get(key);
            int beforeTopY = chunk != null ? chunk.getTopFilledSegment() + 16 : -1;

            vGen.populate(x, z);
            populatedChunks.add(key);

            int afterTopY = chunk != null ? chunk.getTopFilledSegment() + 16 : -1;
            SimpleStructureScanner.LOGGER.debug(
                "Populate succeeded for chunk ({},{}) — topY before={}, after={}",
                x, z, beforeTopY, afterTopY);
            return true;
        } catch (Throwable t) {
            StackTraceElement[] stack = t.getStackTrace();
            String topFrame = stack.length > 0 ? stack[0].toString() : "?";
            String callerFrame = stack.length > 2 ? stack[2].toString() : "?";
            SimpleStructureScanner.LOGGER.warn(
                "Populate failed for chunk ({},{}) on validation world: {}: {} | at: {} | caller: {}",
                x, z, t.getClass().getSimpleName(), t.getMessage(), topFrame, callerFrame);
            return false;
        } finally {
            BlockFalling.fallInstantly = false;
            RCVPredictionContext.setPredicting(false);
        }
    }

    /**
     * Clears the populated-chunks tracking along with the chunk cache.
     */
    public void clearCache() {
        loadedChunks.clear();
        populatedChunks.clear();
    }
}
