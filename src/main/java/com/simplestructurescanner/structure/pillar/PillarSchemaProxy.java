package com.simplestructurescanner.structure.pillar;

import java.util.List;
import java.util.Set;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

/**
 * Proxy for Pillar's StructureSchema class.
 * <p>
 * This class holds the data we need from Pillar structures without requiring
 * Pillar to be loaded at compile time. We use reflection to access Pillar's
 * actual StructureSchema objects and extract the data we need.
 */
public final class PillarSchemaProxy {

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

    /**
     * Evaluate Pillar's dimension rules for one candidate dimension.
     */
    public boolean canSpawnInDimension(int dimensionId) {
        if (generateEverywhere || dimensionSpawns.isEmpty()) return true;

        if (isDimensionSpawnsBlacklist) return !dimensionSpawns.contains(dimensionId);

        return dimensionSpawns.contains(dimensionId);
    }

    /**
     * Evaluate Pillar's biome-name and biome-tag rules for one candidate biome.
     * The name rules short-circuit exactly like Pillar's WorldGenerator does.
     */
    public boolean canSpawnInBiome(Biome biome) {
        if (generateEverywhere || biome == null || biome.getRegistryName() == null) return generateEverywhere;

        String name = biome.getRegistryName().toString();

        if (isBiomeNameSpawnsBlacklist && !biomeNameSpawns.contains(name)) return true;
        if (biomeNameSpawns.contains(name)) return !isBiomeNameSpawnsBlacklist;

        try {
            Set<BiomeDictionary.Type> types = BiomeDictionary.getTypes(biome);

            if (isBiomeTagSpawnsBlacklist) {
                for (BiomeDictionary.Type type : types) {
                    if (biomeTagSpawns.contains(type.getName())) return false;
                }

                return true;
            }

            for (BiomeDictionary.Type type : types) {
                if (biomeTagSpawns.contains(type.getName())) return true;
            }
        } catch (NullPointerException e) {
            // Biome not properly registered in BiomeDictionary.
        }

        return false;
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
