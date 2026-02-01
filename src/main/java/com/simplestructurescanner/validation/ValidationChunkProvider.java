package com.simplestructurescanner.validation;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A chunk provider for the structure validation world that generates real terrain.
 *
 * This provider:
 * - Generates chunks with actual terrain data using the real chunk generator
 * - Caches generated chunks in memory to avoid regenerating
 * - Provides chunks to the validation world for structure placement validation
 *
 * The key difference from DummyChunkProvider is that this actually generates
 * terrain by delegating to the real world's chunk generator.
 */
public class ValidationChunkProvider implements IChunkProvider {

    private final World world;
    private final IChunkGenerator chunkGenerator;
    private final Long2ObjectMap<Chunk> loadedChunks;

    /**
     * Creates a new validation chunk provider.
     *
     * @param world The validation world
     * @param chunkGenerator The real chunk generator to use for terrain generation
     */
    public ValidationChunkProvider(World world, IChunkGenerator chunkGenerator) {
        this.world = world;
        this.chunkGenerator = chunkGenerator;
        this.loadedChunks = new Long2ObjectOpenHashMap<>();
    }

    @Nullable
    @Override
    public Chunk getLoadedChunk(int x, int z) {
        long chunkKey = ChunkPos.asLong(x, z);
        return loadedChunks.get(chunkKey);
    }

    @Nonnull
    @Override
    public Chunk provideChunk(int x, int z) {
        long chunkKey = ChunkPos.asLong(x, z);

        // Return cached chunk if available
        if (loadedChunks.containsKey(chunkKey)) {
            return loadedChunks.get(chunkKey);
        }

        // Generate new chunk with terrain data
        Chunk chunk = chunkGenerator.generateChunk(x, z);

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
        return loadedChunks.containsKey(ChunkPos.asLong(x, z));
    }

    /**
     * Gets the chunk generator being used for terrain generation.
     * This can be useful for debugging or direct access.
     */
    public IChunkGenerator getChunkGenerator() {
        return chunkGenerator;
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
