package com.simplestructurescanner.structure.providers.iceandfire;

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
import com.simplestructurescanner.structure.providers.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
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

    private static final int MYRMEX_MIN_DISTANCE_BLOCKS = 500;
    private static final String COCOON_KEY = "gui.structurescanner.loot.iceandfire.cocoon";

    private int worldGenDistance = 150;
    private int dragonDenChance = 180;
    private int dragonRoostChance = 360;
    private int gorgonChance = 75;
    private int cyclopsCaveChance = 170;
    private int mausoleumChance = 1800;
    private int hydraChance = 200;
    private int myrmexChance = 150;

    public IceAndFireStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();
        loadConfig();
        registerStructures();
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
            mausoleumChance = configClass.getField("generateMausoleumChance").getInt(config);
            hydraChance = configClass.getField("generateHydraChance").getInt(config);
            myrmexChance = configClass.getField("myrmexColonyGenChance").getInt(config);
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Could not load Ice and Fire config, using defaults: {}", e.getMessage());
        }
    }

    private void registerStructures() {
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);

        // Fire Dragon Roost - warm, non-snowy land biomes
        Set<Biome> fireDragonRoostBiomes = new HashSet<>();
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
            }
        }
        register("fire_dragon_roost")
            .fromBundled()
            .withMetadata(fireDragonRoostBiomes, overworld)
            .withRarity(calculateDragonRarity(fireDragonRoostBiomes, true, dragonRoostChance));

        Set<Biome> fireDragonCaveBiomes = hasBiomesBut(fireDragonRoostBiomes, BiomeDictionary.Type.BEACH);
        register("fire_dragon_cave")
            .fromBundled()
            .withLootTables(
                new LootEntry("iceandfire:fire_dragon_female_cave", CHEST_KEY),
                new LootEntry("iceandfire:fire_dragon_male_cave", CHEST_KEY))
            .withEntities(new EntityEntry("iceandfire:firedragon", 1))
            .withMetadata(fireDragonCaveBiomes, overworld)
            .withRarity(calculateDragonRarity(fireDragonCaveBiomes, false, dragonDenChance));

        // Ice Dragon Roost - cold & snowy biomes
        Set<Biome> iceDragonRoostBiomes = hasAllBiomes(BiomeDictionary.Type.COLD, BiomeDictionary.Type.SNOWY);
        register("ice_dragon_roost")
            .fromBundled()
            .withMetadata(iceDragonRoostBiomes, overworld)
            .withRarity(calculateDragonRarity(iceDragonRoostBiomes, true, dragonRoostChance));

        Set<Biome> iceDragonCaveBiomes = hasBiomesBut(iceDragonRoostBiomes, BiomeDictionary.Type.BEACH);
        register("ice_dragon_cave")
            .fromBundled()
            .withLootTables(
                new LootEntry("iceandfire:ice_dragon_female_cave", CHEST_KEY),
                new LootEntry("iceandfire:ice_dragon_male_cave", CHEST_KEY))
            .withEntities(new EntityEntry("iceandfire:icedragon", 1))
            .withMetadata(iceDragonCaveBiomes, overworld)
            .withRarity(calculateDragonRarity(iceDragonCaveBiomes, false, dragonDenChance));

        // Lightning Dragon Roost/Cave - jungle, mesa, savanna biomes
        Set<Biome> lightningDragonBiomes = hasAnyBiomes(BiomeDictionary.Type.JUNGLE,
            BiomeDictionary.Type.MESA, BiomeDictionary.Type.SAVANNA);

        register("lightning_dragon_roost")
            .fromBundled()
            .withLootTables(new LootEntry("iceandfire:lightning_dragon_female_cave", CHEST_KEY))
            .withEntities(new EntityEntry("iceandfire:lightningdragon", 1))
            .withMetadata(lightningDragonBiomes, overworld)
            .withRarity(calculateDragonRarity(lightningDragonBiomes, true, dragonRoostChance));

        register("lightning_dragon_cave")
            .fromBundled()
            .withLootTables(
                new LootEntry("iceandfire:lightning_dragon_female_cave", CHEST_KEY),
                new LootEntry("iceandfire:lightning_dragon_male_cave", CHEST_KEY))
            .withEntities(new EntityEntry("iceandfire:lightningdragon", 1))
            .withMetadata(lightningDragonBiomes, overworld)
            .withRarity(calculateDragonRarity(lightningDragonBiomes, false, dragonDenChance));

        // Cyclops Cave - beach biomes
        Set<Biome> beachBiomes = hasAnyBiomes(BiomeDictionary.Type.BEACH);

        register("cyclops_cave")
            .fromBundled()
            .withMetadata(beachBiomes, overworld)
            .withRarity(calculateApproximateRarity(cyclopsCaveChance + 1.0D, worldGenDistance));

        // Gorgon Temple - beach biomes
        register("gorgon_temple")
            .fromBundled()
            .withMetadata(beachBiomes, overworld)
            .withRarity(calculateApproximateRarity(gorgonChance + 1.0D, worldGenDistance));

        // Mausoleum - cold, snowy biomes
        register("mausoleum")
            .fromBundled()
            .withMetadata(iceDragonRoostBiomes, overworld)
            .withRarity(calculateApproximateRarity(mausoleumChance + 1.0D, worldGenDistance));

        // Hydra Lair - swamp biomes
        Set<Biome> swampBiomes = hasAnyBiomes(BiomeDictionary.Type.SWAMP);

        register("hydra_lair")
            .fromBundled()
            .withMetadata(swampBiomes, overworld)
            .withRarity(calculateApproximateRarity(hydraChance + 1.0D, worldGenDistance));

        // Myrmex Hive Desert - hot & dry & sandy biomes
        Set<Biome> desertBiomes = hasAllBiomes(BiomeDictionary.Type.HOT,
            BiomeDictionary.Type.DRY, BiomeDictionary.Type.SANDY);

        register("myrmex_hive_desert")
            .fromBundled()
            .withMetadata(desertBiomes, overworld)
            .withRarity(calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));

        // Myrmex Hive Jungle - jungle biomes
        Set<Biome> jungleBiomes = hasAnyBiomes(BiomeDictionary.Type.JUNGLE);
        register("myrmex_hive_jungle")
            .fromBundled()
            .withLootTables(
                new LootEntry("iceandfire:myrmex_loot_chest", CHEST_KEY),
                new LootEntry("iceandfire:myrmex_jungle_food_chest", COCOON_KEY),
                new LootEntry("iceandfire:myrmex_trash_chest", COCOON_KEY))
            .withEntities(
                new EntityEntry("iceandfire:myrmex_queen", 1),
                new EntityEntry("iceandfire:myrmex_royal", 2),
                new EntityEntry("iceandfire:myrmex_sentinel", 4),
                new EntityEntry("iceandfire:myrmex_soldier", 8),
                new EntityEntry("iceandfire:myrmex_worker", 12))
            .withMetadata(jungleBiomes, overworld)
            .withRarity(calculateApproximateRarity(myrmexChance, MYRMEX_MIN_DISTANCE_BLOCKS));
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
