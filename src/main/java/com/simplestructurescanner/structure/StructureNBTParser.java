package com.simplestructurescanner.structure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Parses structure NBT files and exposes extension hooks for provider-specific metadata.
 * <p>
 * The default implementation handles block counts, layers, entities, spawners, and loot table tags.
 * Providers such as Pillar can extend this parser to add extra metadata discovery while still reusing
 * the shared block walk, layer construction, and output assembly logic.
 */
public class StructureNBTParser {

    /**
     * Extension point for custom structure parsers.
     * <p>
     * This is intentionally public so unofficial integrations can reuse the shared parser without
     * copying the entire implementation.
     */
    public interface StructureParseExtension {
        default boolean shouldCountBlock(@Nullable IBlockState state, @Nullable Block block) {
            return !StructureNBTParser.isInvisibleBlock(block) && !StructureNBTParser.isFlowingFluid(state, block);
        }

        @Nullable
        default Object getBlockCountKey(@Nullable IBlockState state, @Nullable Block block) {
            FluidStack displayFluid = StructureNBTParser.createDisplayFluid(state);
            if (displayFluid != null && displayFluid.getFluid() != null) return displayFluid.getFluid().getName();

            return state;
        }

        default boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            // The preview should keep the original block state so flowing fluids still render as flowing.
            return !StructureNBTParser.isInvisibleBlock(block);
        }

        default void handleBlockEntity(ParsedStructureBuilder builder, NBTTagCompound blockEntry,
                @Nullable IBlockState state, @Nullable Block block, NBTTagCompound nbtData) {
            StructureNBTParser.handleDefaultBlockEntity(builder, block, nbtData);
        }

        default void handleEntity(ParsedStructureBuilder builder, NBTTagCompound entityNbt) {
            StructureNBTParser.handleDefaultEntity(builder, entityNbt);
        }

