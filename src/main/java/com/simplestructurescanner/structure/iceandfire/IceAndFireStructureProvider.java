package com.simplestructurescanner.structure.iceandfire;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.util.RarityTextHelper;


/**
 * Structure provider for Ice and Fire mod.
 * Provides metadata for I&F's major structures.
 *
 * <p>Note: Ice and Fire structures use non-deterministic generation based on
 * terrain checks, biome conditions, random chance, and instance-based distance tracking.
 * As such, none of these structures can be reliably searched for.</p>
 */
public class IceAndFireStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "iceandfire";
    private static final String MOD_ID = "iceandfire";
    private static final String MOD_NAME = "gui.structurescanner.provider.iceandfire";
    private static final int DEFAULT_WORLD_GEN_DISTANCE = 150;
    private static final int DEFAULT_DRAGON_DEN_CHANCE = 180;
    private static final int DEFAULT_DRAGON_ROOST_CHANCE = 360;
    private static final int DEFAULT_GORGON_CHANCE = 75;
    private static final int DEFAULT_CYCLOPS_CAVE_CHANCE = 170;
    private static final int DEFAULT_MYRMEX_CHANCE = 150;
    private static final int MYRMEX_MIN_DISTANCE_BLOCKS = 500;

    private int worldGenDistance = DEFAULT_WORLD_GEN_DISTANCE;
    private int dragonDenChance = DEFAULT_DRAGON_DEN_CHANCE;
    private int dragonRoostChance = DEFAULT_DRAGON_ROOST_CHANCE;
    private int gorgonChance = DEFAULT_GORGON_CHANCE;
    private int cyclopsCaveChance = DEFAULT_CYCLOPS_CAVE_CHANCE;
    private int myrmexChance = DEFAULT_MYRMEX_CHANCE;

    public IceAndFireStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();
        loadConfig();

        // Dragon structures
        registerStructure("fire_dragon_roost", "gui.structurescanner.structures.iceandfire.fire_dragon_roost", 0, 0, 0);
        registerStructure("ice_dragon_roost", "gui.structurescanner.structures.iceandfire.ice_dragon_roost", 0, 0, 0);
        registerStructure("fire_dragon_cave", "gui.structurescanner.structures.iceandfire.fire_dragon_cave", 0, 0, 0);
        registerStructure("ice_dragon_cave", "gui.structurescanner.structures.iceandfire.ice_dragon_cave", 0, 0, 0);
        registerStructure("lightning_dragon_roost", "gui.structurescanner.structures.iceandfire.lightning_dragon_roost", 0, 0, 0);
        registerStructure("lightning_dragon_cave", "gui.structurescanner.structures.iceandfire.lightning_dragon_cave", 0, 0, 0);

        // Misc
        registerStructure("cyclops_cave", "gui.structurescanner.structures.iceandfire.cyclops_cave", 0, 0, 0);
        registerStructure("gorgon_temple", "gui.structurescanner.structures.iceandfire.gorgon_temple", 0, 0, 0);
        // TODO: add mausoleum
        // TODO: add hydra_lair

        // Myrmex hives
        registerStructure("myrmex_hive_desert", "gui.structurescanner.structures.iceandfire.myrmex_hive_desert", 0, 0, 0);
        registerStructure("myrmex_hive_jungle", "gui.structurescanner.structures.iceandfire.myrmex_hive_jungle", 0, 0, 0);

        populateStructureMetadata();
        populateStructureContents();
    }

    private void loadConfig() {
        try {
            Class<?> iceAndFireClass = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
            Object config = iceAndFireClass.getField("CONFIG").get(null);
            Class<?> configClass = config.getClass();

            worldGenDistance = configClass.getField("worldGenDistance").getInt(config);
            dragonDenChance = configClass.getField("generateDragonDenChance").getInt(config);
            dragonRoostChance = configClass.getField("generateDragonRoostChance").getInt(config);
            gorgonChance = configClass.getField("spawnGorgonsChance").getInt(config);
            cyclopsCaveChance = configClass.getField("spawnCyclopsCaveChance").getInt(config);
            myrmexChance = configClass.getField("myrmexColonyGenChance").getInt(config);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not load Ice and Fire config, using defaults: {}", e.getMessage());
        }
    }

    private void populateStructureMetadata() {
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);

        // Fire Dragon Roost - warm, non-snowy land biomes
        Set<Biome> fireDragonRoostBiomes = new HashSet<>();
        Set<Biome> fireDragonCaveBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (!biome.getEnableSnow()
                    && biome.getDefaultTemperature() > 0.0F
                    && biome != Biomes.ICE_PLAINS
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.WET)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
                fireDragonRoostBiomes.add(biome);

                if (!BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) {
                    fireDragonCaveBiomes.add(biome);
                }
            }
        }

        setMetadata("fire_dragon_roost", fireDragonRoostBiomes, overworld,
            calculateDragonRarity(fireDragonRoostBiomes, true, dragonRoostChance));
        setMetadata("fire_dragon_cave", fireDragonCaveBiomes, overworld,
            calculateDragonRarity(fireDragonCaveBiomes, false, dragonDenChance));

        // Ice Dragon Roost - cold, snowy biomes
        Set<Biome> iceDragonRoostBiomes = new HashSet<>();
        Set<Biome> iceDragonCaveBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)) {
                iceDragonRoostBiomes.add(biome);

                if (!BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) {
                    iceDragonCaveBiomes.add(biome);
                }
            }
        }

        setMetadata("ice_dragon_roost", iceDragonRoostBiomes, overworld,
            calculateDragonRarity(iceDragonRoostBiomes, true, dragonRoostChance));
        setMetadata("ice_dragon_cave", iceDragonCaveBiomes, overworld,
            calculateDragonRarity(iceDragonCaveBiomes, false, dragonDenChance));

        // Lightning Dragon Roost/Cave - jungle, mesa, savanna biomes
        Set<Biome> lightningDragonBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)
                    || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MESA)
                    || BiomeDictionary.hasType(biome, BiomeDictionary.Type.SAVANNA)) {
                lightningDragonBiomes.add(biome);
            }
        }

        setMetadata("lightning_dragon_roost", lightningDragonBiomes, overworld,
            calculateDragonRarity(lightningDragonBiomes, true, dragonRoostChance));
        setMetadata("lightning_dragon_cave", lightningDragonBiomes, overworld,
            calculateDragonRarity(lightningDragonBiomes, false, dragonDenChance));

        // Cyclops Cave - beach biomes
        Set<Biome> beachBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) beachBiomes.add(biome);
        }

        setMetadata("cyclops_cave", beachBiomes, overworld,
            calculateApproximateRarity(cyclopsCaveChance + 1.0D, worldGenDistance));

        // Gorgon Temple - beach biomes
        setMetadata("gorgon_temple", beachBiomes, overworld,
            calculateApproximateRarity(gorgonChance + 1.0D, worldGenDistance));

        // Myrmex Hive Desert - hot, dry, sandy biomes
        Set<Biome> desertBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HOT)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.DRY)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY)) {
                desertBiomes.add(biome);
            }
        }

        setMetadata("myrmex_hive_desert", desertBiomes, overworld,
            calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));

        // Myrmex Hive Jungle - jungle biomes
        Set<Biome> jungleBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)) {
                jungleBiomes.add(biome);
            }
        }

        setMetadata("myrmex_hive_jungle", jungleBiomes, overworld,
            calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));
    }

    private LocalizedText calculateDragonRarity(Set<Biome> biomes, boolean roost, int baseChance) {
        if (biomes.isEmpty()) return calculateApproximateRarity(baseChance + 1.0D, worldGenDistance);

        int hillBiomes = 0;

        for (Biome biome : biomes) {
            if (isDragonHillBiome(biome, roost)) hillBiomes++;
        }

        int flatBiomes = biomes.size() - hillBiomes;
        double hillProbability = 1.0D / (baseChance + 1.0D);
        double flatProbability = 1.0D / (baseChance * 2.0D + 1.0D);
        double averageProbability = (hillBiomes * hillProbability + flatBiomes * flatProbability) / biomes.size();

        return calculateApproximateRarity(RarityTextHelper.chunksFromProbability(averageProbability), worldGenDistance);
    }

    private boolean isDragonHillBiome(Biome biome, boolean roost) {
        if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HILLS)) return true;
        if (!BiomeDictionary.hasType(biome, BiomeDictionary.Type.MOUNTAIN)) return false;
        if (!roost) return true;

        return !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY);
    }

    private LocalizedText calculateApproximateRarity(double rawChunks, double minDistanceBlocks) {
        return RarityTextHelper.withMinimumSpacing(rawChunks, minDistanceBlocks);
    }

    private void populateStructureContents() {
        applyStructureContentsFromNbt("fire_dragon_roost");
        applyStructureContentsFromNbt("ice_dragon_roost");
        applyStructureContentsFromNbt("fire_dragon_cave");
        applyStructureContentsFromNbt("ice_dragon_cave");
        applyStructureContentsFromNbt("lightning_dragon_roost");
        applyStructureContentsFromNbt("lightning_dragon_cave");
        applyStructureContentsFromNbt("cyclops_cave");
        applyStructureContentsFromNbt("gorgon_temple");
        applyStructureContentsFromNbt("myrmex_hive_desert");
        applyStructureContentsFromNbt("myrmex_hive_jungle");

        // Fire Dragon Cave
        setLootTablesIfMissing("fire_dragon_cave",
            createLootEntry("iceandfire:fire_dragon_female_cave", "gui.structurescanner.loot.chest"),
            createLootEntry("iceandfire:fire_dragon_male_cave", "gui.structurescanner.loot.chest"));
        setEntitiesIfMissing("fire_dragon_cave", createEntityEntry("iceandfire:firedragon", 1));

        // Ice Dragon Cave
        setLootTablesIfMissing("ice_dragon_cave",
            createLootEntry("iceandfire:ice_dragon_female_cave", "gui.structurescanner.loot.chest"),
            createLootEntry("iceandfire:ice_dragon_male_cave", "gui.structurescanner.loot.chest"));
        setEntitiesIfMissing("ice_dragon_cave", createEntityEntry("iceandfire:icedragon", 1));

        // Lightning Dragon Roost
        setLootTablesIfMissing("lightning_dragon_roost",
            createLootEntry("iceandfire:lightning_dragon_female_cave", "gui.structurescanner.loot.chest"));
        setEntitiesIfMissing("lightning_dragon_roost", createEntityEntry("iceandfire:lightningdragon", 1));

        // Lightning Dragon Cave
        setLootTablesIfMissing("lightning_dragon_cave",
            createLootEntry("iceandfire:lightning_dragon_female_cave", "gui.structurescanner.loot.chest"),
            createLootEntry("iceandfire:lightning_dragon_male_cave", "gui.structurescanner.loot.chest"));
        setEntitiesIfMissing("lightning_dragon_cave", createEntityEntry("iceandfire:lightningdragon", 1));

        // Myrmex Hive Jungle
        setLootTablesIfMissing("myrmex_hive_jungle",
            createLootEntry("iceandfire:myrmex_loot_chest", "gui.structurescanner.loot.chest"),
            createLootEntry("iceandfire:myrmex_jungle_food_chest", "gui.structurescanner.loot.iceandfire.cocoon"),
            createLootEntry("iceandfire:myrmex_trash_chest", "gui.structurescanner.loot.iceandfire.cocoon"));
        setEntitiesIfMissing("myrmex_hive_jungle",
            createEntityEntry("iceandfire:myrmex_queen", 1),
            createEntityEntry("iceandfire:myrmex_royal", 2),
            createEntityEntry("iceandfire:myrmex_sentinel", 4),
            createEntityEntry("iceandfire:myrmex_soldier", 8),
            createEntityEntry("iceandfire:myrmex_worker", 12));
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        // Ice and Fire structures use non-deterministic generation
        // based on terrain checks, random chance, and instance-based distance tracking.
        // None can be reliably searched for.
        return false;
    }
    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId,
            BlockPos pos, int skipCount, @Nullable Predicate<BlockPos> locationFilter) {
        // Not searchable
        return null;
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId,
            BlockPos pos, int maxResults) {
        // Not searchable
        return null;
    }
}
