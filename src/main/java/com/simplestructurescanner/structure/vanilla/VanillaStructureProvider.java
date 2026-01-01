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
        addStructure("village", "gui.structurescanner.structures.minecraft.village", 0, 0, 0);
        addStructure("mineshaft", "gui.structurescanner.structures.minecraft.mineshaft", 0, 0, 0);
        addStructure("stronghold", "gui.structurescanner.structures.minecraft.stronghold", 0, 0, 0);
        addStructure("desert_temple", "gui.structurescanner.structures.minecraft.desert_temple", 21, 21, 21);
        addStructure("jungle_temple", "gui.structurescanner.structures.minecraft.jungle_temple", 12, 14, 15);
        addStructure("witch_hut", "gui.structurescanner.structures.minecraft.witch_hut", 7, 5, 9);
        addStructure("igloo", "gui.structurescanner.structures.minecraft.igloo", 7, 5, 8);
        addStructure("monument", "gui.structurescanner.structures.minecraft.ocean_monument", 58, 23, 58);
        addStructure("mansion", "gui.structurescanner.structures.minecraft.woodland_mansion", 0, 0, 0);
        addStructure("dungeon", "gui.structurescanner.structures.minecraft.dungeon", 9, 7, 9);
        // Nether
        addStructure("fortress", "gui.structurescanner.structures.minecraft.nether_fortress", 0, 0, 0);

        // End
        addStructure("endcity", "gui.structurescanner.structures.minecraft.end_city", 0, 0, 0);
        addStructure("end_ship", "gui.structurescanner.structures.minecraft.end_ship", 0, 0, 0);

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
     * Populates blocks and loot tables for vanilla structures. Uses NBT parsing when possible.
     */
    private void populateStructureContents() {
        parseNBTStructures();

        // Fill in remaining data (loot tables, entities) and fallback for structures without NBT data
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

        // FIXME: encode and use direct .nbt files (made manually)

        // Igloo structures
        parseAndApplyNBT("igloo", "igloo/igloo_bottom");
        parseAndApplyNBT("igloo", "igloo/igloo_middle", 2, 6);
        parseAndApplyNBT("igloo", "igloo/igloo_top", 0, 1);
    }

    /**
     * Parse an NBT structure file and apply its data to a structure with offsets.
     */
    private void parseAndApplyNBT(String structurePath, String nbtPath, int xOffset, int zOffset) {
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
                mergeLayers(info, parsed.layers, xOffset, zOffset);
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

    private void parseAndApplyNBT(String structurePath, String nbtPath) {
        parseAndApplyNBT(structurePath, nbtPath, 0, 0);
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
            StructureLayer offsetLayer = new StructureLayer(newLayer.y + yOffset, newLayer.width, newLayer.depth, xOffset, zOffset);

            for (int x = 0; x < newLayer.width; x++) {
                for (int z = 0; z < newLayer.depth; z++) {
                    IBlockState state = newLayer.getBlockState(x, z);
                    if (state != null) offsetLayer.setBlockState(x, z, state);
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
        if (!canBeSearched(structureId)) return null;

        String path = structureId.getPath();
        long seed = world.getSeed();

        List<BlockPos> candidates = findStructuresByType(world, path, pos, seed, skipCount + 5);
        if (candidates.isEmpty()) return null;

        // Sort by distance and get requested index
        candidates.sort((a, b) -> Double.compare(a.distanceSq(pos), b.distanceSq(pos)));

        if (skipCount >= candidates.size()) return null;

        return new StructureLocation(candidates.get(skipCount), skipCount, candidates.size());
    }

    /**
     * Find structures of a given type using seed-based algorithms.
     */
    private List<BlockPos> findStructuresByType(World world, String structureType, BlockPos pos, long seed, int maxResults) {
        switch (structureType) {
            case "village":
                // Village uses spacing=32, separation=8, salt=10387312
                return findVillages(world, pos, seed, maxResults);

            case "desert_temple":
            case "jungle_temple":
            case "witch_hut":
            case "igloo":
                // All temples share spacing=32, separation=8, salt=14357617
                return findTemples(world, pos, seed, structureType, maxResults);

            case "monument":
                return findOceanMonuments(world, pos, seed, maxResults);

            case "mansion":
                return findWoodlandMansions(world, pos, seed, maxResults);

            case "stronghold":
                return findStrongholds(world, pos, seed, maxResults);

            case "fortress":
                return findNetherFortresses(world, pos, seed, maxResults);

            case "endcity":
            case "end_ship":
                return findEndCities(world, pos, seed, maxResults);

            case "mineshaft":
                return findMineshafts(world, pos, seed, maxResults);

            default:
                return Collections.emptyList();
        }
    }

    // ========== Village Algorithm ==========

    /**
     * Find villages using MC 1.12 algorithm.
     * Villages have their own salt (10387312) separate from temples.
     */
    private List<BlockPos> findVillages(World world, BlockPos pos, long seed, int maxResults) {
        return findScatteredFeature(world, pos, seed, 32, 8, 10387312, maxResults, null);
    }

    /**
     * MC 1.12 scattered feature algorithm (used by village, temple types).
     * This matches MapGenScatteredFeature.getStructurePosition exactly.
     *
     * @param validBiomes If null, skips biome checking (finds all grid positions)
     */
    private List<BlockPos> findScatteredFeature(World world, BlockPos pos, long seed,
            int maxDist, int minDist, int salt, int maxResults, @Nullable Set<Biome> validBiomes) {

        List<BlockPos> results = new ArrayList<>();

        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;

        // Search outward in chunks, checking each potential structure position
        int searchRadiusChunks = maxDist * 10;  // Search up to 10 regions away

        for (int dist = 0; dist <= searchRadiusChunks && results.size() < maxResults; dist++) {
            for (int cx = -dist; cx <= dist; cx++) {
                for (int cz = -dist; cz <= dist; cz++) {
                    // Only check the perimeter at this distance (expanding square)
                    if (dist > 0 && Math.abs(cx) != dist && Math.abs(cz) != dist) continue;

                    int checkChunkX = playerChunkX + cx;
                    int checkChunkZ = playerChunkZ + cz;

                    // Get the structure position for the region containing this chunk
                    BlockPos structurePos = getScatteredFeaturePos(seed, maxDist, minDist, salt, checkChunkX, checkChunkZ);

                    // Check if this chunk would actually generate the structure
                    int structChunkX = structurePos.getX() >> 4;
                    int structChunkZ = structurePos.getZ() >> 4;

                    // Only count if this is the actual structure chunk
                    if (structChunkX != checkChunkX || structChunkZ != checkChunkZ) continue;

                    // Check biome if required
                    if (validBiomes != null) {
                        Biome biome = world.getBiome(structurePos);
                        if (!validBiomes.contains(biome)) continue;
                    }

                    // Avoid duplicates
                    boolean duplicate = false;
                    for (BlockPos existing : results) {
                        if (existing.getX() == structurePos.getX() && existing.getZ() == structurePos.getZ()) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    /**
     * Get the structure position for a given chunk using MC 1.12's algorithm.
     * This calculates which chunk in the region would contain the structure.
     */
    private BlockPos getScatteredFeaturePos(long seed, int maxDist, int minDist, int salt, int chunkX, int chunkZ) {
        // MC 1.12's region calculation (handles negative coords correctly)
        int regionX = chunkX;
        int regionZ = chunkZ;

        if (chunkX < 0) {
            regionX = chunkX - maxDist + 1;
        }
        if (chunkZ < 0) {
            regionZ = chunkZ - maxDist + 1;
        }

        regionX = regionX / maxDist;
        regionZ = regionZ / maxDist;

        // Seed the random with the region coordinates
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        // Calculate offset within region
        int offsetX = random.nextInt(maxDist - minDist);
        int offsetZ = random.nextInt(maxDist - minDist);

        // Structure chunk position
        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        // Return block position at chunk center
        return new BlockPos(structChunkX * 16 + 8, 64, structChunkZ * 16 + 8);
    }

    // ========== Temple Algorithm (Desert Temple, Jungle Temple, Witch Hut, Igloo) ==========

    /**
     * Find temples using MC 1.12 algorithm.
     * All temple types share the same grid (salt=14357617) but filter by biome.
     */
    private List<BlockPos> findTemples(World world, BlockPos pos, long seed, String templeType, int maxResults) {
        Set<Biome> validBiomes = new HashSet<>();

        switch (templeType) {
            case "desert_temple":
                validBiomes.add(Biomes.DESERT);
                validBiomes.add(Biomes.DESERT_HILLS);
                validBiomes.add(Biomes.MUTATED_DESERT);
                break;
            case "jungle_temple":
                validBiomes.add(Biomes.JUNGLE);
                validBiomes.add(Biomes.JUNGLE_HILLS);
                validBiomes.add(Biomes.MUTATED_JUNGLE);
                validBiomes.add(Biomes.JUNGLE_EDGE);
                validBiomes.add(Biomes.MUTATED_JUNGLE_EDGE);
                break;
            case "witch_hut":
                validBiomes.add(Biomes.SWAMPLAND);
                validBiomes.add(Biomes.MUTATED_SWAMPLAND);
                break;
            case "igloo":
                validBiomes.add(Biomes.ICE_PLAINS);
                validBiomes.add(Biomes.COLD_TAIGA);
                validBiomes.add(Biomes.ICE_MOUNTAINS);
                validBiomes.add(Biomes.COLD_TAIGA_HILLS);
                break;
        }

        // Use the same scattered feature algorithm as villages
        return findScatteredFeature(world, pos, seed, 32, 8, 14357617, maxResults, validBiomes);
    }

    // ========== Ocean Monument Algorithm ==========

    /**
     * Find ocean monuments using MC 1.12 algorithm.
     * Monuments use spacing=32, separation=5, salt=10387313.
     * Uses the same scattered feature placement with averaged offset.
     */
    private List<BlockPos> findOceanMonuments(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();

        int maxDist = 32;
        int minDist = 5;
        int salt = 10387313;

        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;

        int searchRadiusChunks = maxDist * 15;

        for (int dist = 0; dist <= searchRadiusChunks && results.size() < maxResults; dist++) {
            for (int cx = -dist; cx <= dist; cx++) {
                for (int cz = -dist; cz <= dist; cz++) {
                    if (dist > 0 && Math.abs(cx) != dist && Math.abs(cz) != dist) continue;

                    int checkChunkX = playerChunkX + cx;
                    int checkChunkZ = playerChunkZ + cz;

                    BlockPos structurePos = getMonumentPos(seed, maxDist, minDist, salt, checkChunkX, checkChunkZ);
                    if (structurePos == null) continue;

                    int structChunkX = structurePos.getX() >> 4;
                    int structChunkZ = structurePos.getZ() >> 4;

                    if (structChunkX != checkChunkX || structChunkZ != checkChunkZ) continue;

                    // Check biome
                    Biome biome = world.getBiome(structurePos);
                    if (biome != Biomes.DEEP_OCEAN && biome != Biomes.OCEAN) continue;

                    // Avoid duplicates
                    boolean duplicate = false;
                    for (BlockPos existing : results) {
                        if (existing.getX() == structurePos.getX() && existing.getZ() == structurePos.getZ()) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    private BlockPos getMonumentPos(long seed, int maxDist, int minDist, int salt, int chunkX, int chunkZ) {
        int regionX = chunkX;
        int regionZ = chunkZ;

        if (chunkX < 0) regionX = chunkX - maxDist + 1;
        if (chunkZ < 0) regionZ = chunkZ - maxDist + 1;

        regionX = regionX / maxDist;
        regionZ = regionZ / maxDist;

        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        // Monument uses averaged offset (triangular distribution)
        int range = maxDist - minDist;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        return new BlockPos(structChunkX * 16 + 8, 63, structChunkZ * 16 + 8);
    }

    // ========== Woodland Mansion Algorithm ==========

    /**
     * Find woodland mansions using MC 1.12 algorithm.
     * Mansions use spacing=80, separation=20, salt=10387319.
     */
    private List<BlockPos> findWoodlandMansions(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();

        int maxDist = 80;
        int minDist = 20;
        int salt = 10387319;

        int playerChunkX = pos.getX() >> 4;
        int playerChunkZ = pos.getZ() >> 4;

        int searchRadiusChunks = maxDist * 8;

        for (int dist = 0; dist <= searchRadiusChunks && results.size() < maxResults; dist++) {
            for (int cx = -dist; cx <= dist; cx++) {
                for (int cz = -dist; cz <= dist; cz++) {
                    if (dist > 0 && Math.abs(cx) != dist && Math.abs(cz) != dist) continue;

                    int checkChunkX = playerChunkX + cx;
                    int checkChunkZ = playerChunkZ + cz;

                    BlockPos structurePos = getMansionPos(seed, maxDist, minDist, salt, checkChunkX, checkChunkZ);
                    if (structurePos == null) continue;

                    int structChunkX = structurePos.getX() >> 4;
                    int structChunkZ = structurePos.getZ() >> 4;

                    if (structChunkX != checkChunkX || structChunkZ != checkChunkZ) continue;

                    // Check biome
                    Biome biome = world.getBiome(structurePos);
                    if (biome != Biomes.ROOFED_FOREST && biome != Biomes.MUTATED_ROOFED_FOREST) continue;

                    // Avoid duplicates
                    boolean duplicate = false;
                    for (BlockPos existing : results) {
                        if (existing.getX() == structurePos.getX() && existing.getZ() == structurePos.getZ()) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (duplicate) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    private BlockPos getMansionPos(long seed, int maxDist, int minDist, int salt, int chunkX, int chunkZ) {
        int regionX = chunkX;
        int regionZ = chunkZ;

        if (chunkX < 0) regionX = chunkX - maxDist + 1;
        if (chunkZ < 0) regionZ = chunkZ - maxDist + 1;

        regionX = regionX / maxDist;
        regionZ = regionZ / maxDist;

        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        // Mansion uses averaged offset
        int range = maxDist - minDist;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        return new BlockPos(structChunkX * 16 + 8, 64, structChunkZ * 16 + 8);
    }

    // ========== Stronghold Algorithm ==========

    /**
     * Find strongholds using Minecraft 1.12's ring-based algorithm.
     * In 1.12, there are 128 strongholds total, placed in concentric rings.
     * Ring 1: 3 strongholds at distance 1408-2688 blocks
     * Ring 2: 6 strongholds at distance 4480-5760 blocks
     * etc.
     */
    private List<BlockPos> findStrongholds(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> strongholds = calculateStrongholds(seed);

        // Sort by distance from player
        strongholds.sort((a, b) -> Double.compare(a.distanceSq(pos), b.distanceSq(pos)));

        return strongholds.subList(0, Math.min(maxResults, strongholds.size()));
    }

    /**
     * Calculate stronghold positions using Minecraft 1.12's algorithm.
     * MC 1.12 places 128 strongholds in 8 concentric rings.
     * Distance formula: (4 * ringNumber + 1) * 32 chunks, with 25% variance.
     */
    private List<BlockPos> calculateStrongholds(long seed) {
        List<BlockPos> strongholds = new ArrayList<>();
        Random random = new Random();
        random.setSeed(seed);

        // Starting angle (random)
        double angle = random.nextDouble() * Math.PI * 2.0;

        // Stronghold counts per ring (MC 1.12) - ring index starts at 1 in MC code
        int[] countPerRing = {3, 6, 10, 15, 21, 28, 36, 9};  // Total = 128
        int ringNumber = 1;  // MC starts at 1, not 0
        int placedInRing = 0;

        for (int i = 0; i < 128; i++) {
            // Distance formula from MC: (4 * ringNumber + 1) * 32 chunks
            // With random variance of up to 25%
            double baseDistance = (double) (4 * ringNumber + 1) * 32.0;
            double variance = baseDistance * 0.25;
            double distance = baseDistance + random.nextDouble() * variance;

            int chunkX = (int) Math.round(Math.cos(angle) * distance);
            int chunkZ = (int) Math.round(Math.sin(angle) * distance);

            // Convert to block position
            strongholds.add(new BlockPos(chunkX * 16 + 8, 35, chunkZ * 16 + 8));

            // Advance angle by equal division of circle
            angle += (Math.PI * 2.0 / countPerRing[ringNumber - 1]);

            placedInRing++;
            if (placedInRing >= countPerRing[ringNumber - 1]) {
                // Move to next ring
                ringNumber++;
                placedInRing = 0;
                // New random starting angle for next ring
                angle = random.nextDouble() * Math.PI * 2.0;
            }
        }

        return strongholds;
    }

    // ========== Nether Fortress Algorithm ==========

    /**
     * Find nether fortresses using the fortress-specific algorithm.
     * Fortresses generate one per 16x16 chunk region.
     */
    private List<BlockPos> findNetherFortresses(World world, BlockPos pos, long seed, int maxResults) {
        Set<Long> checkedRegions = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        int regionSize = 16;
        int playerRegionX = Math.floorDiv(pos.getX() >> 4, regionSize);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, regionSize);

        int searchRadius = 10; // regions

        for (int radius = 0; radius <= searchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos fortressPos = getFortressPos(seed, regionSize, regionX, regionZ);
                    if (fortressPos != null) results.add(fortressPos);
                }
            }
        }

        return results;
    }

    /**
     * Get the fortress position in a region.
     */
    @Nullable
    private BlockPos getFortressPos(long seed, int regionSize, int regionX, int regionZ) {
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + 30084232L);

        int offsetX = random.nextInt(regionSize - 4);
        int offsetZ = random.nextInt(regionSize - 4);

        int chunkX = regionX * regionSize + offsetX;
        int chunkZ = regionZ * regionSize + offsetZ;

        return new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
    }

    // ========== End City Algorithm ==========

    /**
     * Find end cities using the end city algorithm.
     * End cities generate on the outer End islands (beyond 1000 blocks from origin).
     */
    private List<BlockPos> findEndCities(World world, BlockPos pos, long seed, int maxResults) {
        Set<Long> checkedRegions = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();

        int spacing = 20;
        int separation = 11;
        int salt = 10387313;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, spacing);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, spacing);

        int searchRadius = 10; // regions

        for (int radius = 0; radius <= searchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos structurePos = getEndCityPos(seed, spacing, separation, salt, regionX, regionZ);
                    if (structurePos == null) continue;

                    // End cities only generate beyond 1000 blocks from origin
                    int blockX = structurePos.getX();
                    int blockZ = structurePos.getZ();
                    if ((long) blockX * blockX + (long) blockZ * blockZ < 1000L * 1000L) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    @Nullable
    private BlockPos getEndCityPos(long seed, int spacing, int separation, int salt, int regionX, int regionZ) {
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + salt);

        int offsetX = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
        int offsetZ = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;

        int chunkX = regionX * spacing + offsetX;
        int chunkZ = regionZ * spacing + offsetZ;

        return new BlockPos(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
    }

    // ========== Mineshaft Algorithm ==========

    /**
     * Find mineshafts using the mineshaft algorithm. Mineshafts are determined per-chunk based on seed.
     */
    private List<BlockPos> findMineshafts(World world, BlockPos pos, long seed, int maxResults) {
        Set<Long> checkedChunks = new HashSet<>();
        List<BlockPos> results = new ArrayList<>();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        int searchRadius = 30; // chunks

        for (int radius = 0; radius <= searchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;

                    long chunkKey = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                    if (checkedChunks.contains(chunkKey)) continue;
                    checkedChunks.add(chunkKey);

                    if (isMineshaftChunk(seed, cx, cz)) results.add(new BlockPos(cx * 16 + 8, 40, cz * 16 + 8));
                }
            }
        }

        return results;
    }

    /**
     * Check if a chunk contains a mineshaft based on seed.
     * Uses Minecraft 1.12's algorithm:
     * 1. random.nextDouble() < 0.004
     * 2. random.nextInt(80) < max(abs(chunkX), abs(chunkZ))
     * The second condition makes mineshafts more common further from spawn.
     */
    private boolean isMineshaftChunk(long seed, int chunkX, int chunkZ) {
        Random random = new Random();
        random.setSeed(seed);

        long a = random.nextLong();
        long b = random.nextLong();
        random.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ seed);

        // Both conditions must be met
        if (random.nextDouble() >= 0.004) return false;

        return random.nextInt(80) < Math.max(Math.abs(chunkX), Math.abs(chunkZ));
    }

}
