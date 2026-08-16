package com.simplestructurescanner.rcv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import net.minecraft.util.math.ChunkPos;

/**
 * Thread-safe cache of Recurrent Complex random seeds keyed by world seed and chunk position.
 */
public final class RCVRandomCache {

    private static final int MAX_ENTRIES = 20000;

    private static final ConcurrentHashMap<Long, Long> seedCache = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<Long> insertionOrder = new ConcurrentLinkedDeque<>();

    private RCVRandomCache() {}

    private static long key(long worldSeed, int chunkX, int chunkZ) {
        return worldSeed * 0x9E3779B97F4A7C15L ^ ChunkPos.asLong(chunkX, chunkZ);
    }

    public static void store(long worldSeed, int chunkX, int chunkZ, long randomInternalSeed) {
        long k = key(worldSeed, chunkX, chunkZ);
        Long existing = seedCache.get(k);
        if (existing != null) return;

        seedCache.put(k, randomInternalSeed);
        insertionOrder.addLast(k);
        while (insertionOrder.size() > MAX_ENTRIES) {
            Long oldest = insertionOrder.pollFirst();
            if (oldest != null) seedCache.remove(oldest);
        }
    }

    public static long get(long worldSeed, int chunkX, int chunkZ) {
        return seedCache.getOrDefault(key(worldSeed, chunkX, chunkZ), Long.MIN_VALUE);
    }

    public static boolean has(long worldSeed, int chunkX, int chunkZ) {
        return seedCache.containsKey(key(worldSeed, chunkX, chunkZ));
    }

    public static int size() {
        return seedCache.size();
    }

    public static void clear() {
        seedCache.clear();
        insertionOrder.clear();
    }
}
