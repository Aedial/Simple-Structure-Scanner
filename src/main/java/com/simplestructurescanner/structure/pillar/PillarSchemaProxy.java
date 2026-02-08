package com.simplestructurescanner.structure.pillar;

import java.util.List;

/**
 * Proxy for Pillar's StructureSchema class.
 * <p>
 * This class holds the data we need from Pillar structures without requiring
 * Pillar to be loaded at compile time. We use reflection to access Pillar's
 * actual StructureSchema objects and extract the data we need.
 */
public class PillarSchemaProxy {

    public final String structureName;
    public final PillarGeneratorType generatorType;
    public final int maxY;
    public final int minY;
    public final int rarity;
    public final int minDistanceToSameTypeStructures;
    public final List<Integer> dimensionSpawns;
    public final List<String> biomeNameSpawns;
    public final List<String> biomeTagSpawns;
    public final boolean isDimensionSpawnsBlacklist;
    public final boolean isBiomeNameSpawnsBlacklist;
    public final boolean isBiomeTagSpawnsBlacklist;
    public final boolean generateEverywhere;

    /**
     * Create a proxy from extracted schema data.
     */
    public PillarSchemaProxy(
            String structureName,
            PillarGeneratorType generatorType,
            int maxY,
            int minY,
            int rarity,
            int minDistanceToSameTypeStructures,
            List<Integer> dimensionSpawns,
            List<String> biomeNameSpawns,
            List<String> biomeTagSpawns,
            boolean isDimensionSpawnsBlacklist,
            boolean isBiomeNameSpawnsBlacklist,
            boolean isBiomeTagSpawnsBlacklist,
            boolean generateEverywhere) {

        this.structureName = structureName;
        this.generatorType = generatorType;
        this.maxY = maxY;
        this.minY = minY;
        this.rarity = rarity;
        this.minDistanceToSameTypeStructures = minDistanceToSameTypeStructures;
        this.dimensionSpawns = dimensionSpawns;
        this.biomeNameSpawns = biomeNameSpawns;
        this.biomeTagSpawns = biomeTagSpawns;
        this.isDimensionSpawnsBlacklist = isDimensionSpawnsBlacklist;
        this.isBiomeNameSpawnsBlacklist = isBiomeNameSpawnsBlacklist;
        this.isBiomeTagSpawnsBlacklist = isBiomeTagSpawnsBlacklist;
        this.generateEverywhere = generateEverywhere;
    }

    // TODO: handle blacklist/whitelist logic
    public List<String> getAllBiomeSpawns() {
        return biomeNameSpawns.isEmpty() ? biomeTagSpawns : biomeNameSpawns;
    }

    public List<Integer> getAllDimensionSpawns() {
        return dimensionSpawns;
    }

    @Override
    public String toString() {
        return "PillarSchemaProxy{" +
                "structureName='" + structureName + '\'' +
                ", generatorType=" + generatorType +
                ", rarity=" + rarity +
                '}';
    }
}
