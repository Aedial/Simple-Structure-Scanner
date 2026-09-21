package com.simplestructurescanner.structure.validation;

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
 * Generates and caches chunks for {@link StructureValidationWorld}.
 * <p>
 * Implements {@link IChunkProvider} because {@code ChunkProviderServer}
 * requires a {@code WorldServer}.
 * <p>
 * Attempts to create a second chunk generator from the source generator's
 * {@code (World, long, boolean, String)} constructor. The constructor binds it
 * to the validation world.
 * <p>
 * During prediction, {@link RCVPredictionContext} causes the Recurrent Complex
 * hook to skip its {@code WorldServer}-only structure generation path.
 */
public class ValidationChunkProvider implements IChunkProvider {

    private final World world;
    private final IChunkGenerator realGenerator;
    private final Long2ObjectMap<Chunk> loadedChunks;
    private final Set<Long> populatedChunks;

    @Nullable
    private IChunkGenerator validationGenerator;

    /**
     * Creates a chunk provider for a validation world.
     *
     * @param world The validation world
     * @param chunkGenerator The source chunk generator used to create the validation generator
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
     * Creates a chunk generator bound to the validation world.
     * <p>
     * The source generator must expose a {@code (World, long, boolean, String)}
     * constructor.
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
                "Created validation chunk generator {} for seed {} (map features: {})",
                validationGenerator.getClass().getSimpleName(), seed, mapFeatures);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn(
                "Could not create validation chunk generator from {}; decoration is unavailable: {}",
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
        Chunk cachedChunk = loadedChunks.get(chunkKey);

        if (cachedChunk != null) return cachedChunk;

        IChunkGenerator vGen = getOrCreateValidationGenerator();
        if (vGen != null) {
            boolean wasPredicting = RCVPredictionContext.isPredicting();
            RCVPredictionContext.setPredicting(true);
            try {
                cachedChunk = vGen.generateChunk(x, z);
            } finally {
                RCVPredictionContext.setPredicting(wasPredicting);
            }
        } else {
            cachedChunk = realGenerator.generateChunk(x, z);
        }

        loadedChunks.put(chunkKey, cachedChunk);

        return cachedChunk;
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
     * Returns the number of generated chunks in the cache.
     */
    public int getCachedChunkCount() {
        return loadedChunks.size();
    }

    /**
     * Decorates a generated chunk once with the validation generator.
     * <p>
     * {@link RCVPredictionContext} skips Recurrent Complex's structure hook,
     * which requires a {@code WorldServer}.
     *
     * FIXME: Move the RCV part to RC, this level of coupling is disgusting.
     *
     * @return true when decoration succeeds or the chunk was already decorated
     */
    public boolean populateChunk(int x, int z) {
        long key = getChunkKey(x, z);
        if (populatedChunks.contains(key)) return true;

        IChunkGenerator vGen = getOrCreateValidationGenerator();
        if (vGen == null) return false;

        provideChunk(x, z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) provideChunk(x + dx, z + dz);
        }

        RCVPredictionContext.setPredicting(true);
        try {
            SimpleStructureScanner.LOGGER.debug(
                "Decorating validation chunk ({},{}) with {}",
                x, z, vGen.getClass().getSimpleName());

            Chunk chunk = loadedChunks.get(key);
            int beforeTopY = chunk != null ? chunk.getTopFilledSegment() + 16 : -1;

            vGen.populate(x, z);
            populatedChunks.add(key);

            int afterTopY = chunk != null ? chunk.getTopFilledSegment() + 16 : -1;
            SimpleStructureScanner.LOGGER.debug(
                "Decorated validation chunk ({},{}): top Y {} -> {}",
                x, z, beforeTopY, afterTopY);
            return true;
        } catch (Throwable t) {
            StackTraceElement[] stack = t.getStackTrace();
            String topFrame = stack.length > 0 ? stack[0].toString() : "?";
            String callerFrame = stack.length > 2 ? stack[2].toString() : "?";
            SimpleStructureScanner.LOGGER.warn(
                "Could not decorate validation chunk ({},{}): {}: {} [at {}; caller {}]",
                x, z, t.getClass().getSimpleName(), t.getMessage(), topFrame, callerFrame);
            return false;
        } finally {
            BlockFalling.fallInstantly = false;
            RCVPredictionContext.setPredicting(false);
        }
    }

    /**
     * Clears generated and decorated chunk caches.
     */
    public void clearCache() {
        loadedChunks.clear();
        populatedChunks.clear();
    }
}
