package com.simplestructurescanner.structure.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;


public class StructureContentAccumulator implements StructureNBTParser.StructureContentSink {

    private final Map<Object, BlockAggregate> blocks = new LinkedHashMap<>();
    private final Map<String, EntityAggregate> entities = new LinkedHashMap<>();
    private final Map<String, LootEntry> lootEntries = new LinkedHashMap<>();

    public void add(StructureNBTParser.ParsedStructure parsed) {
        if (parsed == null) return;

        for (BlockEntry blockEntry : parsed.blocks) addBlock(blockEntry);
        for (EntityEntry entityEntry : parsed.entities) addEntity(entityEntry);
        for (LootEntry lootEntry : parsed.lootTables) addLootEntry(lootEntry);
    }

    @Override
    public void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState) {
        addBlockCount(key, representativeState, null);
    }

    @Override
    public void addBlockCount(@Nullable Object key, @Nullable IBlockState representativeState,
            @Nullable NBTTagCompound blockEntityData) {
        if (key == null || representativeState == null) return;

        addBlock(key, StructureNBTParser.createBlockEntry(representativeState, blockEntityData, 1));
    }

    public void addBlock(@Nullable BlockEntry blockEntry) {
        if (blockEntry == null || blockEntry.blockState == null || blockEntry.count <= 0) return;

        Object key = StructureNBTParser.createDisplayedBlockKey(
            blockEntry.blockState, blockEntry.displayFluid, blockEntry.displayStack);
        addBlock(key, blockEntry);
    }

    private void addBlock(@Nullable Object key, @Nullable BlockEntry blockEntry) {
        if (key == null || blockEntry == null || blockEntry.blockState == null || blockEntry.count <= 0) return;

        BlockAggregate aggregate = blocks.get(key);

        if (aggregate == null) {
            blocks.put(key, new BlockAggregate(blockEntry));
            return;
        }

        aggregate.add(blockEntry);
    }

    @Override
    public void addEntity(ResourceLocation entityId, boolean spawner) {
        if (entityId == null) return;

        addEntity(new EntityEntry(entityId, 1, spawner));
    }

    public void addEntity(@Nullable EntityEntry entityEntry) {
        if (entityEntry == null || entityEntry.entityId == null || entityEntry.count <= 0) return;

        String key = entityEntry.entityId.toString() + '|' + entityEntry.spawner;
        EntityAggregate aggregate = entities.get(key);
        if (aggregate == null) {
            entities.put(key, new EntityAggregate(entityEntry));
            return;
        }

        aggregate.count += entityEntry.count;
    }

    @Override
    public void addLootTable(ResourceLocation lootTableId) {
        if (lootTableId == null) return;

        addLootEntry(createLootTableEntry(lootTableId));
    }

    @Override
    public void addLootEntry(@Nullable LootEntry lootEntry) {
        if (lootEntry == null) return;

        lootEntries.putIfAbsent(createLootEntryKey(lootEntry), lootEntry);
    }

    public List<BlockEntry> buildBlocks() {
        List<BlockEntry> results = new ArrayList<>();
        for (BlockAggregate aggregate : blocks.values()) {
            BlockEntry representative = aggregate.representative;
            results.add(new BlockEntry(
                representative.blockState,
                representative.displayStack,
                representative.displayFluid,
                representative.blockEntityData,
                aggregate.count
            ));
        }

        results.sort((first, second) -> Integer.compare(second.count, first.count));
        return results;
    }

    public List<EntityEntry> buildEntities() {
        List<EntityEntry> results = new ArrayList<>();
        for (EntityAggregate aggregate : entities.values()) {
            results.add(new EntityEntry(aggregate.entityId, aggregate.count, aggregate.spawner));
        }

        results.sort((first, second) -> Integer.compare(second.count, first.count));
        return results;
    }

    public List<LootEntry> buildLootEntries() {
        return new ArrayList<>(lootEntries.values());
    }

    public void applyTo(StructureInfo info) {
        info.setBlocks(buildBlocks());
        info.setEntities(buildEntities());
        info.setLootTables(buildLootEntries());
    }

    public void fromPreviewData(GeneratedPreviewData previewData) {
        if (previewData == null) return;

        for (BlockEntry blockEntry : previewData.blocks) addBlock(blockEntry);
        for (EntityEntry entityEntry : previewData.entities) addEntity(entityEntry);
        for (LootEntry lootEntry : previewData.lootEntries) addLootEntry(lootEntry);
    }

    private static LootEntry createLootTableEntry(ResourceLocation lootTableId) {
        return new LootEntry(lootTableId, new ArrayList<>(),
            LocalizedText.translatable("gui.structurescanner.loot.chest"));
    }

    private static String createLootEntryKey(LootEntry lootEntry) {
        StringBuilder key = new StringBuilder();
        key.append(lootEntry.lootTableId != null ? lootEntry.lootTableId.toString() : "<direct>");
        key.append('|').append(lootEntry.kind.name());
        key.append('|').append(createLocalizedTextKey(lootEntry.containerType));

        if (lootEntry.sourceName != null) key.append('|').append(createLocalizedTextKey(lootEntry.sourceName));
        if (lootEntry.sourceStack != null) key.append('|').append(createItemStackKey(lootEntry.sourceStack));

        if (lootEntry.possibleDrops != null) {
            for (ItemStack stack : lootEntry.possibleDrops) {
                key.append('|').append(createItemStackKey(stack)).append('*').append(stack.getCount());
            }
        }

        return key.toString();
    }

    private static String createLocalizedTextKey(@Nullable LocalizedText text) {
        if (text == null) return "<null>";
        return text.isTranslatable() + ":" + text.getValue();
    }

    private static String createItemStackKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";

        NBTTagCompound normalizedStack = stack.copy().writeToNBT(new NBTTagCompound());
        normalizedStack.removeTag("Count");
        return normalizedStack.toString();
    }

    private static final class BlockAggregate {
        private BlockEntry representative;
        private int count;

        private BlockAggregate(BlockEntry representative) {
            this.representative = representative;
            this.count = representative.count;
        }

        private void add(BlockEntry blockEntry) {
            count += blockEntry.count;
            preserveBlockEntityData(blockEntry.blockEntityData);
        }

        private void preserveBlockEntityData(@Nullable NBTTagCompound blockEntityData) {
            if (representative.blockEntityData != null || blockEntityData == null || blockEntityData.isEmpty()) return;

            representative = new BlockEntry(
                representative.blockState,
                representative.displayStack,
                representative.displayFluid,
                blockEntityData,
                representative.count
            );
        }
    }

    private static final class EntityAggregate {
        private final ResourceLocation entityId;
        private final boolean spawner;
        private int count;

        private EntityAggregate(EntityEntry entityEntry) {
            this.entityId = entityEntry.entityId;
            this.spawner = entityEntry.spawner;
            this.count = entityEntry.count;
        }
    }

    
    public static final class GeneratedPreviewData {
        private final List<BlockEntry> blocks;
        private final List<EntityEntry> entities;
        private final List<LootEntry> lootEntries;
        private final List<StructureInfo.StructureLayer> layers;

        public GeneratedPreviewData(List<BlockEntry> blocks, List<EntityEntry> entities,
                List<LootEntry> lootEntries, List<StructureInfo.StructureLayer> layers) {
            this.blocks = blocks;
            this.entities = entities;
            this.lootEntries = lootEntries;
            this.layers = layers;
        }

        public List<StructureInfo.StructureLayer> getLayers() {
            return layers;
        }
    }
}