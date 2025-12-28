package com.simplestructurescanner.structure;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;


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

    // Biome/dimension/rarity info
    private Set<Biome> validBiomes;
    private Set<Integer> validDimensions;
    private String rarity;

    // Layer data for structure viewer (Y-level indexed)
    private List<StructureLayer> layers;

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
        this.validBiomes = null;
        this.validDimensions = null;
        this.rarity = null;
        this.layers = null;
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

    @Nullable
    public Set<Biome> getValidBiomes() {
        return validBiomes;
    }

    public void setValidBiomes(Set<Biome> validBiomes) {
        this.validBiomes = validBiomes;
    }

    @Nullable
    public Set<Integer> getValidDimensions() {
        return validDimensions;
    }

    public void setValidDimensions(Set<Integer> validDimensions) {
        this.validDimensions = validDimensions;
    }

    @Nullable
    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    @Nullable
    public List<StructureLayer> getLayers() {
        return layers;
    }

    public void setLayers(List<StructureLayer> layers) {
        this.layers = layers;
    }

    /**
     * Check if this structure has layer data for the structure viewer.
     */
    public boolean hasLayerData() {
        return layers != null && !layers.isEmpty();
    }

    /**
     * Represents a single Y-level layer of the structure.
     * Contains a 2D grid of block states for rendering.
     */
    public static class StructureLayer {
        public final int y;
        public final int width;
        public final int depth;
        public final IBlockState[] blockStates;

        public StructureLayer(int y, int width, int depth) {
            this.y = y;
            this.width = width;
            this.depth = depth;
            this.blockStates = new IBlockState[width * depth];
        }

        public void setBlockState(int x, int z, IBlockState state) {
            if (x >= 0 && x < width && z >= 0 && z < depth) {
                blockStates[x + z * width] = state;
            }
        }

        @Nullable
        public IBlockState getBlockState(int x, int z) {
            if (x < 0 || x >= width || z < 0 || z >= depth) return null;

            return blockStates[x + z * width];
        }
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
        public final int count;
        public final boolean spawner;

        public EntityEntry(ResourceLocation entityId, int count) {
            this(entityId, count, false);
        }

        public EntityEntry(ResourceLocation entityId, int count, boolean spawner) {
            this.entityId = entityId;
            this.count = count;
            this.spawner = spawner;
        }
    }
}
