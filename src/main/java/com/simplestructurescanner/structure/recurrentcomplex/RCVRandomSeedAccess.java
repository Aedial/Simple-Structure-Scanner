package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.simplestructurescanner.SimpleStructureScanner;


/**
 * Reads and restores Java {@link Random} internal seeds for Recurrent Complex
 * prediction.
 */
public final class RCVRandomSeedAccess {

    private static volatile boolean seedFieldReady = false;
    private static Field cachedSeedField = null;

    private RCVRandomSeedAccess() {}

    public static AtomicLong getSeedAtomic(Random random) {
        Field seedField = getSeedField();
        if (seedField == null) return null;

        try {
            return (AtomicLong) seedField.get(random);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to read Random.seed for Recurrent Complex", e);
            return null;
        }
    }

    public static Random withInternalSeed(long internalSeed) {
        Random random = new Random(0L);
        AtomicLong randomSeed = getSeedAtomic(random);
        if (randomSeed == null) return null;

        randomSeed.set(internalSeed);
        return random;
    }

    private static Field getSeedField() {
        if (seedFieldReady) return cachedSeedField;

        synchronized (RCVRandomSeedAccess.class) {
            try {
                cachedSeedField = Random.class.getDeclaredField("seed");
                cachedSeedField.setAccessible(true);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Failed to access Random.seed for Recurrent Complex", e);
            }

            seedFieldReady = true;
            return cachedSeedField;
        }
    }
}
