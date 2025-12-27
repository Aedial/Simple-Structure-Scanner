package com.simplestructurescanner.structure.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.text.translation.I18n;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProvider;


/**
 * Structure provider for vanilla Minecraft structures.
 * Uses seed-based algorithms to locate structures (similar to /locate).
 */
public class VanillaStructureProvider implements StructureProvider {
    private static final String PROVIDER_ID = "minecraft";
    private static final String MOD_NAME = "gui.structurescanner.provider.minecraft";

    private List<ResourceLocation> knownStructures;
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    public VanillaStructureProvider() {
        initKnownStructures();
    }

    private void initKnownStructures() {
        knownStructures = new ArrayList<>();

        // Overworld
        addStructure("village", "gui.structurescanner.structures.village", 0, 0, 0);
        addStructure("mineshaft", "gui.structurescanner.structures.mineshaft", 0, 0, 0);
        addStructure("stronghold", "gui.structurescanner.structures.stronghold", 0, 0, 0);
        addStructure("desert_temple", "gui.structurescanner.structures.desert_temple", 21, 21, 21);
        addStructure("jungle_temple", "gui.structurescanner.structures.jungle_temple", 12, 14, 15);
        addStructure("witch_hut", "gui.structurescanner.structures.witch_hut", 7, 5, 9);
        addStructure("igloo", "gui.structurescanner.structures.igloo", 7, 5, 8);
        addStructure("monument", "gui.structurescanner.structures.ocean_monument", 58, 23, 58);
        addStructure("mansion", "gui.structurescanner.structures.woodland_mansion", 0, 0, 0);
        addStructure("dungeon", "gui.structurescanner.structures.dungeon", 9, 7, 9);

        // Nether
        addStructure("fortress", "gui.structurescanner.structures.nether_fortress", 0, 0, 0);

        // End
        addStructure("endcity", "gui.structurescanner.structures.end_city", 0, 0, 0);
        addStructure("end_ship", "gui.structurescanner.structures.end_ship", 0, 0, 0);

        // Add blocks and loot tables to structures
        populateStructureContents();
    }

    /**
     * Populates blocks and loot tables for vanilla structures.
     * Since vanilla structures don't have a registry we can query, this data is hardcoded
     * based on vanilla Minecraft structure generation.
     */
    private void populateStructureContents() {
        populateDesertTemple();
        populateJungleTemple();
        populateWitchHut();
        populateIgloo();
        populateOceanMonument();
        populateDungeon();
        populateStronghold();
        populateMineshaft();
        populateNetherFortress();
        populateEndCity();
        populateWoodlandMansion();
        populateVillage();
    }

    // FIXME: we have some invalid blocks (shown as Air)

