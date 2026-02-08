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
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;


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
    private static final String MOD_NAME = I18n.translateToLocal("gui.structurescanner.provider.iceandfire");

    private List<ResourceLocation> knownStructures = new ArrayList<>();
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

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

        String name = I18n.translateToLocal(displayNameKey);
        StructureInfo info = new StructureInfo(id, name, PROVIDER_ID, sizeX, sizeY, sizeZ);
        structureInfos.put(id, info);
    }

    private void populateStructureMetadata() {
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);

        // Fire Dragon Roost - warm, non-snowy hills/mountains
        Set<Biome> fireDragonBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (!biome.getEnableSnow()
                    && biome.getDefaultTemperature() > 0.0
                    && biome != Biomes.ICE_PLAINS
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.WET)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN)
                    && !BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
                fireDragonBiomes.add(biome);
            }
        }

        setMetadata("fire_dragon_roost", fireDragonBiomes, overworld, "gui.structurescanner.rarity.rare");
        setMetadata("fire_dragon_cave", fireDragonBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Ice Dragon Roost - cold, snowy biomes
        Set<Biome> iceDragonBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.COLD)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)) {
                iceDragonBiomes.add(biome);
            }
        }

        setMetadata("ice_dragon_roost", iceDragonBiomes, overworld, "gui.structurescanner.rarity.rare");
        setMetadata("ice_dragon_cave", iceDragonBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Lightning Dragon Roost - jungle, savanna, badlands biomes
        Set<Biome> lightningDragonBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)
                    || BiomeDictionary.hasType(biome, BiomeDictionary.Type.SAVANNA)
                    || BiomeDictionary.hasType(biome, BiomeDictionary.Type.MESA)) {
                lightningDragonBiomes.add(biome);
            }
        }

        setMetadata("lightning_dragon_roost", lightningDragonBiomes, overworld, "gui.structurescanner.rarity.rare");
        setMetadata("lightning_dragon_cave", lightningDragonBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Cyclops Cave - beach biomes
        Set<Biome> beachBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.BEACH)) beachBiomes.add(biome);
        }

        setMetadata("cyclops_cave", beachBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Gorgon Temple - beach biomes
        setMetadata("gorgon_temple", beachBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Myrmex Hive Desert - hot, dry, sandy biomes
        Set<Biome> desertBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.HOT)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.DRY)
                    && BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY)) {
                desertBiomes.add(biome);
            }
        }

        setMetadata("myrmex_hive_desert", desertBiomes, overworld, "gui.structurescanner.rarity.rare");

        // Myrmex Hive Jungle - jungle biomes
        Set<Biome> jungleBiomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.JUNGLE)) {
                jungleBiomes.add(biome);
            }
        }

        setMetadata("myrmex_hive_jungle", jungleBiomes, overworld, "gui.structurescanner.rarity.rare");
    }

    private void setMetadata(String path, Set<Biome> biomes, Set<DimensionInfo> dimensions, String rarity) {
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "fire_dragon_male_cave"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "ice_dragon_male_cave"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "lightning_dragon_male_cave"),
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_desert_food_chest"),
                Collections.emptyList(), "gui.structurescanner.loot.iceandfire.cocoon"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_trash_chest"),
                Collections.emptyList(), "gui.structurescanner.loot.iceandfire.cocoon"));
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
                Collections.emptyList(), "gui.structurescanner.loot.chest"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_jungle_food_chest"),
                Collections.emptyList(), "gui.structurescanner.loot.iceandfire.cocoon"));
            loot.add(new LootEntry(new ResourceLocation(MOD_ID, "myrmex_trash_chest"),
                Collections.emptyList(), "gui.structurescanner.loot.iceandfire.cocoon"));
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
