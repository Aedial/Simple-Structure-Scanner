package com.simplestructurescanner.structure;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.NoiseGeneratorOctaves;

/**
 * Calculates terrain height using Minecraft's noise generation algorithms.
 * This allows estimating structure Y coordinates without loading chunks.
 * 
 * Based on MC 1.12's ChunkGeneratorOverworld terrain generation.
 */
public class TerrainHeightCalculator {

    private final NoiseGeneratorOctaves depthNoise;
    private final BiomeProvider biomeProvider;

    // MC terrain generation constants
    private static final int SEA_LEVEL = 63;

    public TerrainHeightCalculator(long seed, BiomeProvider biomeProvider) {
        this.biomeProvider = biomeProvider;

        // Initialize noise generator with world seed (same as ChunkGeneratorOverworld)
        Random random = new Random(seed);
        // Skip the noise generators we don't need (matching MC's initialization order)
        new NoiseGeneratorOctaves(random, 16); // minLimitPerlinNoise
        new NoiseGeneratorOctaves(random, 16); // maxLimitPerlinNoise
        new NoiseGeneratorOctaves(random, 8);  // mainPerlinNoise
        new NoiseGeneratorOctaves(random, 4);  // surfaceNoise
        new NoiseGeneratorOctaves(random, 10); // scaleNoise (unused in 1.12)
        this.depthNoise = new NoiseGeneratorOctaves(random, 16); // depthNoise
    }

    /**
     * Estimate the terrain height at a given X,Z position.
     * This uses biome base height + depth noise, similar to MC's generation.
     * 
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Estimated terrain height (Y coordinate)
     */
    public int getTerrainHeight(int blockX, int blockZ) {
        // Get biome at this position
        Biome biome = biomeProvider.getBiome(new BlockPos(blockX, 0, blockZ));

        // Get biome base height and variation
        float baseHeight = biome.getBaseHeight();
        float heightVariation = biome.getHeightVariation();

        // Sample depth noise at this position
        // MC uses this to add variation to the terrain
        double depthValue = sampleDepthNoise(blockX, blockZ);

        // Calculate terrain height using MC's formula (simplified)
        // Base formula: SEA_LEVEL + (baseHeight + depthNoise * heightVariation) * 17
        // The 17 is an approximation of MC's height scaling
        double height = SEA_LEVEL + (baseHeight + depthValue * heightVariation * 0.5) * 17.0;

        // Clamp to valid range
        return Math.max(1, Math.min(255, (int) Math.round(height)));
    }

    /**
     * Sample the depth noise at a position.
     * This matches MC's depthNoise sampling in generateHeightmap.
     */
    private double sampleDepthNoise(int blockX, int blockZ) {
        // MC samples noise at chunk-level coordinates, scaled
        double noiseX = blockX / 200.0;
        double noiseZ = blockZ / 200.0;

        // Generate noise value
        double[] noiseArray = new double[1];
        depthNoise.generateNoiseOctaves(noiseArray, (int) noiseX, (int) noiseZ, 1, 1, 1.0, 1.0, 0.5);

        // Normalize to roughly -1 to 1 range
        return noiseArray[0] / 8000.0;
    }

    /**
     * Get a simple biome-based height estimate without noise.
     * Faster but less accurate.
     */
    public int getSimpleTerrainHeight(int blockX, int blockZ) {
        Biome biome = biomeProvider.getBiome(new BlockPos(blockX, 0, blockZ));
        float baseHeight = biome.getBaseHeight();

        // Simple conversion: baseHeight of 0.1 = sea level, higher = hills, negative = ocean
        return SEA_LEVEL + (int) (baseHeight * 20);
    }

    /**
     * Get the sea level constant.
     */
    public static int getSeaLevel() {
        return SEA_LEVEL;
    }
}
