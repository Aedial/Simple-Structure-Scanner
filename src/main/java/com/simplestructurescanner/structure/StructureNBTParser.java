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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.NonNullList;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntryKind;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Parses structure NBT files and exposes extension hooks for provider-specific metadata.
 * <p>
 * The default implementation handles block counts, layers, entities, spawners, and container loot metadata.
 * Providers such as Pillar can extend this parser to add extra metadata discovery while still reusing
 * the shared block walk, layer construction, and output assembly logic.
 */
public class StructureNBTParser {

    private static final String FLUID_BLOCK_KEY_PREFIX = "fluid:";
    private static final String ITEM_BLOCK_KEY_PREFIX = "item:";
    private static final String BLOCK_BLOCK_KEY_PREFIX = "block:";
    private static final WorldSettings DISPLAY_WORLD_SETTINGS = new WorldSettings(
        1L, GameType.CREATIVE, false, false, WorldType.FLAT
    );

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
        default Object getBlockCountKey(@Nullable IBlockState state, @Nullable Block block,
                @Nullable NBTTagCompound blockEntityData) {
            return StructureNBTParser.createDisplayedBlockKey(
                state,
                StructureNBTParser.createDisplayFluid(state),
                StructureNBTParser.createDisplayStack(state, blockEntityData)
            );
        }

