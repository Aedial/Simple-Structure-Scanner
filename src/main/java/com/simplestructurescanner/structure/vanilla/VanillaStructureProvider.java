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
import java.util.function.Predicate;
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
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.util.text.translation.I18n;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureProvider;
import com.simplestructurescanner.structure.TerrainHeightCalculator;


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

    // Cache: seed -> (structureType -> list of positions)
    // Positions are sorted by distance to search origin when cached
    private static final Map<Long, Map<String, List<BlockPos>>> positionCache = new HashMap<>();
    private static final int MAX_CACHED_POSITIONS = 200;

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
        // Dimension sets using DimensionInfo
        Set<DimensionInfo> overworld = Collections.singleton(DimensionInfo.OVERWORLD);
        Set<DimensionInfo> nether = Collections.singleton(DimensionInfo.NETHER);
        Set<DimensionInfo> end = Collections.singleton(DimensionInfo.END);

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

    private void setMetadata(String path, Set<Biome> biomes, Set<DimensionInfo> dimensions, String rarity) {
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

        // TODO: encode and use direct .nbt files (made manually)

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
            new EntityEntry(new ResourceLocation("minecraft", "zombie"), 1, true),
            new EntityEntry(new ResourceLocation("minecraft", "skeleton"), 1, true),
            new EntityEntry(new ResourceLocation("minecraft", "spider"), 1, true)
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
            new EntityEntry(new ResourceLocation("minecraft", "silverfish"), 1, true)
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
            new EntityEntry(new ResourceLocation("minecraft", "cave_spider"), 1, true)
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
            new EntityEntry(new ResourceLocation("minecraft", "blaze"), 1, true),
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
        if (stack.isEmpty()) stack = new ItemStack(block, 1, meta);

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
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        if (world == null || !canBeSearched(structureId)) return null;

        String path = structureId.getPath();
        Long seed = getWorldSeed(world);

        if (seed == null) {
            SimpleStructureScanner.LOGGER.warn("Could not get world seed for structure search");
            return null;
        }

        // Get cached or generate positions
        List<BlockPos> candidates = getCachedPositions(world, path, pos, seed);
        if (candidates.isEmpty()) return null;

        // Sort by distance (Y-agnostic - only use X and Z)
        sortByDistance(candidates, pos);

        // Apply filter and skip to find the target
        int validIndex = 0;
        int totalValid = 0;
        BlockPos targetPos = null;

        for (BlockPos candidate : candidates) {
            if (locationFilter != null && !locationFilter.test(candidate)) continue;

            if (validIndex == skipCount && targetPos == null) targetPos = candidate;

            validIndex++;
            totalValid++;
        }

        if (targetPos == null) return null;

        // Calculate terrain height for surface structures with Y=0
        if (targetPos.getY() == 0 && isSurfaceStructure(path)) {
            TerrainHeightCalculator heightCalc = new TerrainHeightCalculator(seed, world.getBiomeProvider());
            int terrainY = heightCalc.getTerrainHeight(targetPos.getX(), targetPos.getZ());
            targetPos = new BlockPos(targetPos.getX(), terrainY, targetPos.getZ());
        }

        boolean yAgnostic = targetPos.getY() == 0;

        return new StructureLocation(targetPos, skipCount, totalValid, yAgnostic);
    }

    /**
     * Get cached positions or generate and cache them.
     */
    private List<BlockPos> getCachedPositions(World world, String structureType, BlockPos searchPos, long seed) {
        Map<String, List<BlockPos>> seedCache = positionCache.computeIfAbsent(seed, k -> new HashMap<>());

        if (!seedCache.containsKey(structureType)) {
            List<BlockPos> positions = findStructuresByType(world, structureType, searchPos, seed, MAX_CACHED_POSITIONS);
            seedCache.put(structureType, positions);
        }

        return new ArrayList<>(seedCache.get(structureType));
    }

    @Override
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        if (world == null) return Collections.emptyList();
        if (!canBeSearched(structureId)) return Collections.emptyList();

        String path = structureId.getPath();
        Long seed = getWorldSeed(world);

        if (seed == null) {
            SimpleStructureScanner.LOGGER.warn("Could not get world seed for structure search");
            return Collections.emptyList();
        }

        List<BlockPos> candidates = findStructuresByType(world, path, pos, seed, maxResults);

        // Calculate terrain heights for surface structures
        if (isSurfaceStructure(path) && !candidates.isEmpty()) {
            TerrainHeightCalculator heightCalc = new TerrainHeightCalculator(seed, world.getBiomeProvider());
            List<BlockPos> withHeights = new ArrayList<>(candidates.size());

            for (BlockPos candidate : candidates) {
                if (candidate.getY() == 0) {
                    int terrainY = heightCalc.getTerrainHeight(candidate.getX(), candidate.getZ());
                    withHeights.add(new BlockPos(candidate.getX(), terrainY, candidate.getZ()));
                } else {
                    withHeights.add(candidate);
                }
            }

            return withHeights;
        }

        return candidates;
    }

    /**
     * Sorts positions by horizontal distance from the given position.
     */
    private void sortByDistance(List<BlockPos> positions, BlockPos from) {
        final int px = from.getX();
        final int pz = from.getZ();

        positions.sort((a, b) -> {
            // Cast to long to avoid integer overflow for large distances
            long dxA = a.getX() - px;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dzA * dzA;
            long distB = dxB * dxB + dzB * dzB;
            return Long.compare(distA, distB);
        });
    }

    /**
     * Check if a structure is a surface structure (vs underground/underwater).
     */
    private boolean isSurfaceStructure(String structureType) {
        switch (structureType) {
            case "village":
            case "desert_temple":
            case "jungle_temple":
            case "witch_hut":
            case "igloo":
            case "mansion":
            case "endcity":
            case "end_ship":
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the world seed. This should only be called on the server side.
     * Returns 0 if the seed cannot be retrieved (should not happen on server).
     */
    private Long getWorldSeed(World world) {
        // On server, WorldInfo should always have the correct seed
        if (world.getWorldInfo() != null) return world.getWorldInfo().getSeed();

        return null;
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
        Set<Biome> validBiomes = new HashSet<>();
        validBiomes.add(Biomes.PLAINS);
        validBiomes.add(Biomes.DESERT);
        validBiomes.add(Biomes.SAVANNA);
        validBiomes.add(Biomes.TAIGA);
        validBiomes.add(Biomes.ICE_PLAINS);
        validBiomes.add(Biomes.MUTATED_PLAINS);
        validBiomes.add(Biomes.SAVANNA_PLATEAU);

        return findScatteredFeature(world, pos, seed, 32, 8, 10387312, maxResults, validBiomes);
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
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        // Player's region coordinates
        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        // Search outward in regions (not chunks) - much more efficient
        int searchRadiusRegions = 20;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    // Only check the perimeter at this distance (expanding square)
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    // Avoid duplicate region checks
                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    // Get the structure position for this region directly
                    BlockPos structurePos = getScatteredFeaturePosForRegion(seed, maxDist, minDist, salt, regionX, regionZ);

                    // Check biome using BiomeProvider (fast, doesn't load chunks)
                    if (validBiomes != null) {
                        Biome biome = biomeProvider.getBiome(structurePos);
                        if (!validBiomes.contains(biome)) continue;
                    }

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    /**
     * Get the structure position for a specific region.
     */
    private BlockPos getScatteredFeaturePosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ) {
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        int offsetX = random.nextInt(maxDist - minDist);
        int offsetZ = random.nextInt(maxDist - minDist);

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        return new BlockPos(structChunkX * 16 + 8, 0, structChunkZ * 16 + 8);
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

        return findScatteredFeature(world, pos, seed, 32, 8, 14357617, maxResults, validBiomes);
    }

    // ========== Ocean Monument Algorithm ==========

    /**
     * Find ocean monuments using MC 1.12 algorithm.
     * Monuments use spacing=32, separation=5, salt=10387313.
     * Iterates over regions for efficiency.
     */
    private List<BlockPos> findOceanMonuments(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        int maxDist = 32;
        int minDist = 5;
        int salt = 10387313;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        int searchRadiusRegions = 20;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos structurePos = getMonumentPosForRegion(seed, maxDist, minDist, salt, regionX, regionZ);

                    // Check biome using BiomeProvider (fast, doesn't load chunks)
                    Biome biome = biomeProvider.getBiome(structurePos);
                    if (biome != Biomes.DEEP_OCEAN) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    private BlockPos getMonumentPosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ) {
        // FIXME: less than 50% chance to find a monument, something is wrong here
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        // Monument uses averaged offset (triangular distribution)
        // MC formula: regionX * spacing + (rand(range) + rand(range)) / 2
        int range = maxDist - minDist;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        // Ocean monuments sit on the sea floor, typically Y=39 (center), but surface is ~Y=63
        return new BlockPos(structChunkX * 16 + 8, 63, structChunkZ * 16 + 8);
    }

    // ========== Woodland Mansion Algorithm ==========

    /**
     * Find woodland mansions using MC 1.12 algorithm.
     * Mansions use spacing=80, separation=20, salt=10387319.
     * Mansions are VERY rare - roofed forest biomes are uncommon.
     */
    private List<BlockPos> findWoodlandMansions(World world, BlockPos pos, long seed, int maxResults) {
        List<BlockPos> results = new ArrayList<>();
        Set<Long> checkedRegions = new HashSet<>();
        BiomeProvider biomeProvider = world.getBiomeProvider();

        int maxDist = 80;
        int minDist = 20;
        int salt = 10387319;

        int playerRegionX = Math.floorDiv(pos.getX() >> 4, maxDist);
        int playerRegionZ = Math.floorDiv(pos.getZ() >> 4, maxDist);

        // Mansions are very rare, search further
        int searchRadiusRegions = 30;

        for (int dist = 0; dist <= searchRadiusRegions && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos structurePos = getMansionPosForRegion(seed, maxDist, minDist, salt, regionX, regionZ);

                    // Check biome using BiomeProvider
                    Biome biome = biomeProvider.getBiome(structurePos);
                    if (biome != Biomes.ROOFED_FOREST && biome != Biomes.MUTATED_ROOFED_FOREST) continue;

                    results.add(structurePos);
                }
            }
        }

        return results;
    }

    private BlockPos getMansionPosForRegion(long seed, int maxDist, int minDist, int salt, int regionX, int regionZ) {
        // FIXME: 1/10 chance to find a mansion, something is wrong here
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + (long) salt);

        // MC formula: regionX * spacing + (rand(range) + rand(range)) / 2
        int range = maxDist - minDist;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;

        int structChunkX = regionX * maxDist + offsetX;
        int structChunkZ = regionZ * maxDist + offsetZ;

        return new BlockPos(structChunkX * 16 + 8, 0, structChunkZ * 16 + 8);
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
     * 
     * From MapGenStronghold source:
     * distance = (4 * 32 + ringNumber * 32 * 6) + (random - 0.5) * 32 * 2.5
     * This is in CHUNKS, so:
     * - Ring 0: 128 chunks ± 40 chunks = 88-168 chunks = 1408-2688 blocks
     * - Ring 1: 320 chunks ± 40 chunks = 280-360 chunks = 4480-5760 blocks
     */
    private List<BlockPos> calculateStrongholds(long seed) {
        // FIXME: broken algo - strongholds are not where they should be
        List<BlockPos> strongholds = new ArrayList<>();
        Random random = new Random();
        random.setSeed(seed);

        // Starting angle (random)
        double angle = random.nextDouble() * Math.PI * 2.0;

        // Stronghold counts per ring (MC 1.12)
        int[] countPerRing = {3, 6, 10, 15, 21, 28, 36, 9};  // Total = 128
        int ringNumber = 0;
        int placedInRing = 0;

        for (int i = 0; i < 128; i++) {
            // MC 1.12 formula from MapGenStronghold (in CHUNKS):
            // distance = (4 * 32 + ringNumber * 32 * 6) + (random - 0.5) * 32 * 2.5
            // Simplifies to: (128 + ringNumber * 192) + (random - 0.5) * 80
            double baseDistance = 4.0 * 32.0 + (double) ringNumber * 32.0 * 6.0;
            double spread = (random.nextDouble() - 0.5) * 32.0 * 2.5;
            double distanceInChunks = baseDistance + spread;

            int chunkX = (int) Math.round(Math.cos(angle) * distanceInChunks);
            int chunkZ = (int) Math.round(Math.sin(angle) * distanceInChunks);

            // Convert to block coordinates (center of chunk)
            int blockX = chunkX * 16 + 8;
            int blockZ = chunkZ * 16 + 8;

            // Strongholds generate between Y=0 and Y=50, typically around Y=35
            // There is no deterministic way to get exact Y without loading chunks, so use Y=0
            strongholds.add(new BlockPos(blockX, 0, blockZ));

            // Advance angle by equal division of circle
            angle += (Math.PI * 2.0 / countPerRing[ringNumber]);

            placedInRing++;
            if (placedInRing >= countPerRing[ringNumber]) {
                // Move to next ring
                ringNumber++;
                placedInRing = 0;
                // Add random angle offset for next ring
                angle += random.nextDouble() * Math.PI * 2.0;
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
        // FIXME: broken algo - fortresses are not where they should be
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + 30084232L);

        int offsetX = random.nextInt(regionSize - 4);
        int offsetZ = random.nextInt(regionSize - 4);

        int chunkX = regionX * regionSize + offsetX;
        int chunkZ = regionZ * regionSize + offsetZ;

        // Nether fortresses typically generate around Y=64 (middle of nether)
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

        int searchRadius = 15;

        for (int dist = 0; dist <= searchRadius && results.size() < maxResults; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dist > 0 && Math.abs(dx) != dist && Math.abs(dz) != dist) continue;

                    int regionX = playerRegionX + dx;
                    int regionZ = playerRegionZ + dz;

                    long regionKey = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                    if (checkedRegions.contains(regionKey)) continue;
                    checkedRegions.add(regionKey);

                    BlockPos structurePos = getEndCityPosForRegion(seed, spacing, separation, salt, regionX, regionZ);

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

    private BlockPos getEndCityPosForRegion(long seed, int spacing, int separation, int salt, int regionX, int regionZ) {
        Random random = new Random();
        random.setSeed((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + salt);

        int offsetX = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;
        int offsetZ = (random.nextInt(spacing - separation) + random.nextInt(spacing - separation)) / 2;

        int chunkX = regionX * spacing + offsetX;
        int chunkZ = regionZ * spacing + offsetZ;

        return new BlockPos(chunkX * 16 + 8, 0, chunkZ * 16 + 8);
    }

    // ========== Mineshaft Algorithm ==========

    /**
     * Find mineshafts using the mineshaft algorithm. Mineshafts are determined per-chunk based on seed.
     * Mineshafts are common (0.4% per chunk) but only far from spawn due to distance check.
     */
    private List<BlockPos> findMineshafts(World world, BlockPos pos, long seed, int maxResults) {
        // FIXME: broken algo - mineshafts are not where they should be
        List<BlockPos> results = new ArrayList<>();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // Search in a spiral pattern outward
        // Mineshafts require distance from origin, so may need to search far
        int maxSearchRadius = 150; // chunks

        for (int radius = 0; radius <= maxSearchRadius && results.size() < maxResults; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the perimeter at this distance
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;

                    if (isMineshaftChunk(seed, cx, cz)) {
                        // Mineshafts generate between Y=10 and Y=60, typically starting around Y=35
                        // There is no deterministic way to get exact Y without loading chunks, so use Y=0
                        results.add(new BlockPos(cx * 16 + 8, 0, cz * 16 + 8));
                    }
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