        default void finish(ParsedStructureBuilder builder, NBTTagCompound structureNbt) {
        }
    }

    /**
     * Result of parsing a structure NBT file.
     */
    public static class ParsedStructure {
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        public final List<BlockEntry> blocks;
        public final List<StructureLayer> layers;
        public final List<EntityEntry> entities;
        public final List<LootEntry> lootTables;

        public ParsedStructure(int sizeX, int sizeY, int sizeZ,
                List<BlockEntry> blocks,
                List<StructureLayer> layers,
                List<EntityEntry> entities,
                List<LootEntry> lootTables) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = blocks;
            this.layers = layers;
            this.entities = entities;
            this.lootTables = lootTables;
        }
    }

    /**
     * Mutable builder used by parser extensions to add blocks, layers, entities, and loot.
     */
    public static class ParsedStructureBuilder {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<Object, Integer> blockCounts = new LinkedHashMap<>();
        private final Map<Object, IBlockState> blockRepresentatives = new LinkedHashMap<>();
        private final Map<Integer, IBlockState[][]> layerBlocks = new LinkedHashMap<>();
        private final Map<EntityKey, Integer> entityCounts = new LinkedHashMap<>();
        private final Set<ResourceLocation> lootTableIds = new LinkedHashSet<>();
        private final List<LootEntry> extraLootEntries = new ArrayList<>();

        public ParsedStructureBuilder(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;

            for (int y = 0; y < sizeY; y++) {
                layerBlocks.put(y, new IBlockState[sizeX][sizeZ]);
            }
        }

        public int getSizeX() {
            return sizeX;
        }

        public int getSizeY() {
            return sizeY;
        }

        public int getSizeZ() {
            return sizeZ;
        }

        public void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState) {
            if (key == null || representativeState == null) return;

            blockCounts.merge(key, 1, Integer::sum);
            if (!blockRepresentatives.containsKey(key)) blockRepresentatives.put(key, representativeState);
        }

        public void setLayerBlock(int x, int y, int z, @Nullable IBlockState state) {
            if (state == null) return;
            if (y < 0 || y >= sizeY || x < 0 || x >= sizeX || z < 0 || z >= sizeZ) return;

            layerBlocks.get(y)[x][z] = state;
        }

        public void addEntity(ResourceLocation entityId, boolean spawner) {
            EntityKey key = new EntityKey(entityId, spawner);
            entityCounts.merge(key, 1, Integer::sum);
        }

        public void addLootTable(ResourceLocation lootTableId) {
            if (lootTableId == null) return;

            lootTableIds.add(lootTableId);
        }

        public void addLootEntry(@Nullable LootEntry lootEntry) {
            if (lootEntry == null) return;

            extraLootEntries.add(lootEntry);
        }

        public ParsedStructure build() {
            // Convert the aggregated counts into UI-ready block entries using one representative state per key.
            List<BlockEntry> blocks = new ArrayList<>();
            for (Map.Entry<Object, Integer> entry : blockCounts.entrySet()) {
                IBlockState representative = blockRepresentatives.get(entry.getKey());
                if (representative == null) continue;

                BlockEntry blockEntry = createBlockEntry(representative, entry.getValue());
                if (blockEntry != null) blocks.add(blockEntry);
            }

            blocks.sort((a, b) -> Integer.compare(b.count, a.count));

            // Rebuild the layer list from the indexed 3D snapshot collected during parsing.
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

            // Entity counts are merged by id + spawner flag so repeated references become one UI entry.
            List<EntityEntry> entities = new ArrayList<>();
            for (Map.Entry<EntityKey, Integer> entry : entityCounts.entrySet()) {
                entities.add(new EntityEntry(entry.getKey().entityId, entry.getValue(), entry.getKey().spawner));
            }

            // Loot tables discovered from NBT tags become regular loot entries, then extension-specific extras are appended.
            List<LootEntry> lootTables = new ArrayList<>();
            for (ResourceLocation lootTableId : lootTableIds) {
                lootTables.add(new LootEntry(lootTableId, new ArrayList<>(),
                    LocalizedText.translatable("gui.structurescanner.loot.chest")));
            }

            lootTables.addAll(extraLootEntries);

            return new ParsedStructure(sizeX, sizeY, sizeZ, blocks, layers, entities, lootTables);
        }
    }

    private static final class EntityKey {
        private final ResourceLocation entityId;
        private final boolean spawner;

        private EntityKey(ResourceLocation entityId, boolean spawner) {
            this.entityId = entityId;
            this.spawner = spawner;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            EntityKey that = (EntityKey) obj;
            return spawner == that.spawner && entityId.equals(that.entityId);
        }

        @Override
        public int hashCode() {
            int result = entityId.hashCode();
            return 31 * result + Boolean.hashCode(spawner);
        }
    }

    private static final StructureParseExtension DEFAULT_EXTENSION = new StructureParseExtension() {
    };

    /**
     * Parse a structure NBT from the built-in Minecraft assets.
     * @param structurePath Path relative to assets/minecraft/structures/ without .nbt extension
     */
    @Nullable
    public static ParsedStructure parseStructure(String structurePath) {
        return parseStructure(structurePath, null);
    }

    /**
     * Parse a structure NBT from the built-in Minecraft assets with a custom extension.
     */
    @Nullable
    public static ParsedStructure parseStructure(String structurePath, @Nullable StructureParseExtension extension) {
        String resourcePath = "/assets/minecraft/structures/" + structurePath + ".nbt";

        return parseResource(resourcePath, "minecraft:" + structurePath, extension);
    }

    /**
     * Parse a structure NBT from bundled mod assets.
     * @param namespace Asset namespace
     * @param structurePath Path relative to assets/<namespace>/structures/ without .nbt extension
     */
    @Nullable
    public static ParsedStructure parseBundledStructure(String namespace, String structurePath) {
        return parseBundledStructure(namespace, structurePath, null);
    }

    /**
     * Parse a structure NBT from bundled mod assets with a custom extension.
     */
    @Nullable
    public static ParsedStructure parseBundledStructure(String namespace, String structurePath,
            @Nullable StructureParseExtension extension) {
        String resourcePath = "/assets/" + namespace + "/structures/" + structurePath + ".nbt";

        return parseResource(resourcePath, namespace + ":" + structurePath, extension);
    }

    @Nullable
    private static ParsedStructure parseResource(String resourcePath, String structureId,
            @Nullable StructureParseExtension extension) {
        try (InputStream stream = StructureNBTParser.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                SimpleStructureScanner.LOGGER.debug("Structure file not found: {}", resourcePath);
                return null;
            }

            NBTTagCompound nbt = CompressedStreamTools.readCompressed(stream);
            return parseNBT(nbt, extension);
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse structure {}: {}", structureId, e.getMessage());
            return null;
        }
    }

    /**
     * Parse a structure NBT file directly from disk.
     */
    @Nullable
    public static ParsedStructure parseStructureFile(File nbtFile) {
        return parseStructureFile(nbtFile, null);
    }

    /**
     * Parse a structure NBT file directly from disk with a custom extension.
     */
    @Nullable
    public static ParsedStructure parseStructureFile(File nbtFile, @Nullable StructureParseExtension extension) {
        if (nbtFile == null || !nbtFile.exists() || !nbtFile.isFile()) return null;

        try (InputStream stream = Files.newInputStream(nbtFile.toPath())) {
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(stream);
            return parseNBT(nbt, extension);
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse structure NBT file {}: {}", nbtFile.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Parse an in-memory structure NBT tag.
     * Used by features that already have the serialized structure contents without a backing file.
     */
    @Nullable
    public static ParsedStructure parseStructureNbt(@Nullable NBTTagCompound nbt) {
        if (nbt == null) return null;

        return parseNBT(nbt, null);
    }

    @Nullable
    private static ParsedStructure parseNBT(NBTTagCompound nbt, @Nullable StructureParseExtension extension) {
        if (!nbt.hasKey("size") || !nbt.hasKey("palette") || !nbt.hasKey("blocks")) return null;

        StructureParseExtension parseExtension = extension != null ? extension : DEFAULT_EXTENSION;

        NBTTagList sizeTag = nbt.getTagList("size", Constants.NBT.TAG_INT);
        int sizeX = sizeTag.getIntAt(0);
        int sizeY = sizeTag.getIntAt(1);
        int sizeZ = sizeTag.getIntAt(2);

        NBTTagList paletteTag = nbt.getTagList("palette", Constants.NBT.TAG_COMPOUND);
        IBlockState[] palette = new IBlockState[paletteTag.tagCount()];

        for (int i = 0; i < paletteTag.tagCount(); i++) {
            palette[i] = parseBlockState(paletteTag.getCompoundTagAt(i));
        }

        ParsedStructureBuilder builder = new ParsedStructureBuilder(sizeX, sizeY, sizeZ);
        NBTTagList blocksTag = nbt.getTagList("blocks", Constants.NBT.TAG_COMPOUND);

        // Walk every placed block once so the base parser and the extension hooks see the exact same structure data.
        for (int i = 0; i < blocksTag.tagCount(); i++) {
            NBTTagCompound blockEntry = blocksTag.getCompoundTagAt(i);
            int paletteIndex = blockEntry.getInteger("state");
            if (paletteIndex < 0 || paletteIndex >= palette.length) continue;

            IBlockState state = palette[paletteIndex];
            Block block = state != null ? state.getBlock() : null;

            if (parseExtension.shouldCountBlock(state, block)) {
                builder.addBlockCount(parseExtension.getBlockCountKey(state, block), state);
            }

            NBTTagList posTag = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
            int x = posTag.getIntAt(0);
            int y = posTag.getIntAt(1);
            int z = posTag.getIntAt(2);

            if (parseExtension.shouldStoreLayerBlock(state, block)) {
                builder.setLayerBlock(x, y, z, state);
            }

            if (blockEntry.hasKey("nbt")) {
                parseExtension.handleBlockEntity(builder, blockEntry, state, block, blockEntry.getCompoundTag("nbt"));
            }
        }

        if (nbt.hasKey("entities")) {
            NBTTagList entitiesTag = nbt.getTagList("entities", Constants.NBT.TAG_COMPOUND);

            // Entity parsing is also delegated through the extension so integrations can reinterpret custom metadata.
            for (int i = 0; i < entitiesTag.tagCount(); i++) {
                NBTTagCompound entityTag = entitiesTag.getCompoundTagAt(i);
                if (!entityTag.hasKey("nbt")) continue;

                parseExtension.handleEntity(builder, entityTag.getCompoundTag("nbt"));
            }
        }

        // Give extensions one final pass after the shared walk is complete.
        parseExtension.finish(builder, nbt);
        return builder.build();
    }

    public static void handleDefaultBlockEntity(ParsedStructureBuilder builder, @Nullable Block block, NBTTagCompound nbtData) {
        // Spawner and LootTable tags are widely used enough to belong in the shared default parser.
        if (block == Blocks.MOB_SPAWNER) parseSpawnerTileEntityNBT(builder, nbtData);

        if (nbtData.hasKey("LootTable")) {
            String lootTable = nbtData.getString("LootTable");
            if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation(lootTable));
        }
    }

    public static void handleDefaultEntity(ParsedStructureBuilder builder, NBTTagCompound entityNbt) {
        String entityId = entityNbt.getString("id");
        if (entityId.isEmpty()) return;

        builder.addEntity(new ResourceLocation(entityId), false);
    }

    public static void parseSpawnerTileEntityNBT(ParsedStructureBuilder builder, NBTTagCompound nbt) {
        Set<String> foundIds = new LinkedHashSet<>();

        // SpawnPotentials is preferred because it can expose every possible mob a spawner may create.
        if (nbt.hasKey("SpawnPotentials", Constants.NBT.TAG_LIST)) {
            NBTTagList potentials = nbt.getTagList("SpawnPotentials", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < potentials.tagCount(); i++) {
                NBTTagCompound potential = potentials.getCompoundTagAt(i);
                if (!potential.hasKey("Entity", Constants.NBT.TAG_COMPOUND)) continue;

                String id = potential.getCompoundTag("Entity").getString("id");
                if (!id.isEmpty()) foundIds.add(id);
            }
        }

        // If no potential list exists, fall back to the currently selected SpawnData entry.
        if (foundIds.isEmpty() && nbt.hasKey("SpawnData", Constants.NBT.TAG_COMPOUND)) {
            String id = nbt.getCompoundTag("SpawnData").getString("id");
            if (!id.isEmpty()) foundIds.add(id);
        }

        for (String id : foundIds) {
            builder.addEntity(new ResourceLocation(id), true);
        }
    }

    public static boolean isInvisibleBlock(@Nullable Block block) {
        return block == null || block == Blocks.AIR || block == Blocks.STRUCTURE_VOID;
    }

    public static boolean isFluidBlock(@Nullable IBlockState state, @Nullable Block block) {
        if (state == null || block == null) return false;
        if (state.getMaterial().isLiquid()) return true;
        if (FluidRegistry.lookupFluidForBlock(block) != null) return true;

        return block instanceof IFluidBlock;
    }

    public static boolean isFlowingFluid(@Nullable IBlockState state, @Nullable Block block) {
        if (!isFluidBlock(state, block)) return false;
        if (block == Blocks.FLOWING_WATER || block == Blocks.FLOWING_LAVA) return true;

        Integer fluidLevel = getFluidLevel(state);
        if (fluidLevel != null) return fluidLevel > 0;

        return block.getMetaFromState(state) > 0;
    }

    @Nullable
    public static BlockEntry createBlockEntry(@Nullable IBlockState state, int count) {
        if (state == null) return null;

        FluidStack displayFluid = createDisplayFluid(state);
        if (displayFluid != null) return new BlockEntry(state, null, displayFluid, count);

        return new BlockEntry(state, createDisplayStack(state), count);
    }

    @Nullable
    public static FluidStack createDisplayFluid(@Nullable IBlockState state) {
        Block block = state != null ? state.getBlock() : null;
        if (state == null || isFlowingFluid(state, block)) return null;

        Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
        if (fluid == null && block instanceof IFluidBlock) fluid = ((IFluidBlock) block).getFluid();
        if (fluid == null && block == Blocks.WATER) fluid = FluidRegistry.getFluid("water");
        if (fluid == null && block == Blocks.LAVA) fluid = FluidRegistry.getFluid("lava");
        if (fluid == null) return null;

        return new FluidStack(fluid, Fluid.BUCKET_VOLUME);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    private static Integer getFluidLevel(IBlockState state) {
        for (IProperty property : state.getPropertyKeys()) {
            if (!"level".equals(property.getName())) continue;

            Comparable value = state.getValue(property);
            if (value instanceof Number) return ((Number) value).intValue();
        }

        return null;
    }

    /**
     * Parse an IBlockState from an NBT palette entry.
     */
    public static IBlockState parseBlockState(NBTTagCompound nbt) {
        String blockName = nbt.getString("Name");
        Block block = Block.REGISTRY.getObject(new ResourceLocation(blockName));

        if (block == Blocks.AIR) return Blocks.AIR.getDefaultState();

        IBlockState state = block.getDefaultState();
        if (!nbt.hasKey("Properties")) return state;

        NBTTagCompound props = nbt.getCompoundTag("Properties");
        for (String key : props.getKeySet()) {
            state = applyProperty(state, key, props.getString(key));
        }

        return state;
    }

    /**
     * Apply a block state property from string key/value.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static IBlockState applyProperty(IBlockState state, String propertyName, String value) {
        for (IProperty property : state.getPropertyKeys()) {
            if (!property.getName().equals(propertyName)) continue;

            Optional<?> parsedValue = property.parseValue(value);
            if (parsedValue.isPresent()) return state.withProperty(property, (Comparable) parsedValue.get());
        }

        return state;
    }

    private static final Random RANDOM = new Random();

    /**
     * Create a display ItemStack for a block state.
     */
    public static ItemStack createDisplayStack(IBlockState state) {
        Block block = state.getBlock();
        if (isInvisibleBlock(block) || isFluidBlock(state, block)) return ItemStack.EMPTY;

        try {
            // Strategy 1: Use Item.getItemFromBlock with damageDropped
            // This works for most blocks that have a direct item form
            Item blockItem = Item.getItemFromBlock(block);
            if (blockItem != Items.AIR) {
                ItemStack stack = new ItemStack(blockItem, 1, block.damageDropped(state));
                if (!stack.isEmpty()) return stack;
            }

            // Strategy 2: Use getItemDropped to get what the block drops
            // This handles blocks like crops, redstone wire, etc.
            Item droppedItem = block.getItemDropped(state, RANDOM, 0);
            if (droppedItem != Items.AIR) {
                ItemStack stack = new ItemStack(droppedItem, 1, block.damageDropped(state));
                if (!stack.isEmpty()) return stack;
            }

            // Strategy 3: Fallback to direct ItemStack creation with block's metadata
            ItemStack fallback = new ItemStack(block, 1, block.getMetaFromState(state));
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
