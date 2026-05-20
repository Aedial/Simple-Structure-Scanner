package com.simplestructurescanner.structure.util;

import com.simplestructurescanner.structure.LocalizedText;


public final class RarityTextHelper {

    private RarityTextHelper() {
    }

    public static LocalizedText oneInChunks(long chunks) {
        return LocalizedText.translatable(
            "gui.structurescanner.rarity",
            LocalizedText.translatable("gui.structurescanner.rarity.one_in_chunks", chunks)
        );
    }

    public static LocalizedText oneInChunks(double chunks) {
        return oneInChunks(Math.max(1L, Math.round(chunks)));
    }

    public static double chunksFromProbability(double probability) {
        if (probability <= 0.0D) return Double.POSITIVE_INFINITY;

        return 1.0D / probability;
    }

    /**
     * A minimum center spacing of r blocks guarantees at least a non-overlapping
     * protected disc of radius r / 2 around each successful structure.
     */
    public static double minimumSpacingChunks(double minDistanceBlocks) {
        if (minDistanceBlocks <= 0.0D) return 0.0D;

        double protectedRadiusChunks = minDistanceBlocks / 32.0D;
        return Math.PI * protectedRadiusChunks * protectedRadiusChunks;
    }

    public static double averageChunksForFixedCountInRadius(int structureCount, double outerRadiusChunks) {
        if (structureCount <= 0 || outerRadiusChunks <= 0.0D) return Double.POSITIVE_INFINITY;

        return Math.PI * outerRadiusChunks * outerRadiusChunks / structureCount;
    }

    public static LocalizedText fixedPosition() {
        return LocalizedText.translatable(
            "gui.structurescanner.rarity",
            LocalizedText.translatable("gui.structurescanner.rarity.fixed_position")
        );
    }
}