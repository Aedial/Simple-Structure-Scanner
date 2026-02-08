package com.simplestructurescanner.structure.util;

import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.world.World;


/**
 * Utility class for common seed-related operations used by structure providers.
 * Contains methods for getting world seeds and initializing Random instances
 * using Minecraft's standard seeding patterns.
 */
public final class SeedHelper {

    private SeedHelper() {
    }

    /**
     * Get the world seed from a World instance.
     *
     * @param world the world to get the seed from
     * @return the world seed, or null if it cannot be retrieved
     */
    @Nullable
    public static Long getWorldSeed(World world) {
        if (world == null) return null;
        if (world.getWorldInfo() != null) return world.getWorldInfo().getSeed();

        return null;
    }

    /**
     * Initialize a Random instance using Minecraft's standard chunk-based seeding formula.
     * <p>
     * This is the common seeding pattern used by many Minecraft structure generators:
     * <pre>
     * random.setSeed(seed);
     * long i = random.nextLong();
     * long j = random.nextLong();
     * random.setSeed((chunkX * i) ^ (chunkZ * j) ^ seed);
     * </pre>
     *
     * @param seed the world seed
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @return a seeded Random instance
     */
    public static Random seedChunkRandom(long seed, int chunkX, int chunkZ) {
        Random random = new Random(seed);
        long i = random.nextLong();
        long j = random.nextLong();
        random.setSeed(((long) chunkX * i) ^ ((long) chunkZ * j) ^ seed);

        return random;
    }

    /**
     * Initialize a Random instance using Minecraft's scattered feature (region-based) seeding formula.
     * <p>
     * This is the seeding pattern used by villages, temples, and other scattered features:
     * <pre>
     * random.setSeed(regionX * 341873128712L + regionZ * 132897987541L + seed + salt);
     * </pre>
     *
     * @param seed the world seed
     * @param regionX the region X coordinate
     * @param regionZ the region Z coordinate
     * @param salt the structure-specific salt value
     * @return a seeded Random instance
     */
    public static Random seedRegionRandom(long seed, int regionX, int regionZ, long salt) {
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + salt);

        return random;
    }

    /**
     * Initialize a Random instance using Minecraft's StructureStart.create() seeding formula.
     * <p>
     * This is used by structure components to determine internal randomness like Y offsets:
     * <pre>
     * random.setSeed(seed);
     * long i = random.nextLong();
     * long j = random.nextLong();
     * random.setSeed(chunkX * i ^ chunkZ * j ^ seed);
     * </pre>
     *
     * @param seed the world seed
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @return a seeded Random instance
     */
    public static Random seedStructureStartRandom(long seed, int chunkX, int chunkZ) {
        Random random = new Random();
        random.setSeed(seed);
        long i = random.nextLong();
        long j = random.nextLong();
        random.setSeed((long) chunkX * i ^ (long) chunkZ * j ^ seed);

        return random;
    }

    /**
     * Create a decorrelated seed from a base seed and coordinates.
     * <p>
     * Useful for generating independent random sequences for different purposes
     * at the same coordinates.
     *
     * @param seed the base seed
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param salt a salt value to decorrelate from other uses
     * @return a decorrelated seed value
     */
    public static long decorrelate(long seed, int x, int z, long salt) {
        return (seed ^ (x * 341873128712L) ^ (z * 132897987541L)) + salt;
    }
}
