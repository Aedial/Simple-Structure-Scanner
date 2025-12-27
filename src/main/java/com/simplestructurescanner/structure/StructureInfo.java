package com.simplestructurescanner.structure;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;


/**
 * Contains information about a structure.
 */
public class StructureInfo {
    private final ResourceLocation id;
    private final String displayName;
    private final String modId;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    private List<BlockEntry> blocks;
    private List<LootEntry> lootTables;
    private List<EntityEntry> entities;

    public StructureInfo(ResourceLocation id, String displayName, String modId, int sizeX, int sizeY, int sizeZ) {
        this.id = id;
        this.displayName = displayName;
        this.modId = modId;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = Collections.emptyList();
        this.lootTables = Collections.emptyList();
        this.entities = Collections.emptyList();
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModId() {
        return modId;
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

    public List<BlockEntry> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockEntry> blocks) {
        this.blocks = blocks != null ? blocks : Collections.emptyList();
    }

    public List<LootEntry> getLootTables() {
        return lootTables;
    }

    public void setLootTables(List<LootEntry> lootTables) {
        this.lootTables = lootTables != null ? lootTables : Collections.emptyList();
    }

    public List<EntityEntry> getEntities() {
        return entities;
    }

    public void setEntities(List<EntityEntry> entities) {
        this.entities = entities != null ? entities : Collections.emptyList();
    }

    /**
     * Represents a block in the structure with its count.
     */
    public static class BlockEntry {
        public final IBlockState blockState;
        public final ItemStack displayStack;
        public final int count;

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, int count) {
            this.blockState = blockState;
            this.displayStack = displayStack;
            this.count = count;
        }

        public String formatCount() {
            if (count >= 1000) return String.format("%.1fk", count / 1000.0);

            return String.valueOf(count);
        }
    }

    /**
     * Represents a loot table entry with potential drops.
     */
    public static class LootEntry {
        public final ResourceLocation lootTableId;
        public final List<ItemStack> possibleDrops;
        public final String containerType;

        public LootEntry(ResourceLocation lootTableId, List<ItemStack> possibleDrops, String containerType) {
            this.lootTableId = lootTableId;
            this.possibleDrops = possibleDrops;
            this.containerType = containerType;
        }
    }

    /**
     * Represents an entity that spawns with the structure.
     */
    public static class EntityEntry {
        public final ResourceLocation entityId;
        public final String displayName;
        public final int count;
        public final boolean spawner;

        public EntityEntry(ResourceLocation entityId, String displayName, int count) {
            this(entityId, displayName, count, false);
        }

        public EntityEntry(ResourceLocation entityId, String displayName, int count, boolean spawner) {
            this.entityId = entityId;
            this.displayName = displayName;
            this.count = count;
            this.spawner = spawner;
        }
    }
}
