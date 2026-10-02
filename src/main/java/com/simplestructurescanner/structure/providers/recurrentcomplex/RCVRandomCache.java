package com.simplestructurescanner.structure.providers.recurrentcomplex;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Thread-safe cache of Recurrent Complex random seeds classified by capture source.
 * <p>
 * Sized to hold several full search radii (a 64-chunk radius spans 16,641 chunks)
 * so repeat scans of the same area hit the cache. Insertion-ordered with FIFO
 * eviction beyond {@link #MAX_ENTRIES}. Synchronized because writes happen from
 * both the scan thread (simulated captures) and the server thread (real
 * generation captures via the mixin).
 */
public final class RCVRandomCache {

    private static final int MAX_ENTRIES = 100_000;

    private static final Map<CacheKey, CacheEntry> SEEDS = new LinkedHashMap<>();

    private RCVRandomCache() {}

    public static synchronized void recordSimulated(long worldSeed, int dimensionId,
            int chunkX, int chunkZ, long randomInternalSeed) {
        CacheKey key = new CacheKey(worldSeed, dimensionId, chunkX, chunkZ);
        if (SEEDS.containsKey(key)) return;

        add(key, randomInternalSeed, CaptureSource.SIMULATED);
    }

    public static synchronized void recordObserved(long worldSeed, int dimensionId,
            int chunkX, int chunkZ, long randomInternalSeed) {
        CacheKey key = new CacheKey(worldSeed, dimensionId, chunkX, chunkZ);
        CacheEntry entry = SEEDS.get(key);
        if (entry != null && entry.source == CaptureSource.OBSERVED &&
                entry.randomInternalSeed == randomInternalSeed) return;

        if (entry != null) {
            SEEDS.put(key, new CacheEntry(randomInternalSeed, CaptureSource.OBSERVED));
            return;
        }

        add(key, randomInternalSeed, CaptureSource.OBSERVED);
    }

    public static synchronized long get(long worldSeed, int dimensionId, int chunkX, int chunkZ) {
        CacheEntry entry = SEEDS.get(new CacheKey(worldSeed, dimensionId, chunkX, chunkZ));
        return entry != null ? entry.randomInternalSeed : Long.MIN_VALUE;
    }

    public static synchronized boolean has(long worldSeed, int dimensionId, int chunkX, int chunkZ) {
        return SEEDS.containsKey(new CacheKey(worldSeed, dimensionId, chunkX, chunkZ));
    }

    private static void add(CacheKey key, long randomInternalSeed, CaptureSource source) {
        if (SEEDS.size() >= MAX_ENTRIES) {
            Iterator<CacheKey> keys = SEEDS.keySet().iterator();
            keys.next();
            keys.remove();
        }

        SEEDS.put(key, new CacheEntry(randomInternalSeed, source));
    }

    private enum CaptureSource {
        SIMULATED, OBSERVED
    }

    private static final class CacheEntry {

        private final long randomInternalSeed;
        private final CaptureSource source;

        private CacheEntry(long randomInternalSeed, CaptureSource source) {
            this.randomInternalSeed = randomInternalSeed;
            this.source = source;
        }
    }

    private static final class CacheKey {

        private final long worldSeed;
        private final int dimensionId;
        private final int chunkX;
        private final int chunkZ;

        private CacheKey(long worldSeed, int dimensionId, int chunkX, int chunkZ) {
            this.worldSeed = worldSeed;
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CacheKey)) return false;

            CacheKey other = (CacheKey) object;
            return worldSeed == other.worldSeed && dimensionId == other.dimensionId &&
                chunkX == other.chunkX && chunkZ == other.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = (int) (worldSeed ^ worldSeed >>> 32);
            result = 31 * result + dimensionId;
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
    }

    public static synchronized void clear() {
        SEEDS.clear();
    }
}
