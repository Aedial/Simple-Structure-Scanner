package com.simplestructurescanner.structure;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.FluidStack;


/**
 * Contains information about a structure.
 */
public class StructureInfo {
    private final ResourceLocation id;
    private final LocalizedText displayName;
    private final String modId;
    private int sizeX;
    private int sizeY;
    private int sizeZ;

    private List<BlockEntry> blocks;
    private List<LootEntry> lootTables;
    private List<EntityEntry> entities;

    // Biome/dimension/rarity info
    private Set<Biome> validBiomes;
    // null means unrestricted, empty means unknown/not applicable, non-empty is an allow-list.
    private Set<DimensionInfo> validDimensions;
    private LocalizedText rarity;

    // Layer data for structure viewer (Y-level indexed)
    private List<StructureLayer> layers;

    public StructureInfo(ResourceLocation id, LocalizedText displayName, String modId, int sizeX, int sizeY, int sizeZ) {
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

    public LocalizedText getDisplayName() {
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
    public Set<DimensionInfo> getValidDimensions() {
        return validDimensions;
    }

    public void setValidDimensions(Set<DimensionInfo> validDimensions) {
        this.validDimensions = validDimensions;
    }

    /**
     * Check if this structure can generate in the given dimension.
     * If dimension metadata is absent, returns true (allowed in all dimensions).
     * If dimension metadata is explicitly unknown, returns false.
     *
     * @param dimensionId The dimension ID to check
     * @return true if the structure can generate in this dimension
     */
    public boolean isValidForDimension(int dimensionId) {
        if (StructureSearchOverrides.isStructureHiddenInDimension(modId, id, dimensionId)) return false;
        if (validDimensions == null) return true;
        if (validDimensions.isEmpty()) return false;

        for (DimensionInfo dim : validDimensions) {
            if (dim.getDimensionId() == dimensionId) return true;
        }

        return false;
    }

    @Nullable
    public LocalizedText getRarity() {
        return rarity;
    }

    public void setRarity(LocalizedText rarity) {
        this.rarity = rarity;
    }

    public void setRarityKey(String rarityKey) {
        if (rarityKey == null || rarityKey.isEmpty()) {
            rarity = null;
            return;
        }

        rarity = LocalizedText.translatable("gui.structurescanner.rarity",
            LocalizedText.translatable(rarityKey));
    }

    @Nullable
    public List<StructureLayer> getLayers() {
        return layers;
    }

    /**
     * Set the layer data for the structure viewer.
     * Automatically calculates sizeX, sizeY, and sizeZ based on the layers.
     *
     * @param layers List of structure layers (Y-level indexed)
     */
    public void setLayers(List<StructureLayer> layers) {
        this.layers = layers;

        if (layers == null || layers.isEmpty()) return;

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (StructureLayer layer : layers) {
            if (layer == null) continue;

            if (layer.y < minY) minY = layer.y;
            if (layer.y > maxY) maxY = layer.y;

            if (layer.width <= 0 || layer.depth <= 0) continue;

            if (layer.xOffset < minX) minX = layer.xOffset;
            if (layer.zOffset < minZ) minZ = layer.zOffset;

            int layerMaxX = layer.xOffset + layer.width - 1;
            int layerMaxZ = layer.zOffset + layer.depth - 1;
            if (layerMaxX > maxX) maxX = layerMaxX;
            if (layerMaxZ > maxZ) maxZ = layerMaxZ;
        }

        if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE) return;

        this.sizeY = maxY - minY + 1;
        this.sizeX = minX <= maxX ? maxX - minX + 1 : 0;
        this.sizeZ = minZ <= maxZ ? maxZ - minZ + 1 : 0;
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
        public final int xOffset;
        public final int zOffset;

        public StructureLayer(int y, int width, int depth, int xOffset, int zOffset) {
            this.y = y;
            this.width = width;

            this.depth = depth;
            this.xOffset = xOffset;
            this.zOffset = zOffset;
            this.blockStates = new IBlockState[width * depth];
        }

        public StructureLayer(int y, int width, int depth) {
            this(y, width, depth, 0, 0);
        }

        public void setBlockState(int x, int z, IBlockState state) {
            if (x >= 0 && x < width && z >= 0 && z < depth) blockStates[x + z * width] = state;
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
        @Nullable
        public final ItemStack displayStack;
        @Nullable
        public final FluidStack displayFluid;
        public final int count;

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, int count) {
            this(blockState, displayStack, null, count);
        }

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, @Nullable FluidStack displayFluid, int count) {
            this.blockState = blockState;
            this.displayStack = displayStack != null && !displayStack.isEmpty() ? displayStack.copy() : null;
            this.displayFluid = displayFluid != null ? displayFluid.copy() : null;
            this.count = count;
        }

        public BlockEntry withCount(int newCount) {
            return new BlockEntry(blockState, displayStack, displayFluid, newCount);
        }

        public String formatCount() {
            if (count >= 1000) return String.format("%.1f%s", count / 1000.0, I18n.format("gui.structurescanner.k"));

            return String.valueOf(count);
        }
    }

    /**
     * Represents one loot source entry.
     * A null lootTableId means the entry is not backed by a vanilla loot table.
     * Use kind to distinguish fixed inventories from generated loot sources.
     */
    public enum LootEntryKind {
        LOOT_TABLE,
        FIXED_ITEMS,
        GENERATED_ITEMS,
    }

    public static class LootEntry {
        @Nullable
        public final ResourceLocation lootTableId;
        public final List<ItemStack> possibleDrops;
        public final LocalizedText containerType;
        public final LootEntryKind kind;
        @Nullable
        public final LocalizedText sourceName;
        @Nullable
        public final ItemStack sourceStack;

        public LootEntry(@Nullable ResourceLocation lootTableId, List<ItemStack> possibleDrops,
                LocalizedText containerType) {
            this(lootTableId, possibleDrops, containerType,
                lootTableId != null ? LootEntryKind.LOOT_TABLE : LootEntryKind.FIXED_ITEMS,
                null, null);
        }

        public LootEntry(@Nullable ResourceLocation lootTableId, List<ItemStack> possibleDrops,
                LocalizedText containerType, LootEntryKind kind) {
            this(lootTableId, possibleDrops, containerType, kind, null, null);
        }

        public LootEntry(@Nullable ResourceLocation lootTableId, List<ItemStack> possibleDrops,
                LocalizedText containerType, LootEntryKind kind,
                @Nullable LocalizedText sourceName, @Nullable ItemStack sourceStack) {
            this.lootTableId = lootTableId;
            this.possibleDrops = possibleDrops;
            this.containerType = containerType;
            this.kind = kind;
            this.sourceName = sourceName;
            this.sourceStack = sourceStack != null && !sourceStack.isEmpty() ? sourceStack.copy() : null;
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
