package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Applies parsed NBT snapshots onto {@link StructureInfo} instances.
 * The helper keeps the merge rules in one place so providers can reuse the same block,
 * layer, entity, and loot handling without copy-pasting that bookkeeping.
 */
public final class ParsedStructureApplier {

    private ParsedStructureApplier() {
    }

    public static void apply(StructureInfo info, StructureNBTParser.ParsedStructure parsed) {
        if (!parsed.blocks.isEmpty()) info.setBlocks(parsed.blocks);
        if (!parsed.layers.isEmpty()) info.setLayers(parsed.layers);
        if (!parsed.entities.isEmpty()) info.setEntities(parsed.entities);
        if (!parsed.lootTables.isEmpty()) info.setLootTables(parsed.lootTables);
    }

    public static void merge(StructureInfo info, StructureNBTParser.ParsedStructure parsed) {
        merge(info, parsed, 0, 0, true);
    }

    public static void merge(StructureInfo info, StructureNBTParser.ParsedStructure parsed,
            int xOffset, int zOffset, boolean mergeLootTables) {
        if (!parsed.blocks.isEmpty()) mergeBlocks(info, parsed.blocks);
        if (!parsed.layers.isEmpty()) mergeLayers(info, parsed.layers, xOffset, zOffset);
        if (!parsed.entities.isEmpty()) mergeEntities(info, parsed.entities);
        if (mergeLootTables && !parsed.lootTables.isEmpty()) mergeLootTables(info, parsed.lootTables);
    }

    private static void mergeBlocks(StructureInfo info, List<BlockEntry> newBlocks) {
        if (info.getBlocks().isEmpty()) {
            info.setBlocks(newBlocks);
            return;
        }

        Map<BlockKey, BlockEntry> mergedBlocks = new LinkedHashMap<>();

        for (BlockEntry entry : info.getBlocks()) {
            mergedBlocks.put(new BlockKey(entry.blockState), entry);
        }

        for (BlockEntry entry : newBlocks) {
            BlockKey key = new BlockKey(entry.blockState);
            BlockEntry existing = mergedBlocks.get(key);

            if (existing == null) {
                mergedBlocks.put(key, entry);
                continue;
            }

            mergedBlocks.put(key, existing.withCount(existing.count + entry.count));
        }

        List<BlockEntry> merged = new ArrayList<>(mergedBlocks.values());
        merged.sort((a, b) -> Integer.compare(b.count, a.count));
        info.setBlocks(merged);
    }

    private static void mergeLayers(StructureInfo info, List<StructureLayer> newLayers, int xOffset, int zOffset) {
        if (!info.hasLayerData() && xOffset == 0 && zOffset == 0) {
            info.setLayers(newLayers);
            return;
        }

        List<StructureLayer> mergedLayers = info.hasLayerData()
            ? new ArrayList<>(info.getLayers())
            : new ArrayList<>();

        int maxExistingY = Integer.MIN_VALUE;
        for (StructureLayer layer : mergedLayers) {
            if (layer.y > maxExistingY) maxExistingY = layer.y;
        }

        int minNewY = Integer.MAX_VALUE;
        for (StructureLayer layer : newLayers) {
            if (layer.y < minNewY) minNewY = layer.y;
        }

        int yOffset = maxExistingY == Integer.MIN_VALUE ? 0 : maxExistingY - minNewY + 1;

        for (StructureLayer newLayer : newLayers) {
            StructureLayer offsetLayer = new StructureLayer(
                newLayer.y + yOffset, newLayer.width, newLayer.depth, xOffset, zOffset);

            for (int x = 0; x < newLayer.width; x++) {
                for (int z = 0; z < newLayer.depth; z++) {
                    IBlockState state = newLayer.getBlockState(x, z);
                    if (state != null) offsetLayer.setBlockState(x, z, state);
                }
            }

            mergedLayers.add(offsetLayer);
        }

        mergedLayers.sort((a, b) -> Integer.compare(a.y, b.y));
        info.setLayers(mergedLayers);
    }

    private static void mergeEntities(StructureInfo info, List<EntityEntry> newEntities) {
        if (info.getEntities().isEmpty()) {
            info.setEntities(newEntities);
            return;
        }

        Map<EntityKey, EntityEntry> mergedEntities = new LinkedHashMap<>();

        for (EntityEntry entry : info.getEntities()) {
            mergedEntities.put(new EntityKey(entry), entry);
        }

        for (EntityEntry entry : newEntities) {
            EntityKey key = new EntityKey(entry);
            EntityEntry existing = mergedEntities.get(key);

            if (existing == null) {
                mergedEntities.put(key, entry);
                continue;
            }

            mergedEntities.put(key, new EntityEntry(entry.entityId, existing.count + entry.count, entry.spawner));
        }

        info.setEntities(new ArrayList<>(mergedEntities.values()));
    }

    private static void mergeLootTables(StructureInfo info, List<LootEntry> newLootEntries) {
        if (info.getLootTables().isEmpty()) {
            info.setLootTables(newLootEntries);
            return;
        }

        Map<LootKey, LootEntry> mergedLoot = new LinkedHashMap<>();

        for (LootEntry entry : info.getLootTables()) {
            mergedLoot.put(new LootKey(entry), entry);
        }

        for (LootEntry entry : newLootEntries) {
            mergedLoot.putIfAbsent(new LootKey(entry), entry);
        }

        info.setLootTables(new ArrayList<>(mergedLoot.values()));
    }

    private static final class BlockKey {
        @Nullable
        private final ResourceLocation blockId;
        private final int metadata;

        private BlockKey(IBlockState state) {
            this.blockId = state.getBlock().getRegistryName();
            this.metadata = state.getBlock().getMetaFromState(state);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BlockKey)) return false;

            BlockKey that = (BlockKey) obj;
            return metadata == that.metadata && Objects.equals(blockId, that.blockId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockId, metadata);
        }
    }

    private static final class EntityKey {
        private final ResourceLocation entityId;
        private final boolean spawner;

        private EntityKey(EntityEntry entry) {
            this.entityId = entry.entityId;
            this.spawner = entry.spawner;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntityKey)) return false;

            EntityKey that = (EntityKey) obj;
            return spawner == that.spawner && entityId.equals(that.entityId);
        }

        @Override
        public int hashCode() {
            return 31 * entityId.hashCode() + Boolean.hashCode(spawner);
        }
    }

    private static final class LootKey {
        private final ResourceLocation lootTableId;
        private final String containerValue;
        private final boolean translatable;

        private LootKey(LootEntry entry) {
            this.lootTableId = entry.lootTableId;
            this.containerValue = entry.containerType.getValue();
            this.translatable = entry.containerType.isTranslatable();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LootKey)) return false;

            LootKey that = (LootKey) obj;
            return translatable == that.translatable
                && lootTableId.equals(that.lootTableId)
                && containerValue.equals(that.containerValue);
        }

        @Override
        public int hashCode() {
            int result = lootTableId.hashCode();
            result = 31 * result + containerValue.hashCode();
            return 31 * result + Boolean.hashCode(translatable);
        }
    }
}