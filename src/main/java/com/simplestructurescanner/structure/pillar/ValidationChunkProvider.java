package com.simplestructurescanner.structure.pillar;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A chunk provider for the structure validation world that generates real terrain.
 * <p>
 * This provider:
 * - Generates chunks with actual terrain data using the real chunk generator
 * - Caches generated chunks in memory to avoid regenerating
 * - Provides chunks to the validation world for structure placement validation
 * <p>
 * The key difference from DummyChunkProvider is that this actually generates
 * terrain by delegating to the real world's chunk generator.
 */
public class ValidationChunkProvider implements IChunkProvider {

    private final IChunkGenerator chunkGenerator;
    private final Long2ObjectMap<Chunk> loadedChunks;

    /**
     * Creates a new validation chunk provider.
     *
     * @param chunkGenerator The real chunk generator to use for terrain generation
     */
    public ValidationChunkProvider(IChunkGenerator chunkGenerator) {
        this.chunkGenerator = chunkGenerator;
        this.loadedChunks = new Long2ObjectOpenHashMap<>();
    }

    private static long getChunkKey(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    @Nullable
    @Override
    public Chunk getLoadedChunk(int x, int z) {
        long chunkKey = getChunkKey(x, z);
        return loadedChunks.get(chunkKey);
    }

    @Nonnull
    @Override
    public Chunk provideChunk(int x, int z) {
        long chunkKey = getChunkKey(x, z);
        Chunk chunk = loadedChunks.get(chunkKey);

        // Return cached chunk if available
        if (chunk != null) return chunk;

        // Generate new chunk with terrain data
        chunk = chunkGenerator.generateChunk(x, z);

        // Cache the chunk
        loadedChunks.put(chunkKey, chunk);

        return chunk;
    }

    @Override
    public boolean tick() {
        // No-op - validation world doesn't tick
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

    /**
     * Clears all cached chunks to free memory.
     * Call this when done validating structures to prevent memory leaks.
     */
    public void clearCache() {
        loadedChunks.clear();
    }

    /**
     * Gets the number of cached chunks.
     * Useful for monitoring memory usage.
     */
    public int getCachedChunkCount() {
        return loadedChunks.size();
    }
}