    private void populateDesertTemple() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "desert_temple"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.SANDSTONE, 0, 500),
            createBlockEntry(Blocks.SANDSTONE, 1, 100),  // Chiseled
            createBlockEntry(Blocks.SANDSTONE, 2, 50),   // Smooth
            createBlockEntry(Blocks.STAINED_HARDENED_CLAY, 1, 80),  // Orange
            createBlockEntry(Blocks.STAINED_HARDENED_CLAY, 11, 20), // Blue
            createBlockEntry(Blocks.SANDSTONE_STAIRS, 0, 30),
            createBlockEntry(Blocks.STONE_PRESSURE_PLATE, 0, 1),
            createBlockEntry(Blocks.TNT, 0, 9)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/desert_pyramid", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);
    }

    private void populateJungleTemple() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "jungle_temple"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.COBBLESTONE, 0, 400),
            createBlockEntry(Blocks.MOSSY_COBBLESTONE, 0, 200),
            createBlockEntry(Blocks.STONE_STAIRS, 0, 80),
            createBlockEntry(Blocks.LEVER, 0, 3),
            createBlockEntry(Blocks.TRIPWIRE_HOOK, 0, 4),
            createBlockEntry(Blocks.TRIPWIRE, 0, 5),
            createBlockEntry(Blocks.REDSTONE_WIRE, 0, 15),
            createBlockEntry(Blocks.DISPENSER, 0, 2),
            createBlockEntry(Blocks.STICKY_PISTON, 0, 3)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/jungle_temple", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/jungle_temple_dispenser", "gui.structurescanner.loot.dispenser")
        );
        info.setLootTables(loot);
    }

    private void populateWitchHut() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "witch_hut"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.LOG, 1, 16),  // Spruce log
            createBlockEntry(Blocks.PLANKS, 1, 30),  // Spruce planks
            createBlockEntry(Blocks.WOODEN_SLAB, 1, 20),  // Spruce slab
            createBlockEntry(Blocks.OAK_FENCE, 0, 4),
            createBlockEntry(Blocks.CAULDRON, 0, 1),
            createBlockEntry(Blocks.CRAFTING_TABLE, 0, 1),
            createBlockEntry(Blocks.FLOWER_POT, 0, 1)
        );
        info.setBlocks(blocks);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "witch"), "entity.witch.name", 1)
        );
        info.setEntities(entities);
    }

    private void populateIgloo() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "igloo"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.SNOW, 0, 100),
            createBlockEntry(Blocks.ICE, 0, 20),
            createBlockEntry(Blocks.WHITE_GLAZED_TERRACOTTA, 0, 1),
            createBlockEntry(Blocks.CARPET, 0, 10),
            createBlockEntry(Blocks.FURNACE, 0, 1),
            createBlockEntry(Blocks.CRAFTING_TABLE, 0, 1),
            createBlockEntry(Blocks.REDSTONE_TORCH, 0, 1),
            createBlockEntry(Blocks.BREWING_STAND, 0, 1),
            createBlockEntry(Blocks.CAULDRON, 0, 1)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/igloo_chest", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "villager"), "entity.villager.name", 1),
            new EntityEntry(new ResourceLocation("minecraft", "zombie_villager"), "entity.zombie_villager.name", 1)
        );
        info.setEntities(entities);
    }

    private void populateOceanMonument() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "monument"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.PRISMARINE, 0, 5000),
            createBlockEntry(Blocks.PRISMARINE, 1, 1000),  // Bricks
            createBlockEntry(Blocks.PRISMARINE, 2, 500),   // Dark
            createBlockEntry(Blocks.SEA_LANTERN, 0, 50),
            createBlockEntry(Blocks.SPONGE, 1, 30),  // Wet sponge
            createBlockEntry(Blocks.GOLD_BLOCK, 0, 8)
        );
        info.setBlocks(blocks);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "guardian"), "entity.guardian.name", 20),
            new EntityEntry(new ResourceLocation("minecraft", "elder_guardian"), "entity.elder_guardian.name", 3)
        );
        info.setEntities(entities);
    }

    private void populateDungeon() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "dungeon"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.COBBLESTONE, 0, 50),
            createBlockEntry(Blocks.MOSSY_COBBLESTONE, 0, 50),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 1),
            createBlockEntry(Blocks.CHEST, 0, 2)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/simple_dungeon", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "zombie"), "entity.zombie.name", 1, true),
            new EntityEntry(new ResourceLocation("minecraft", "skeleton"), "entity.skeleton.name", 1, true),
            new EntityEntry(new ResourceLocation("minecraft", "spider"), "entity.spider.name", 1, true)
        );
        info.setEntities(entities);
    }

    private void populateStronghold() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "stronghold"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.STONEBRICK, 0, 3000),
            createBlockEntry(Blocks.STONEBRICK, 1, 500),  // Mossy
            createBlockEntry(Blocks.STONEBRICK, 2, 500),  // Cracked
            createBlockEntry(Blocks.STONE_BRICK_STAIRS, 0, 200),
            createBlockEntry(Blocks.IRON_BARS, 0, 100),
            createBlockEntry(Blocks.IRON_DOOR, 0, 10),
            createBlockEntry(Blocks.BOOKSHELF, 0, 100),
            createBlockEntry(Blocks.END_PORTAL_FRAME, 0, 12),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 1)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/stronghold_corridor", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/stronghold_crossing", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/stronghold_library", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "silverfish"), "entity.silverfish.name", 1, true)
        );
        info.setEntities(entities);
    }

    private void populateMineshaft() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "mineshaft"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.PLANKS, 0, 500),
            createBlockEntry(Blocks.OAK_FENCE, 0, 200),
            createBlockEntry(Blocks.RAIL, 0, 300),
            createBlockEntry(Blocks.TORCH, 0, 100),
            createBlockEntry(Blocks.WEB, 0, 50),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 1)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/abandoned_mineshaft", "gui.structurescanner.loot.minecart_chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "cave_spider"), "entity.cave_spider.name", 1, true)
        );
        info.setEntities(entities);
    }

    private void populateNetherFortress() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "fortress"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.NETHER_BRICK, 0, 5000),
            createBlockEntry(Blocks.NETHER_BRICK_FENCE, 0, 500),
            createBlockEntry(Blocks.NETHER_BRICK_STAIRS, 0, 300),
            createBlockEntry(Blocks.NETHER_WART, 0, 50),
            createBlockEntry(Blocks.SOUL_SAND, 0, 20),
            createBlockEntry(Blocks.MOB_SPAWNER, 0, 2)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/nether_bridge", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "blaze"), "entity.blaze.name", 1, true),
            new EntityEntry(new ResourceLocation("minecraft", "wither_skeleton"), "entity.wither_skeleton.name", 1, false)
        );
        info.setEntities(entities);
    }

    private void populateEndCity() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "endcity"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.PURPUR_BLOCK, 0, 2000),
            createBlockEntry(Blocks.PURPUR_PILLAR, 0, 500),
            createBlockEntry(Blocks.PURPUR_STAIRS, 0, 300),
            createBlockEntry(Blocks.PURPUR_SLAB, 0, 200),
            createBlockEntry(Blocks.END_BRICKS, 0, 500),
            createBlockEntry(Blocks.END_ROD, 0, 100),
            createBlockEntry(Blocks.STAINED_GLASS, 2, 50)  // Magenta stained glass (meta 2)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/end_city_treasure", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "shulker"), "entity.shulker.name", 10)
        );
        info.setEntities(entities);

        // End ship info
        StructureInfo shipInfo = structureInfos.get(new ResourceLocation("minecraft", "end_ship"));
        if (shipInfo != null) {
            List<BlockEntry> shipBlocks = Arrays.asList(
                createBlockEntry(Blocks.PURPUR_BLOCK, 0, 300),
                createBlockEntry(Blocks.PURPUR_STAIRS, 0, 50),
                createBlockEntry(Blocks.OBSIDIAN, 0, 16),
                createBlockEntry(Blocks.END_ROD, 0, 10)
            );
            shipInfo.setBlocks(shipBlocks);

            List<LootEntry> shipLoot = Arrays.asList(
                createLootEntry("minecraft:chests/end_city_treasure", "gui.structurescanner.loot.chest")
            );
            shipInfo.setLootTables(shipLoot);

            List<EntityEntry> shipEntities = Arrays.asList(
                new EntityEntry(new ResourceLocation("minecraft", "shulker"), "entity.shulker.name", 3)
            );
            shipInfo.setEntities(shipEntities);
        }
    }

    private void populateWoodlandMansion() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "mansion"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.DARK_OAK_STAIRS, 0, 1000),
            createBlockEntry(Blocks.PLANKS, 5, 2000),  // Dark oak
            createBlockEntry(Blocks.LOG2, 1, 500),  // Dark oak log
            createBlockEntry(Blocks.COBBLESTONE, 0, 800),
            createBlockEntry(Blocks.GLASS_PANE, 0, 200),
            createBlockEntry(Blocks.CARPET, 0, 300),
            createBlockEntry(Blocks.BOOKSHELF, 0, 100),
            createBlockEntry(Blocks.CHEST, 0, 20)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/woodland_mansion", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "vindication_illager"), "entity.vindication_illager.name", 10),
            new EntityEntry(new ResourceLocation("minecraft", "evocation_illager"), "entity.evocation_illager.name", 3)
        );
        info.setEntities(entities);
    }

    private void populateVillage() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "village"));
        if (info == null) return;

        List<BlockEntry> blocks = Arrays.asList(
            createBlockEntry(Blocks.COBBLESTONE, 0, 500),
            createBlockEntry(Blocks.PLANKS, 0, 400),
            createBlockEntry(Blocks.LOG, 0, 200),
            createBlockEntry(Blocks.OAK_STAIRS, 0, 150),
            createBlockEntry(Blocks.OAK_FENCE, 0, 100),
            createBlockEntry(Blocks.GLASS_PANE, 0, 80),
            createBlockEntry(Blocks.TORCH, 0, 50),
            createBlockEntry(Blocks.CRAFTING_TABLE, 0, 5),
            createBlockEntry(Blocks.FURNACE, 0, 5),
            createBlockEntry(Blocks.CHEST, 0, 10),
            createBlockEntry(Blocks.FARMLAND, 0, 100),
            createBlockEntry(Blocks.WHEAT, 0, 100)
        );
        info.setBlocks(blocks);

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/village_blacksmith", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "villager"), "entity.villager.name", 10),
            new EntityEntry(new ResourceLocation("minecraft", "iron_golem"), "entity.iron_golem.name", 1)
        );
        info.setEntities(entities);
    }

    private BlockEntry createBlockEntry(Block block, int meta, int count) {
        ItemStack stack = new ItemStack(block, 1, meta);

        return new BlockEntry(block.getStateFromMeta(meta), stack, count);
    }

    private LootEntry createLootEntry(String lootTableId, String containerType) {
        return new LootEntry(new ResourceLocation(lootTableId), Collections.emptyList(), containerType);
    }

    private void addStructure(String path, String displayName, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = new ResourceLocation("minecraft", path);
        knownStructures.add(id);

        String name = I18n.translateToLocal(displayName);
        StructureInfo info = new StructureInfo(id, name, PROVIDER_ID, sizeX, sizeY, sizeZ);
        structureInfos.put(id, info);
    }

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
        return true;
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return knownStructures.contains(structureId) && !structureId.getPath().equals("dungeon");
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount) {
        if (world == null) return null;

        // FIXME: findNearest does not work at all. Derive structures from seed instead.

        String structureName = getVanillaStructureName(structureId);
        if (structureName == null) return null;

        // For structures that don't have a vanilla locator, return null
        if (!canBeSearched(structureId)) return null;

        BlockPos found = world.findNearestStructure(structureName, pos, false);
        if (found == null) return null;

        // skipCount > 0 means we need to find additional structures
        if (skipCount > 0) {
            List<BlockPos> foundStructures = findMultipleStructures(world, structureName, pos, skipCount + 1);

            if (skipCount < foundStructures.size()) {
                found = foundStructures.get(skipCount);

                return new StructureLocation(found, skipCount, foundStructures.size());
            }

            return null;
        }

        return new StructureLocation(found, 0, 1);
    }

    /**
     * Find multiple structures of the same type, sorted by distance.
     */
    private List<BlockPos> findMultipleStructures(World world, String structureName, BlockPos pos, int count) {
        Set<Long> foundChunks = new HashSet<>();
        List<BlockPos> foundStructures = new ArrayList<>();

        // First, find the nearest one
        BlockPos nearest = world.findNearestStructure(structureName, pos, false);
        if (nearest != null) {
            foundStructures.add(nearest);
            foundChunks.add(chunkKey(nearest));
        }

        // Search in expanding rings to find more structures
        int searchRadius = 500;
        for (int ring = 1; ring <= 20 && foundStructures.size() < count; ring++) {
            int checkDist = ring * searchRadius;

            // Check in 8 directions plus intermediate points
            BlockPos[] checkPositions = {
                pos.add(checkDist, 0, 0),
                pos.add(-checkDist, 0, 0),
                pos.add(0, 0, checkDist),
                pos.add(0, 0, -checkDist),
                pos.add(checkDist, 0, checkDist),
                pos.add(-checkDist, 0, checkDist),
                pos.add(checkDist, 0, -checkDist),
                pos.add(-checkDist, 0, -checkDist),
                pos.add(checkDist / 2, 0, checkDist),
                pos.add(-checkDist / 2, 0, checkDist),
                pos.add(checkDist, 0, checkDist / 2),
                pos.add(checkDist, 0, -checkDist / 2)
            };

            for (BlockPos checkPos : checkPositions) {
                BlockPos nearbyStructure = world.findNearestStructure(structureName, checkPos, false);
                if (nearbyStructure != null) {
                    long key = chunkKey(nearbyStructure);
                    if (!foundChunks.contains(key)) {
                        foundStructures.add(nearbyStructure);
                        foundChunks.add(key);
                    }
                }
            }
        }

        // Sort by distance from original position
        foundStructures.sort((a, b) -> Double.compare(a.distanceSq(pos), b.distanceSq(pos)));

        return foundStructures;
    }

    private long chunkKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * Convert our structure ID to vanilla's internal structure name for findNearestStructure.
     */
    @Nullable
    private String getVanillaStructureName(ResourceLocation structureId) {
        if (!structureId.getNamespace().equals("minecraft")) return null;

        switch (structureId.getPath()) {
            case "village":
                return "Village";
            case "mineshaft":
                return "Mineshaft";
            case "stronghold":
                return "Stronghold";
            case "desert_temple":
            case "jungle_temple":
            case "witch_hut":
            case "igloo":
                // All temples use the same "Temple" locator
                return "Temple";
            case "monument":
                return "Monument";
            case "mansion":
                return "Mansion";
            case "endcity":
            case "end_ship":
                // End ships are part of end cities
                return "EndCity";
            case "fortress":
                return "Fortress";
            case "dungeon":
                return "Dungeon";
            default:
                return null;
        }
    }
}
