package com.simplestructurescanner.structure;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Random;

import javax.annotation.Nullable;

import com.google.common.base.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;

/**
 * Parses vanilla structure NBT files to extract block and entity data.
 * Structures are stored in assets/minecraft/structures/*.nbt
 */
public class StructureNBTParser {

    /**
     * Result of parsing a structure NBT file.
     */
    public static class ParsedStructure {
        public final int sizeX, sizeY, sizeZ;
        public final List<BlockEntry> blocks;
        public final List<StructureLayer> layers;
        public final List<EntityEntry> entities;

        public ParsedStructure(int sizeX, int sizeY, int sizeZ,
                               List<BlockEntry> blocks,
                               List<StructureLayer> layers,
                               List<EntityEntry> entities) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = blocks;
            this.layers = layers;
            this.entities = entities;
        }
    }

    /**
     * Parse a vanilla structure NBT file.
     * @param structurePath Path relative to assets/minecraft/structures/ without .nbt extension
     * @return Parsed structure data or null if parsing fails
     */
    @Nullable
    public static ParsedStructure parseStructure(String structurePath) {
        String resourcePath = "/assets/minecraft/structures/" + structurePath + ".nbt";

        try (InputStream stream = StructureNBTParser.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                SimpleStructureScanner.LOGGER.debug("Structure file not found: {}", resourcePath);
                return null;
            }

            NBTTagCompound nbt = CompressedStreamTools.readCompressed(stream);
            return parseNBT(nbt);
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse structure {}: {}", structurePath, e.getMessage());
            return null;
        }
    }

    /**
     * Parse structure data from an NBT compound.
     */
    @Nullable
    private static ParsedStructure parseNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("size") || !nbt.hasKey("palette") || !nbt.hasKey("blocks")) return null;

        // Read size
        NBTTagList sizeTag = nbt.getTagList("size", Constants.NBT.TAG_INT);
        int sizeX = sizeTag.getIntAt(0);
        int sizeY = sizeTag.getIntAt(1);
        int sizeZ = sizeTag.getIntAt(2);

        // Read palette (block state definitions)
        NBTTagList paletteTag = nbt.getTagList("palette", Constants.NBT.TAG_COMPOUND);
        IBlockState[] palette = new IBlockState[paletteTag.tagCount()];

        for (int i = 0; i < paletteTag.tagCount(); i++) {
            NBTTagCompound blockTag = paletteTag.getCompoundTagAt(i);
            palette[i] = parseBlockState(blockTag);
        }

        // Read blocks and count occurrences
        NBTTagList blocksTag = nbt.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        Map<IBlockState, Integer> blockCounts = new HashMap<>();

        // Create layer data structure
        Map<Integer, IBlockState[][]> layerBlocks = new HashMap<>();
        for (int y = 0; y < sizeY; y++) layerBlocks.put(y, new IBlockState[sizeX][sizeZ]);

        for (int i = 0; i < blocksTag.tagCount(); i++) {
            NBTTagCompound blockEntry = blocksTag.getCompoundTagAt(i);
            int paletteIndex = blockEntry.getInteger("state");

            if (paletteIndex >= 0 && paletteIndex < palette.length) {
                IBlockState state = palette[paletteIndex];
                if (state != null && state.getBlock() != Blocks.AIR && state.getBlock() != Blocks.STRUCTURE_VOID) {
                    blockCounts.merge(state, 1, Integer::sum);
                }

                // Store in layer data
                NBTTagList posTag = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
                int x = posTag.getIntAt(0);
                int y = posTag.getIntAt(1);
                int z = posTag.getIntAt(2);

                if (y >= 0 && y < sizeY && x >= 0 && x < sizeX && z >= 0 && z < sizeZ) layerBlocks.get(y)[x][z] = state;
            }
        }

        // Convert block counts to BlockEntry list
        List<BlockEntry> blocks = new ArrayList<>();
        for (Map.Entry<IBlockState, Integer> entry : blockCounts.entrySet()) {
            IBlockState state = entry.getKey();
            int count = entry.getValue();
            ItemStack displayStack = createDisplayStack(state);
            blocks.add(new BlockEntry(state, displayStack, count));
        }

        // Sort by count descending
        blocks.sort((a, b) -> Integer.compare(b.count, a.count));

        // Convert layer blocks to StructureLayer list
        List<StructureLayer> layers = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            StructureLayer layer = new StructureLayer(y, sizeX, sizeZ);
            IBlockState[][] yLayer = layerBlocks.get(y);

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (yLayer[x][z] != null) layer.setBlockState(x, z, yLayer[x][z]);
                }
            }

            layers.add(layer);
        }

        // Parse entities
        List<EntityEntry> entities = new ArrayList<>();
        if (nbt.hasKey("entities")) {
            NBTTagList entitiesTag = nbt.getTagList("entities", Constants.NBT.TAG_COMPOUND);
            Map<String, Integer> entityCounts = new HashMap<>();

            for (int i = 0; i < entitiesTag.tagCount(); i++) {
                NBTTagCompound entityTag = entitiesTag.getCompoundTagAt(i);
                if (entityTag.hasKey("nbt")) {
                    NBTTagCompound entityNbt = entityTag.getCompoundTag("nbt");
                    String entityId = entityNbt.getString("id");
                    if (!entityId.isEmpty()) entityCounts.merge(entityId, 1, Integer::sum);
                }
            }

            for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
                entities.add(new EntityEntry(new ResourceLocation(entry.getKey()), entry.getValue(), false));
            }
        }

        return new ParsedStructure(sizeX, sizeY, sizeZ, blocks, layers, entities);
    }

    /**
     * Parse an IBlockState from NBT palette entry.
     */
    private static IBlockState parseBlockState(NBTTagCompound nbt) {
        String blockName = nbt.getString("Name");
        Block block = Block.REGISTRY.getObject(new ResourceLocation(blockName));

        if (block == null || block == Blocks.AIR) return Blocks.AIR.getDefaultState();

        IBlockState state = block.getDefaultState();

        // Parse properties if present
        if (nbt.hasKey("Properties")) {
            NBTTagCompound props = nbt.getCompoundTag("Properties");
            for (String key : props.getKeySet()) {
                String value = props.getString(key);
                state = applyProperty(state, key, value);
            }
        }

        return state;
    }

    /**
     * Apply a block state property from string key/value.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState applyProperty(IBlockState state, String propertyName, String value) {
        for (IProperty property : state.getPropertyKeys()) {
            if (property.getName().equals(propertyName)) {
                Optional<?> parsedValue = property.parseValue(value);
                if (parsedValue.isPresent()) return state.withProperty(property, (Comparable) parsedValue.get());
            }
        }

        return state;
    }

    private static final Random RANDOM = new Random();

    /**
     * Create a display ItemStack for a block state.
     * Uses multiple strategies to find the correct item representation:
     * 1. Try Item.getItemFromBlock with damageDropped for proper metadata mapping
     * 2. Try block.getItemDropped to get what the block drops
     * 3. Direct ItemStack creation as fallback
     */
    private static ItemStack createDisplayStack(IBlockState state) {
        Block block = state.getBlock();

        if (block == null || block == Blocks.AIR || block == Blocks.STRUCTURE_VOID) {
            return ItemStack.EMPTY;
        }

        try {
            // Strategy 1: Use Item.getItemFromBlock with damageDropped
            // This works for most blocks that have a direct item form
            Item blockItem = Item.getItemFromBlock(block);
            if (blockItem != null && blockItem != Items.AIR) {
                int damage = block.damageDropped(state);
                ItemStack stack = new ItemStack(blockItem, 1, damage);

                if (!stack.isEmpty()) return stack;
            }

            // Strategy 2: Use getItemDropped to get what the block drops
            // This handles blocks like crops, redstone wire, etc.
            Item droppedItem = block.getItemDropped(state, RANDOM, 0);
            if (droppedItem != null && droppedItem != Items.AIR) {
                int damage = block.damageDropped(state);
                ItemStack stack = new ItemStack(droppedItem, 1, damage);

                if (!stack.isEmpty()) return stack;
            }

            // Strategy 3: Fallback to direct ItemStack creation with block's metadata
            int meta = block.getMetaFromState(state);
            ItemStack fallback = new ItemStack(block, 1, meta);

            if (!fallback.isEmpty() && fallback.getItem() != Items.AIR) return fallback;

            // Last resort: try meta 0
            fallback = new ItemStack(block, 1, 0);
            if (!fallback.isEmpty() && fallback.getItem() != Items.AIR) return fallback;

            return ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
