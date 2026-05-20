package com.simplestructurescanner.structure.iceandfire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.structure.util.RarityTextHelper;


/**
 * Structure provider for Ice and Fire mod.
 * Provides metadata for I&F's major structures.
 *
 * <p>Note: Ice and Fire structures use non-deterministic generation based on
 * terrain checks, biome conditions, random chance, and instance-based distance tracking.
 * As such, none of these structures can be reliably searched for.</p>
 */
public class IceAndFireStructureProvider implements StructureProvider {

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

    private List<ResourceLocation> knownStructures = new ArrayList<>();
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();
    private int worldGenDistance = DEFAULT_WORLD_GEN_DISTANCE;
    private int dragonDenChance = DEFAULT_DRAGON_DEN_CHANCE;
    private int dragonRoostChance = DEFAULT_DRAGON_ROOST_CHANCE;
    private int gorgonChance = DEFAULT_GORGON_CHANCE;
    private int cyclopsCaveChance = DEFAULT_CYCLOPS_CAVE_CHANCE;
    private int myrmexChance = DEFAULT_MYRMEX_CHANCE;

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public String getModName() {
        return MOD_NAME;
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(MOD_ID);
    }

    @Override
    public void postInit() {
        loadConfig();

        // Dragon structures
        addStructure("fire_dragon_roost", "gui.structurescanner.structures.iceandfire.fire_dragon_roost", 0, 0, 0);
        addStructure("ice_dragon_roost", "gui.structurescanner.structures.iceandfire.ice_dragon_roost", 0, 0, 0);
        addStructure("fire_dragon_cave", "gui.structurescanner.structures.iceandfire.fire_dragon_cave", 0, 0, 0);
        addStructure("ice_dragon_cave", "gui.structurescanner.structures.iceandfire.ice_dragon_cave", 0, 0, 0);
        addStructure("lightning_dragon_roost", "gui.structurescanner.structures.iceandfire.lightning_dragon_roost", 0, 0, 0);
        addStructure("lightning_dragon_cave", "gui.structurescanner.structures.iceandfire.lightning_dragon_cave", 0, 0, 0);

        // Misc
        addStructure("cyclops_cave", "gui.structurescanner.structures.iceandfire.cyclops_cave", 0, 0, 0);
        addStructure("gorgon_temple", "gui.structurescanner.structures.iceandfire.gorgon_temple", 0, 0, 0);

        // Myrmex hives
        addStructure("myrmex_hive_desert", "gui.structurescanner.structures.iceandfire.myrmex_hive_desert", 0, 0, 0);
        addStructure("myrmex_hive_jungle", "gui.structurescanner.structures.iceandfire.myrmex_hive_jungle", 0, 0, 0);

        populateStructureMetadata();
        populateStructureContents();
    }

    private void addStructure(String path, String displayNameKey, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = new ResourceLocation(MOD_ID, path);
        knownStructures.add(id);

        StructureInfo info = new StructureInfo(id, LocalizedText.translatable(displayNameKey), PROVIDER_ID, sizeX, sizeY, sizeZ);
        structureInfos.put(id, info);
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
        double spacingChunks = RarityTextHelper.minimumSpacingChunks(minDistanceBlocks);
        return RarityTextHelper.oneInChunks(Math.max(rawChunks, spacingChunks));
    }

    private void setMetadata(String path, Set<Biome> biomes, Set<DimensionInfo> dimensions, LocalizedText rarity) {
        StructureInfo info = structureInfos.get(new ResourceLocation(MOD_ID, path));
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    private void populateStructureContents() {
        // Fire Dragon Roost
        StructureInfo fireRoost = structureInfos.get(new ResourceLocation(MOD_ID, "fire_dragon_roost"));
        if (fireRoost != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "fire_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            fireRoost.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "firedragon"), 1));
            fireRoost.setEntities(entities);
        }

        // Fire Dragon Cave
        StructureInfo fireCave = structureInfos.get(new ResourceLocation(MOD_ID, "fire_dragon_cave"));
        if (fireCave != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "fire_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "fire_dragon_male_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            fireCave.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "firedragon"), 1));
            fireCave.setEntities(entities);
        }

        // Ice Dragon Roost
        StructureInfo iceRoost = structureInfos.get(new ResourceLocation(MOD_ID, "ice_dragon_roost"));
        if (iceRoost != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "ice_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            iceRoost.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "icedragon"), 1));
            iceRoost.setEntities(entities);
        }

        // Ice Dragon Cave
        StructureInfo iceCave = structureInfos.get(new ResourceLocation(MOD_ID, "ice_dragon_cave"));
        if (iceCave != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "ice_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "ice_dragon_male_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            iceCave.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "icedragon"), 1));
            iceCave.setEntities(entities);
        }

        // Lightning Dragon Roost
        StructureInfo lightningRoost = structureInfos.get(new ResourceLocation(MOD_ID, "lightning_dragon_roost"));
        if (lightningRoost != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "lightning_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            lightningRoost.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "lightningdragon"), 1));
            lightningRoost.setEntities(entities);
        }

        // Lightning Dragon Cave
        StructureInfo lightningCave = structureInfos.get(new ResourceLocation(MOD_ID, "lightning_dragon_cave"));
        if (lightningCave != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "lightning_dragon_female_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "lightning_dragon_male_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            lightningCave.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "lightningdragon"), 1));
            lightningCave.setEntities(entities);
        }

        // Cyclops Cave
        StructureInfo cyclopsCave = structureInfos.get(new ResourceLocation(MOD_ID, "cyclops_cave"));
        if (cyclopsCave != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "cyclops_cave"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            cyclopsCave.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "cyclops"), 1));
            cyclopsCave.setEntities(entities);
        }

        // Gorgon Temple
        StructureInfo gorgonTemple = structureInfos.get(new ResourceLocation(MOD_ID, "gorgon_temple"));
        if (gorgonTemple != null) {
            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "gorgon"), 1));
            gorgonTemple.setEntities(entities);
        }

        // Myrmex Hive Desert
        StructureInfo myrmexDesert = structureInfos.get(new ResourceLocation(MOD_ID, "myrmex_hive_desert"));
        if (myrmexDesert != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_loot_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_desert_food_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.iceandfire.cocoon")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_trash_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.iceandfire.cocoon")));
            myrmexDesert.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_queen"), 1));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_royal"), 2));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_sentinel"), 4));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_soldier"), 8));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_worker"), 12));
            myrmexDesert.setEntities(entities);
        }

        // Myrmex Hive Jungle
        StructureInfo myrmexJungle = structureInfos.get(new ResourceLocation(MOD_ID, "myrmex_hive_jungle"));
        if (myrmexJungle != null) {
            List<LootEntry> loot = new ArrayList<>();
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_loot_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.chest")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_jungle_food_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.iceandfire.cocoon")));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_trash_chest"),
                Collections.emptyList(), LocalizedText.translatable("gui.structurescanner.loot.iceandfire.cocoon")));
            myrmexJungle.setLootTables(loot);

            List<EntityEntry> entities = new ArrayList<>();
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_queen"), 1));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_royal"), 2));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_sentinel"), 4));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_soldier"), 8));
            entities.add(new EntityEntry(new ResourceLocation(MOD_ID, "myrmex_worker"), 12));
            myrmexJungle.setEntities(entities);
        }
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
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
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
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
