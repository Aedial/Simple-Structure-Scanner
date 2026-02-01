package com.simplestructurescanner.structure.pillar;

/**
 * Proxy for Pillar's GeneratorType enum.
 * <p>
 * This mirrors the GeneratorType values from Pillar:
 * - SURFACE: Spawns on terrain surface
 * - UNDERGROUND: Spawns underground (0-60)
 * - UNDERWATER: Spawns underwater on ocean floor
 * - ABOVE_WATER: Spawns above water surface
 * - SKY: Spawns in the sky (128-256)
 * - ANYWHERE: Spawns at any Y level
 * - NONE: Does not spawn
 */
public enum PillarGeneratorType {
    SURFACE,
    UNDERGROUND,
    UNDERWATER,
    ABOVE_WATER,
    SKY,
    ANYWHERE,
    NONE
}
