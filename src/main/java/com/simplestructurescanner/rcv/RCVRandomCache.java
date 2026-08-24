package com.simplestructurescanner.rcv;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;

import net.minecraft.util.math.ChunkPos;


/**
 * Thread-safe cache of Recurrent Complex random seeds keyed by world seed and chunk position.
 * <p>
 * Sized to hold several full search radii (a 64-chunk radius spans 16,641 chunks)
 * so repeat scans of the same area hit the cache. Insertion-ordered with FIFO
 * eviction beyond {@link #MAX_ENTRIES}. Synchronized because writes happen from
 * both the scan thread (simulated captures) and the server thread (real
 * generation captures via the mixin).
 */
public final class RCVRandomCache {

    private static final int MAX_ENTRIES = 100_000;

    private static final Long2LongLinkedOpenHashMap SEEDS = new Long2LongLinkedOpenHashMap(MAX_ENTRIES);

    static {
        SEEDS.defaultReturnValue(Long.MIN_VALUE);
    }

    private RCVRandomCache() {}

    private static long key(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed * 0x9E3779B97F4A7C15L ^ ChunkPos.asLong(chunkX, chunkZ);
    }

    public static synchronized void store(long worldSeed, int chunkX, int chunkZ, long randomInternalSeed) {
        long k = key(worldSeed, chunkX, chunkZ);
        if (SEEDS.containsKey(k)) return;

        if (SEEDS.size() >= MAX_ENTRIES) SEEDS.remove(SEEDS.firstLongKey());

        SEEDS.put(k, randomInternalSeed);
    }

    public static synchronized long get(long worldSeed, int chunkX, int chunkZ) {
        return SEEDS.get(key(worldSeed, chunkX, chunkZ));
    }

    public static synchronized boolean has(long worldSeed, int chunkX, int chunkZ) {
        return SEEDS.containsKey(key(worldSeed, chunkX, chunkZ));
    }

    public static synchronized int size() {
        return SEEDS.size();
    }

    public static synchronized void clear() {
        SEEDS.clear();
    }
}
