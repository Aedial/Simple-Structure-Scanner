package com.simplestructurescanner.structure.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.util.text.translation.I18n;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureProvider;


/**
 * Structure provider for vanilla Minecraft structures.
 * Uses seed-based algorithms to locate structures.
 */
public class VanillaStructureProvider implements StructureProvider {
    private static final String PROVIDER_ID = "minecraft";
    private static final Random RANDOM = new Random();
    private static final String MOD_NAME = I18n.translateToLocal("gui.structurescanner.provider.minecraft");

    private List<ResourceLocation> knownStructures;
    private Map<ResourceLocation, StructureInfo> structureInfos = new HashMap<>();

    public VanillaStructureProvider() {
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
    public void postInit() {
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

        // Add biome, dimension, and rarity data
        populateStructureMetadata();
    }

    /**
     * Populates biome, dimension, and rarity info for vanilla structures.
     */
    private void populateStructureMetadata() {
        // Dimension IDs
        Set<Integer> overworld = Collections.singleton(0);
        Set<Integer> nether = Collections.singleton(-1);
        Set<Integer> end = Collections.singleton(1);

        // Village - Plains, Savanna, Desert, Taiga, Snowy Tundra
        setMetadata("village", biomes(Biomes.PLAINS, Biomes.SAVANNA, Biomes.DESERT, Biomes.TAIGA,
                Biomes.ICE_PLAINS, Biomes.MUTATED_PLAINS, Biomes.SAVANNA_PLATEAU), overworld, "gui.structurescanner.rarity.common");

        // Mineshaft - any biome underground
        setMetadata("mineshaft", null, overworld, "gui.structurescanner.rarity.common");

        // Stronghold - most overworld biomes
        setMetadata("stronghold", null, overworld, "gui.structurescanner.rarity.rare");

        // Desert Temple - Desert, Desert Hills
        setMetadata("desert_temple", biomes(Biomes.DESERT, Biomes.DESERT_HILLS, Biomes.MUTATED_DESERT), overworld, "gui.structurescanner.rarity.uncommon");

        // Jungle Temple - Jungle, Jungle Hills
        setMetadata("jungle_temple", biomes(Biomes.JUNGLE, Biomes.JUNGLE_HILLS, Biomes.MUTATED_JUNGLE), overworld, "gui.structurescanner.rarity.uncommon");

        // Witch Hut - Swamp
        setMetadata("witch_hut", biomes(Biomes.SWAMPLAND, Biomes.MUTATED_SWAMPLAND), overworld, "gui.structurescanner.rarity.uncommon");

        // Igloo - Snowy biomes
        setMetadata("igloo", biomes(Biomes.ICE_PLAINS, Biomes.COLD_TAIGA), overworld, "gui.structurescanner.rarity.uncommon");

        // Ocean Monument - Deep Ocean
        setMetadata("monument", biomes(Biomes.DEEP_OCEAN), overworld, "gui.structurescanner.rarity.uncommon");

        // Woodland Mansion - Roofed Forest
        setMetadata("mansion", biomes(Biomes.ROOFED_FOREST, Biomes.MUTATED_ROOFED_FOREST), overworld, "gui.structurescanner.rarity.rare");

        // Dungeon - any biome underground
        setMetadata("dungeon", null, overworld, "gui.structurescanner.rarity.common");

        // Nether Fortress
        setMetadata("fortress", null, nether, "gui.structurescanner.rarity.common");

        // End City & End Ship
        setMetadata("endcity", null, end, "gui.structurescanner.rarity.uncommon");
        setMetadata("end_ship", null, end, "gui.structurescanner.rarity.uncommon");
    }

    private Set<Biome> biomes(Biome... biomes) {
        return Stream.of(biomes).collect(Collectors.toSet());
    }

    private void setMetadata(String path, Set<Biome> biomes, Set<Integer> dimensions, String rarity) {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", path));
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    /**
     * Populates blocks and loot tables for vanilla structures.
     * Uses NBT parsing for non-procedural structures, falls back to hardcoded data for procedural ones.
     */
    private void populateStructureContents() {
        // Try to parse NBT files for non-procedural structures
        parseNBTStructures();

        // Fill in remaining data (loot tables, entities) and fallback for structures without NBT
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

    /**
     * Parse NBT structure files for non-procedural vanilla structures.
     *
     * Procedural structures (no NBT files, generated algorithmically):
     * - Desert Temple, Jungle Temple, Witch Hut (MapGenScatteredFeature)
     * - Ocean Monument (StructureOceanMonument)
     * - Village, Stronghold, Mineshaft, Mansion, Dungeon, Nether Fortress
     */
    private void parseNBTStructures() {

        // FIXME: we need a fake world to properly parse the rest (with GenStructure classes)
        //        The fake world should be limited to 5x5 chunks for performance

        // Igloo structures
        parseAndApplyNBT("igloo", "igloo/igloo_bottom");
        parseAndApplyNBT("igloo", "igloo/igloo_middle");
        parseAndApplyNBT("igloo", "igloo/igloo_top");
    }

    /**
     * Parse an NBT structure file and apply its data to a structure.
     */
    private void parseAndApplyNBT(String structurePath, String nbtPath) {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", structurePath));
        if (info == null) {
            SimpleStructureScanner.LOGGER.error("StructureInfo not found for {}, this should not happen.", structurePath);
            return;
        }

        StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseStructure(nbtPath);
        if (parsed == null) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse NBT structure {} for {}", nbtPath, structurePath);
            return;
        }

        // Only apply if we don't already have block data (first NBT wins, or merge)
        if (info.getBlocks().isEmpty()) {
            info.setBlocks(parsed.blocks);
        } else {
            // Merge block counts from additional structure parts
            mergeBlocks(info, parsed.blocks);
        }

        // Merge layer data (combine layers from multiple structure parts)
        if (!parsed.layers.isEmpty()) {
            if (!info.hasLayerData()) {
                info.setLayers(parsed.layers);
            } else {
                mergeLayers(info, parsed.layers);
            }
        }

        // Merge entities
        if (!parsed.entities.isEmpty()) {
            if (info.getEntities().isEmpty()) {
                info.setEntities(parsed.entities);
            } else {
                // Merge entity counts
                List<EntityEntry> merged = new ArrayList<>(info.getEntities());
                for (EntityEntry newEntity : parsed.entities) {
                    boolean found = false;
                    for (int i = 0; i < merged.size(); i++) {
                        if (merged.get(i).entityId.equals(newEntity.entityId)) {
                            merged.set(i, new EntityEntry(newEntity.entityId,
                                merged.get(i).count + newEntity.count, merged.get(i).spawner));
                            found = true;

                            break;
                        }
                    }

                    if (!found) merged.add(newEntity);
                }
                info.setEntities(merged);
            }
        }

        SimpleStructureScanner.LOGGER.debug("Parsed NBT structure {} for {}: {} blocks, {} layers",
            nbtPath, structurePath, parsed.blocks.size(), parsed.layers.size());
    }

    /**
     * Merge additional blocks into existing block list.
     */
    private void mergeBlocks(StructureInfo info, List<BlockEntry> newBlocks) {
        Map<String, BlockEntry> blockMap = new HashMap<>();

        // Add existing blocks
        for (BlockEntry entry : info.getBlocks()) {
            String key = getBlockKey(entry.blockState);
            blockMap.put(key, entry);
        }

        // Merge new blocks
        for (BlockEntry newEntry : newBlocks) {
            String key = getBlockKey(newEntry.blockState);
            BlockEntry existing = blockMap.get(key);

            if (existing != null) {
                // Add counts
                blockMap.put(key, new BlockEntry(existing.blockState, existing.displayStack, existing.count + newEntry.count));
            } else {
                blockMap.put(key, newEntry);
            }
        }

        // Convert back to list and sort
        List<BlockEntry> merged = new ArrayList<>(blockMap.values());
        merged.sort((a, b) -> Integer.compare(b.count, a.count));
        info.setBlocks(merged);
    }

    private String getBlockKey(IBlockState state) {
        return state.getBlock().getRegistryName() + "@" + state.getBlock().getMetaFromState(state);
    }

    /**
     * Merge additional layers into existing layer list.
     * New layers are stacked on top of existing layers (offset by max Y + 1).
     */
    private void mergeLayers(StructureInfo info, List<StructureLayer> newLayers) {
        mergeLayers(info, newLayers, 0, 0);
    }

    /**
     * Merge additional layers into existing layer list with X/Z offsets.
     * New layers are stacked on top of existing layers (offset by max Y + 1).
     */
    private void mergeLayers(StructureInfo info, List<StructureLayer> newLayers, int xOffset, int zOffset) {
        List<StructureLayer> existing = new ArrayList<>(info.getLayers());

        // Find the maximum Y in existing layers to stack new parts on top
        int maxExistingY = Integer.MIN_VALUE;
        for (StructureLayer layer : existing) {
            if (layer.y > maxExistingY) maxExistingY = layer.y;
        }

        // Find the minimum Y in new layers to calculate offset
        int minNewY = Integer.MAX_VALUE;
        for (StructureLayer layer : newLayers) {
            if (layer.y < minNewY) minNewY = layer.y;
        }

        // Offset to place new layers on top of existing ones
        int yOffset = (maxExistingY == Integer.MIN_VALUE) ? 0 : (maxExistingY - minNewY + 1);

        // Add new layers with Y offset
        for (StructureLayer newLayer : newLayers) {
            StructureLayer offsetLayer = new StructureLayer(newLayer.y + yOffset, newLayer.width, newLayer.depth);

            for (int x = 0; x < newLayer.width; x++) {
                for (int z = 0; z < newLayer.depth; z++) {
                    IBlockState state = newLayer.getBlockState(x, z);
                    if (state != null) offsetLayer.setBlockState(x + xOffset, z + zOffset, state);
                }
            }

            existing.add(offsetLayer);
        }

        // Sort by Y
        existing.sort((a, b) -> Integer.compare(a.y, b.y));
        info.setLayers(existing);
    }

    // Procedural structures use hardcoded estimates since they're generated algorithmically

    private void populateDesertTemple() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "desert_temple"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/desert_pyramid", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);
    }

    private void populateJungleTemple() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "jungle_temple"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/jungle_temple", "gui.structurescanner.loot.chest"),
            // FIXME: the loot table is empty in the GUI, it shouldn't be. It was working before...
            createLootEntry("minecraft:chests/jungle_temple_dispenser", "gui.structurescanner.loot.dispenser")
        );
        info.setLootTables(loot);
    }

    private void populateWitchHut() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "witch_hut"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.LOG, 1, 16),  // Spruce log
                createBlockEntry(Blocks.PLANKS, 1, 30),  // Spruce planks
                createBlockEntry(Blocks.WOODEN_SLAB, 1, 20),  // Spruce slab
                createBlockEntry(Blocks.OAK_FENCE, 0, 4),
                createBlockEntry(Blocks.CAULDRON, 0, 1),
                createBlockEntry(Blocks.CRAFTING_TABLE, 0, 1),
                createBlockEntry(Blocks.FLOWER_POT, 0, 1)
            );
            info.setBlocks(blocks);
        }

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "witch"), 1)
        );
        info.setEntities(entities);
    }

    private void populateIgloo() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "igloo"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/igloo_chest", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "villager"), 1),
            new EntityEntry(new ResourceLocation("minecraft", "zombie_villager"), 1)
        );
        info.setEntities(entities);
    }

    private void populateOceanMonument() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "monument"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.PRISMARINE, 0, 5000),
                createBlockEntry(Blocks.PRISMARINE, 1, 1000),  // Bricks
                createBlockEntry(Blocks.PRISMARINE, 2, 500),   // Dark
                createBlockEntry(Blocks.SEA_LANTERN, 0, 50),
                createBlockEntry(Blocks.SPONGE, 1, 30),  // Wet sponge
                createBlockEntry(Blocks.GOLD_BLOCK, 0, 8)
            );
            info.setBlocks(blocks);
        }

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "guardian"), 20),
            new EntityEntry(new ResourceLocation("minecraft", "elder_guardian"), 3)
        );
        info.setEntities(entities);
    }

    private void populateDungeon() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "dungeon"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.COBBLESTONE, 0, 50),
                createBlockEntry(Blocks.MOSSY_COBBLESTONE, 0, 50),
                createBlockEntry(Blocks.MOB_SPAWNER, 0, 1),
                createBlockEntry(Blocks.CHEST, 0, 2)
            );
            info.setBlocks(blocks);
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/simple_dungeon", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "zombie"), 1),
            new EntityEntry(new ResourceLocation("minecraft", "skeleton"), 1),
            new EntityEntry(new ResourceLocation("minecraft", "spider"), 1)
        );
        info.setEntities(entities);
    }

    private void populateStronghold() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "stronghold"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/stronghold_corridor", "gui.structurescanner.loot.chest"),
            // FIXME: the loot table is empty in the GUI, it shouldn't be. It was working before...
            createLootEntry("minecraft:chests/stronghold_crossing", "gui.structurescanner.loot.chest"),
            createLootEntry("minecraft:chests/stronghold_library", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "silverfish"), 1)
        );
        info.setEntities(entities);
    }

    private void populateMineshaft() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "mineshaft"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.PLANKS, 0, 500),
                createBlockEntry(Blocks.OAK_FENCE, 0, 200),
                createBlockEntry(Blocks.RAIL, 0, 300),
                createBlockEntry(Blocks.TORCH, 0, 100),
                createBlockEntry(Blocks.WEB, 0, 50),
                createBlockEntry(Blocks.MOB_SPAWNER, 0, 1)
            );
            info.setBlocks(blocks);
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/abandoned_mineshaft", "gui.structurescanner.loot.minecart_chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "cave_spider"), 1)
        );
        info.setEntities(entities);
    }

    private void populateNetherFortress() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "fortress"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.NETHER_BRICK, 0, 5000),
                createBlockEntry(Blocks.NETHER_BRICK_FENCE, 0, 500),
                createBlockEntry(Blocks.NETHER_BRICK_STAIRS, 0, 300),
                createBlockEntry(Blocks.NETHER_WART, 0, 50),
                createBlockEntry(Blocks.SOUL_SAND, 0, 20),
                createBlockEntry(Blocks.MOB_SPAWNER, 0, 2)
            );
            info.setBlocks(blocks);
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/nether_bridge", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "blaze"), 1),
            new EntityEntry(new ResourceLocation("minecraft", "wither_skeleton"), 1)
        );
        info.setEntities(entities);
    }

    private void populateEndCity() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "endcity"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
                createBlockEntry(Blocks.PURPUR_BLOCK, 0, 2000),
                createBlockEntry(Blocks.PURPUR_PILLAR, 0, 500),
                createBlockEntry(Blocks.PURPUR_STAIRS, 0, 300),
                createBlockEntry(Blocks.PURPUR_SLAB, 0, 200),
                createBlockEntry(Blocks.END_BRICKS, 0, 500),
                createBlockEntry(Blocks.END_ROD, 0, 100),
                createBlockEntry(Blocks.STAINED_GLASS, 2, 50)  // Magenta stained glass (meta 2)
            );
            info.setBlocks(blocks);
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/end_city_treasure", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "shulker"), 10)
        );
        info.setEntities(entities);

        // End ship info
        StructureInfo shipInfo = structureInfos.get(new ResourceLocation("minecraft", "end_ship"));
        if (shipInfo != null) {
            if (shipInfo.getBlocks().isEmpty()) {
                List<BlockEntry> shipBlocks = filterNulls(
                    createBlockEntry(Blocks.PURPUR_BLOCK, 0, 300),
                    createBlockEntry(Blocks.PURPUR_STAIRS, 0, 50),
                    createBlockEntry(Blocks.OBSIDIAN, 0, 16),
                    createBlockEntry(Blocks.END_ROD, 0, 10)
                );
                shipInfo.setBlocks(shipBlocks);
            }

            List<LootEntry> shipLoot = Arrays.asList(
                createLootEntry("minecraft:chests/end_city_treasure", "gui.structurescanner.loot.chest")
            );
            shipInfo.setLootTables(shipLoot);

            List<EntityEntry> shipEntities = Arrays.asList(
                new EntityEntry(new ResourceLocation("minecraft", "shulker"), 3)
            );
            shipInfo.setEntities(shipEntities);
        }
    }

    private void populateWoodlandMansion() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "mansion"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/woodland_mansion", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "vindication_illager"), 10),
            new EntityEntry(new ResourceLocation("minecraft", "evocation_illager"), 3)
        );
        info.setEntities(entities);
    }

    private void populateVillage() {
        StructureInfo info = structureInfos.get(new ResourceLocation("minecraft", "village"));
        if (info == null) return;

        if (info.getBlocks().isEmpty()) {
            List<BlockEntry> blocks = filterNulls(
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
        }

        List<LootEntry> loot = Arrays.asList(
            createLootEntry("minecraft:chests/village_blacksmith", "gui.structurescanner.loot.chest")
        );
        info.setLootTables(loot);

        List<EntityEntry> entities = Arrays.asList(
            new EntityEntry(new ResourceLocation("minecraft", "villager"), 10),
            new EntityEntry(new ResourceLocation("minecraft", "villager_golem"), 1)
        );
        info.setEntities(entities);
    }

    private BlockEntry createBlockEntry(Block block, int meta, int count) {
        IBlockState state = block.getStateFromMeta(meta);
        ItemStack stack = ItemStack.EMPTY;

        // Strategy 1: Use Item.getItemFromBlock with damageDropped
        Item blockItem = Item.getItemFromBlock(block);
        if (blockItem != null && blockItem != Items.AIR) {
            int damage = block.damageDropped(state);
            stack = new ItemStack(blockItem, 1, damage);
        }

        // Strategy 2: Use getItemDropped if direct item form failed
        if (stack.isEmpty()) {
            Item droppedItem = block.getItemDropped(state, RANDOM, 0);
            if (droppedItem != null && droppedItem != Items.AIR) {
                int damage = block.damageDropped(state);
                stack = new ItemStack(droppedItem, 1, damage);
            }
        }

        // Strategy 3: Fallback to direct ItemStack creation
        if (stack.isEmpty()) {
            stack = new ItemStack(block, 1, meta);
        }

        if (stack.isEmpty()) {
            stack = new ItemStack(block);
            if (stack.isEmpty()) return null;
        }

        return new BlockEntry(state, stack, count);
    }

    @SafeVarargs
    private final <T> List<T> filterNulls(T... elements) {
        return Stream.of(elements).filter(e -> e != null).collect(Collectors.toList());
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

        // FIXME: world.findNearestStructure only works for already-generated structures.
        //        To find ungenerated structures, need to reimplement structure placement algorithms:
        //        - Village/Temple: MapGenScatteredFeature with biome checks and spacing
        //        - Monument: StructureOceanMonument with deep ocean biome requirement
        //        - Stronghold: MapGenStronghold with ring-based placement from world origin
        //        - Mansion: WoodlandMansion with roofed forest biome checks
        //        Each uses world seed with specific modifiers for deterministic placement.
        //        See AMIDST or similar tools for reference implementations.

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