        default boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            // The preview should keep the original block state so flowing fluids still render as flowing.
            return !StructureNBTParser.isInvisibleBlock(block);
        }

        default void handleBlockEntity(ParsedStructureBuilder builder, NBTTagCompound blockEntry,
                @Nullable IBlockState state, @Nullable Block block, NBTTagCompound nbtData) {
            StructureNBTParser.handleDefaultBlockEntity(builder, state, block, nbtData);
        }

        default void handleEntity(ParsedStructureBuilder builder, NBTTagCompound entityNbt) {
            StructureNBTParser.handleDefaultEntity(builder, entityNbt);
        }

        default void finish(ParsedStructureBuilder builder, NBTTagCompound structureNbt) {
        }
    }

    public interface StructureContentSink {
        void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState);

        void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState,
                @Nullable NBTTagCompound blockEntityData);

        void addEntity(ResourceLocation entityId, boolean spawner);

        void addLootTable(ResourceLocation lootTableId);

        void addLootEntry(@Nullable LootEntry lootEntry);
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
    public static class ParsedStructureBuilder implements StructureContentSink {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<Object, Integer> blockCounts = new LinkedHashMap<>();
        private final Map<Object, BlockCountRepresentative> blockRepresentatives = new LinkedHashMap<>();
        private final Map<Integer, StructureLayer> layerBlocks = new LinkedHashMap<>();
        private final Map<EntityKey, Integer> entityCounts = new LinkedHashMap<>();
        private final Set<ResourceLocation> lootTableIds = new LinkedHashSet<>();
        private final List<LootEntry> extraLootEntries = new ArrayList<>();

        public ParsedStructureBuilder(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;

            for (int y = 0; y < sizeY; y++) {
                layerBlocks.put(y, new StructureLayer(y, sizeX, sizeZ));
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

        @Override
        public void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState) {
            addBlockCount(key, representativeState, null);
        }

        @Override
        public void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState,
                @Nullable NBTTagCompound blockEntityData) {
            if (key == null || representativeState == null) return;

            blockCounts.merge(key, 1, Integer::sum);
            BlockCountRepresentative representative = blockRepresentatives.get(key);
            if (representative == null) {
                blockRepresentatives.put(key, new BlockCountRepresentative(representativeState, blockEntityData));
                return;
            }

            representative.preserveBlockEntityData(blockEntityData);
        }

        public void setLayerBlock(int x, int y, int z, @Nullable IBlockState state) {
            setLayerBlock(x, y, z, state, null);
        }

        public void setLayerBlock(int x, int y, int z, @Nullable IBlockState state,
                @Nullable NBTTagCompound blockEntityData) {
            if (state == null) return;
            if (y < 0 || y >= sizeY || x < 0 || x >= sizeX || z < 0 || z >= sizeZ) return;

            layerBlocks.get(y).setBlockState(x, z, state, blockEntityData);
        }

        @Override
        public void addEntity(ResourceLocation entityId, boolean spawner) {
            EntityKey key = new EntityKey(entityId, spawner);
            entityCounts.merge(key, 1, Integer::sum);
        }

        @Override
        public void addLootTable(ResourceLocation lootTableId) {
            if (lootTableId == null) return;

            lootTableIds.add(lootTableId);
        }

        @Override
        public void addLootEntry(@Nullable LootEntry lootEntry) {
            if (lootEntry == null) return;
            if (containsEquivalentLootEntry(lootEntry)) return;

            extraLootEntries.add(lootEntry);
        }

        private boolean containsEquivalentLootEntry(LootEntry candidate) {
            String candidateKey = createLootEntryKey(candidate);

            for (LootEntry existingEntry : extraLootEntries) {
                if (createLootEntryKey(existingEntry).equals(candidateKey)) return true;
            }

            return false;
        }

        public ParsedStructure build() {
            // Convert the aggregated counts into UI-ready block entries using one representative state per key.
            List<BlockEntry> blocks = new ArrayList<>();
            for (Map.Entry<Object, Integer> entry : blockCounts.entrySet()) {
                BlockCountRepresentative representative = blockRepresentatives.get(entry.getKey());
                if (representative == null) continue;

                BlockEntry blockEntry = createBlockEntry(representative.state, representative.blockEntityData,
                    entry.getValue());
                if (blockEntry != null) blocks.add(blockEntry);
            }

            blocks.sort((a, b) -> Integer.compare(b.count, a.count));

            // Rebuild the layer list from the indexed 3D snapshot collected during parsing.
            List<StructureLayer> layers = new ArrayList<>();
            for (int y = 0; y < sizeY; y++) layers.add(layerBlocks.get(y));

            // Entity counts are merged by id + spawner flag so repeated references become one UI entry.
            List<EntityEntry> entities = new ArrayList<>();
            for (Map.Entry<EntityKey, Integer> entry : entityCounts.entrySet()) {
                entities.add(new EntityEntry(entry.getKey().entityId, entry.getValue(), entry.getKey().spawner));
            }

            // Loot tables discovered from NBT tags become regular loot entries, then fixed inventories and extension-specific extras are appended.
            List<LootEntry> lootTables = new ArrayList<>();
            for (ResourceLocation lootTableId : lootTableIds) {
                lootTables.add(new LootEntry(lootTableId, new ArrayList<>(),
                    LocalizedText.translatable("gui.structurescanner.loot.chest")));
            }

            lootTables.addAll(extraLootEntries);

            return new ParsedStructure(sizeX, sizeY, sizeZ, blocks, layers, entities, lootTables);
        }
    }

    private static final class BlockCountRepresentative {
        private final IBlockState state;
        @Nullable
        private NBTTagCompound blockEntityData;

        private BlockCountRepresentative(IBlockState state, @Nullable NBTTagCompound blockEntityData) {
            this.state = state;
            this.blockEntityData = blockEntityData != null && !blockEntityData.isEmpty() ? blockEntityData.copy() : null;
        }

        private void preserveBlockEntityData(@Nullable NBTTagCompound blockEntityData) {
            if (this.blockEntityData != null || blockEntityData == null || blockEntityData.isEmpty()) return;

            this.blockEntityData = blockEntityData.copy();
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
            NBTTagCompound blockEntityData = blockEntry.hasKey("nbt", Constants.NBT.TAG_COMPOUND)
                ? blockEntry.getCompoundTag("nbt")
                : null;

            if (parseExtension.shouldCountBlock(state, block)) {
                Object blockCountKey = parseExtension.getBlockCountKey(state, block, blockEntityData);
                builder.addBlockCount(blockCountKey, state, blockEntityData);
            }

            NBTTagList posTag = blockEntry.getTagList("pos", Constants.NBT.TAG_INT);
            int x = posTag.getIntAt(0);
            int y = posTag.getIntAt(1);
            int z = posTag.getIntAt(2);

            if (parseExtension.shouldStoreLayerBlock(state, block)) {
                builder.setLayerBlock(x, y, z, state, blockEntityData);
            }

            if (blockEntityData == null) continue;

            parseExtension.handleBlockEntity(builder, blockEntry, state, block, blockEntityData);
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

    public static void handleDefaultBlockEntity(StructureContentSink builder, @Nullable Block block,
            NBTTagCompound nbtData) {
        handleDefaultBlockEntity(builder, null, block, nbtData);
    }

    public static void handleDefaultBlockEntity(StructureContentSink builder, @Nullable IBlockState state,
            @Nullable Block block, NBTTagCompound nbtData) {
        // Spawner and LootTable tags are widely used enough to belong in the shared default parser.
        if (isSpawnerTileEntity(block, nbtData)) parseSpawnerTileEntityNBT(builder, nbtData);

        if (nbtData.hasKey("LootTable")) {
            String lootTable = nbtData.getString("LootTable");
            if (!lootTable.isEmpty()) builder.addLootTable(new ResourceLocation(lootTable));
        }

        // Some manually-authored structures include both fixed items and a loot table tag.
        // Surface both sources separately instead of assuming one should hide the other.
        LootEntry fixedInventory = createFixedInventoryLootEntry(state, nbtData);
        if (fixedInventory != null) builder.addLootEntry(fixedInventory);
    }

    public static void handleDefaultEntity(StructureContentSink builder, NBTTagCompound entityNbt) {
        String entityId = entityNbt.getString("id");
        if (entityId.isEmpty()) return;

        ResourceLocation resolvedEntityId = new ResourceLocation(entityId);
        if (!shouldIncludeStructureEntity(resolvedEntityId)) return;

        builder.addEntity(resolvedEntityId, false);
    }

    public static boolean shouldIncludeStructureEntity(@Nullable ResourceLocation entityId) {
        if (entityId == null) return false;

        Class<? extends Entity> entityClass = EntityList.getClass(entityId);
        if (entityClass == null) return true;

        // The entities window is meant to show mob spawns, not decorative placed entities.
        // Unknown ids are left visible so optional or late-bound entities are not hidden by mistake.
        return EntityLiving.class.isAssignableFrom(entityClass);
    }

    private static boolean isSpawnerTileEntity(@Nullable Block block, NBTTagCompound nbtData) {
        if (block == Blocks.MOB_SPAWNER) return true;

        if (nbtData.hasKey("id", Constants.NBT.TAG_STRING)) {
            String tileEntityId = nbtData.getString("id");
            if ("minecraft:mob_spawner".equals(tileEntityId) || "MobSpawner".equals(tileEntityId)) return true;
        }

        if (nbtData.hasKey("SpawnPotentials", Constants.NBT.TAG_LIST)) return true;
        if (nbtData.hasKey("SpawnData", Constants.NBT.TAG_COMPOUND)) return true;

        return nbtData.hasKey("EntityId", Constants.NBT.TAG_STRING) && !nbtData.getString("EntityId").isEmpty();
    }

    public static void parseSpawnerTileEntityNBT(StructureContentSink builder, NBTTagCompound nbt) {
        for (ResourceLocation entityId : collectSpawnerEntityIds(nbt)) {
            builder.addEntity(entityId, true);
        }
    }

    public static Set<ResourceLocation> collectSpawnerEntityIds(NBTTagCompound nbt) {
        Set<ResourceLocation> foundIds = new LinkedHashSet<>();

        // SpawnPotentials is preferred because it can expose every possible mob a spawner may create.
        if (nbt.hasKey("SpawnPotentials", Constants.NBT.TAG_LIST)) {
            NBTTagList potentials = nbt.getTagList("SpawnPotentials", Constants.NBT.TAG_COMPOUND);

            for (int i = 0; i < potentials.tagCount(); i++) {
                collectSpawnerPotentialEntityId(foundIds, potentials.getCompoundTagAt(i));
            }
        }

        // If no potential list exists, fall back to the currently selected SpawnData entry.
        if (foundIds.isEmpty() && nbt.hasKey("SpawnData", Constants.NBT.TAG_COMPOUND)) {
            collectSpawnerEntityCompoundId(foundIds, nbt.getCompoundTag("SpawnData"));
        }

        // Older structure snapshots still store the selected mob under EntityId.
        if (foundIds.isEmpty()) addSpawnerEntityId(foundIds, nbt.getString("EntityId"));

        return foundIds;
    }

    private static void collectSpawnerPotentialEntityId(Set<ResourceLocation> foundIds, NBTTagCompound potential) {
        boolean found = false;

        if (potential.hasKey("Entity", Constants.NBT.TAG_COMPOUND)) {
            found = collectSpawnerEntityCompoundId(foundIds, potential.getCompoundTag("Entity"));
        }

        // Legacy SpawnPotentials entries used Type + Properties instead of Entity.
        if (!found && potential.hasKey("Properties", Constants.NBT.TAG_COMPOUND)) {
            found = collectSpawnerEntityCompoundId(foundIds, potential.getCompoundTag("Properties"));
        }

        if (!found) addSpawnerEntityId(foundIds, potential.getString("Type"));
    }

    private static boolean collectSpawnerEntityCompoundId(Set<ResourceLocation> foundIds, NBTTagCompound entityTag) {
        if (addSpawnerEntityId(foundIds, entityTag.getString("id"))) return true;

        return addSpawnerEntityId(foundIds, entityTag.getString("EntityId"));
    }

    private static boolean addSpawnerEntityId(Set<ResourceLocation> foundIds, String entityIdText) {
        if (entityIdText.isEmpty()) return false;

        foundIds.add(new ResourceLocation(entityIdText));
        return true;
    }

    @Nullable
    private static LootEntry createFixedInventoryLootEntry(@Nullable IBlockState state, NBTTagCompound nbtData) {
        List<ItemStack> fixedItems = extractFixedInventoryItems(nbtData);
        if (fixedItems.isEmpty()) return null;

        List<ItemStack> mergedItems = mergeItemStacks(fixedItems);
        if (mergedItems.isEmpty()) return null;

        return new LootEntry(
            null,
            mergedItems,
            createFixedInventoryContainerType(state),
            LootEntryKind.FIXED_ITEMS
        );
    }

    private static LocalizedText createFixedInventoryContainerType(@Nullable IBlockState state) {
        ItemStack displayStack = state != null ? createDisplayStack(state) : ItemStack.EMPTY;
        if (!displayStack.isEmpty()) {
            String translationKey = displayStack.getTranslationKey() + ".name";
            return LocalizedText.translatableWithFallback(translationKey,
                LocalizedText.literal(displayStack.getDisplayName()));
        }

        Block block = state != null ? state.getBlock() : null;
        if (block != null) {
            String translationKey = block.getTranslationKey() + ".name";
            return LocalizedText.translatableWithFallback(translationKey,
                LocalizedText.literal(block.getLocalizedName()));
        }

        return LocalizedText.translatable("gui.structurescanner.loot.container");
    }

    private static List<ItemStack> extractFixedInventoryItems(NBTTagCompound nbtData) {
        List<ItemStack> items = new ArrayList<>();
        if (!nbtData.hasKey("Items", Constants.NBT.TAG_LIST)) return items;

        NBTTagList itemList = nbtData.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < itemList.tagCount(); i++) {
            ItemStack stack = new ItemStack(itemList.getCompoundTagAt(i));
            if (!stack.isEmpty()) items.add(stack);
        }

        return items;
    }

    private static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
        Map<String, ItemStack> mergedItems = new LinkedHashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            String itemKey = createItemStackKey(stack);
            ItemStack existingStack = mergedItems.get(itemKey);

            if (existingStack != null) {
                existingStack.grow(stack.getCount());
                continue;
            }

            mergedItems.put(itemKey, stack.copy());
        }

        List<ItemStack> result = new ArrayList<>(mergedItems.values());
        result.sort((first, second) -> {
            int countCompare = Integer.compare(second.getCount(), first.getCount());
            if (countCompare != 0) return countCompare;

            return createItemStackKey(first).compareTo(createItemStackKey(second));
        });

        return result;
    }

    private static String createLootEntryKey(LootEntry lootEntry) {
        StringBuilder key = new StringBuilder();
        key.append(lootEntry.lootTableId != null ? lootEntry.lootTableId.toString() : "<direct>");
        key.append('|').append(lootEntry.kind.name());
        key.append('|').append(lootEntry.containerType.isTranslatable()).append(':').append(lootEntry.containerType.getValue());

        if (lootEntry.sourceName != null) {
            key.append('|').append(lootEntry.sourceName.isTranslatable()).append(':').append(lootEntry.sourceName.getValue());
        }

        if (lootEntry.sourceStack != null) key.append('|').append(createItemStackKey(lootEntry.sourceStack));

        if (lootEntry.possibleDrops == null) return key.toString();

        for (ItemStack stack : lootEntry.possibleDrops) {
            key.append('|').append(createItemStackKey(stack)).append('*').append(stack.getCount());
        }

        return key.toString();
    }

    private static String createItemStackKey(ItemStack stack) {
        if (stack.isEmpty()) return "empty";

        NBTTagCompound normalizedStack = stack.copy().writeToNBT(new NBTTagCompound());
        normalizedStack.removeTag("Count");
        return normalizedStack.toString();
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
        return createBlockEntry(state, null, count);
    }

    @Nullable
    public static BlockEntry createBlockEntry(@Nullable IBlockState state, @Nullable NBTTagCompound blockEntityData,
            int count) {
        if (state == null) return null;

        FluidStack displayFluid = createDisplayFluid(state);
        if (displayFluid != null) return new BlockEntry(state, null, displayFluid, blockEntityData, count);

        return new BlockEntry(state, createDisplayStack(state, blockEntityData), null, blockEntityData, count);
    }

    /**
     * Build the UI grouping key for a block using the already-resolved display fluid or item.
     */
    public static String createDisplayedBlockKey(@Nullable IBlockState state, @Nullable FluidStack displayFluid,
            @Nullable ItemStack displayStack) {
        Block block = state != null ? state.getBlock() : null;

        if (displayFluid != null && displayFluid.getFluid() != null) {
            return FLUID_BLOCK_KEY_PREFIX + displayFluid.getFluid().getName();
        }

        if (displayStack != null && !displayStack.isEmpty()) {
            return ITEM_BLOCK_KEY_PREFIX + createItemStackKey(displayStack);
        }

        String blockId = block != null && block.getRegistryName() != null ? block.getRegistryName().toString() : "minecraft:air";
        return BLOCK_BLOCK_KEY_PREFIX + blockId;
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
    private static final DisplayBlockWorld DISPLAY_BLOCK_WORLD = new DisplayBlockWorld();

    /**
     * Create a display ItemStack for a block state.
     */
    public static ItemStack createDisplayStack(IBlockState state) {
        return createDisplayStack(state, null);
    }

    public static ItemStack createDisplayStack(@Nullable IBlockState state, @Nullable NBTTagCompound blockEntityData) {
        if (state == null) return ItemStack.EMPTY;

        Block block = state.getBlock();
        if (isInvisibleBlock(block) || isFluidBlock(state, block)) return ItemStack.EMPTY;

        ItemStack blockEntityDisplayStack = createBlockEntityDisplayStack(state, blockEntityData);
        if (!blockEntityDisplayStack.isEmpty()) return blockEntityDisplayStack;

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

    private static ItemStack createBlockEntityDisplayStack(IBlockState state, @Nullable NBTTagCompound blockEntityData) {
        if (blockEntityData == null || blockEntityData.isEmpty()) return ItemStack.EMPTY;

        TileEntity tileEntity = createDisplayTileEntity(state, blockEntityData);
        if (tileEntity == null) return ItemStack.EMPTY;

        NonNullList<ItemStack> drops = NonNullList.create();

        try {
            synchronized (DISPLAY_BLOCK_WORLD) {
                // Some modded drop helpers ignore the supplied IBlockAccess and reach
                // back through tileEntity.getWorld(), so both views need the same block.
                DISPLAY_BLOCK_WORLD.setDisplayBlock(state, tileEntity);

                try {
                    state.getBlock().getDrops(drops, DISPLAY_BLOCK_WORLD, BlockPos.ORIGIN, state, 0);
                } finally {
                    DISPLAY_BLOCK_WORLD.clearDisplayBlock();
                }
            }
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) return drop;
        }

        return ItemStack.EMPTY;
    }

    @Nullable
    private static TileEntity createDisplayTileEntity(IBlockState state, NBTTagCompound blockEntityData) {
        try {
            NBTTagCompound tileEntityTag = blockEntityData.copy();
            tileEntityTag.setInteger("x", 0);
            tileEntityTag.setInteger("y", 0);
            tileEntityTag.setInteger("z", 0);

            if (tileEntityTag.hasKey("id", Constants.NBT.TAG_STRING)) {
                TileEntity tileEntity = TileEntity.create(null, tileEntityTag);
                if (tileEntity != null) {
                    tileEntity.setPos(BlockPos.ORIGIN);
                    return tileEntity;
                }
            }

            TileEntity tileEntity = state.getBlock().createTileEntity(null, state);
            if (tileEntity == null) return null;

            tileEntity.readFromNBT(tileEntityTag);
            tileEntity.setPos(BlockPos.ORIGIN);
            return tileEntity;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class DisplayBlockWorld extends World {
        @Nullable
        private IBlockState state;

        @Nullable
        private TileEntity tileEntity;

        private DisplayBlockWorld() {
            super(
                new DisplaySaveHandler(),
                new WorldInfo(DISPLAY_WORLD_SETTINGS, "StructureDisplayWorld"),
                new WorldProviderSurface(),
                new Profiler(),
                true
            );

            this.provider.setDimension(Integer.MAX_VALUE - 2048);
            int providerDimension = this.provider.getDimension();
            this.provider.setWorld(this);
            this.provider.setDimension(providerDimension);
            this.chunkProvider = createChunkProvider();
            this.getWorldBorder().setSize(30000000);
        }

        @Override
        protected void initCapabilities() {
            // The parser only needs a world shell for drop queries.
        }

        private void setDisplayBlock(IBlockState state, @Nullable TileEntity tileEntity) {
            this.state = state;
            this.tileEntity = tileEntity;

            if (tileEntity == null) return;

            tileEntity.setWorld(this);
            tileEntity.setPos(BlockPos.ORIGIN);
        }

        private void clearDisplayBlock() {
            state = null;
            tileEntity = null;
        }

        @Nonnull
        @Override
        protected IChunkProvider createChunkProvider() {
            return new DisplayChunkProvider(this);
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return true;
        }

        @Nullable
        @Override
        public TileEntity getTileEntity(@Nonnull BlockPos pos) {
            if (!BlockPos.ORIGIN.equals(pos)) return null;

            return tileEntity;
        }

        @Override
        public int getCombinedLight(@Nonnull BlockPos pos, int lightValue) {
            return 15 << 20 | 15 << 4;
        }

        @Nonnull
        @Override
        public IBlockState getBlockState(@Nonnull BlockPos pos) {
            if (!BlockPos.ORIGIN.equals(pos) || state == null) return Blocks.AIR.getDefaultState();

            return state;
        }

        @Override
        public boolean isAirBlock(@Nonnull BlockPos pos) {
            return getBlockState(pos).getBlock() == Blocks.AIR;
        }

        @Override
        public int getStrongPower(@Nonnull BlockPos pos, @Nonnull EnumFacing direction) {
            return 0;
        }

        @Nonnull
        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(@Nonnull BlockPos pos, @Nonnull EnumFacing side, boolean _default) {
            IBlockState blockState = getBlockState(pos);
            return blockState.isSideSolid(this, pos, side);
        }
    }

    private static final class DisplayChunkProvider implements IChunkProvider {
        private final World world;

        private DisplayChunkProvider(World world) {
            this.world = world;
        }

        @Nonnull
        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return provideChunk(x, z);
        }

        @Nonnull
        @Override
        public Chunk provideChunk(int x, int z) {
            return new Chunk(world, x, z);
        }

        @Override
        public boolean tick() {
            return false;
        }

        @Nonnull
        @Override
        public String makeString() {
            return "DisplayChunkProvider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    private static final class DisplaySaveHandler implements ISaveHandler, IPlayerFileData, IChunkLoader {

        @Override
        public WorldInfo loadWorldInfo() {
            return null;
        }

        @Override
        public void checkSessionLock() {
        }

        @Nonnull
        @Override
        public IChunkLoader getChunkLoader(@Nonnull WorldProvider provider) {
            return this;
        }

        @Nonnull
        @Override
        public IPlayerFileData getPlayerNBTManager() {
            return this;
        }

        @Nonnull
        @Override
        public TemplateManager getStructureTemplateManager() {
            return new TemplateManager("", new DataFixer(0));
        }

        @Override
        public void saveWorldInfoWithPlayer(@Nonnull WorldInfo worldInformation, @Nonnull NBTTagCompound tagCompound) {
        }

        @Override
        public void saveWorldInfo(@Nonnull WorldInfo worldInformation) {
        }

        @Override
        public File getWorldDirectory() {
            return null;
        }

        @Override
        public File getMapFileFromName(@Nonnull String mapName) {
            return null;
        }

        @Override
        public Chunk loadChunk(@Nonnull World worldIn, int x, int z) {
            return null;
        }

        @Override
        public void saveChunk(@Nonnull World worldIn, @Nonnull Chunk chunkIn) {
        }

        @Override
        public void saveExtraChunkData(@Nonnull World worldIn, @Nonnull Chunk chunkIn) {
        }

        @Override
        public void chunkTick() {
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return false;
        }

        @Override
        public void writePlayerData(@Nonnull EntityPlayer player) {
        }

        @Override
        public NBTTagCompound readPlayerData(@Nonnull EntityPlayer player) {
            return null;
        }

        @Nonnull
        @Override
        public String[] getAvailablePlayerDat() {
            return new String[0];
        }
    }
}
