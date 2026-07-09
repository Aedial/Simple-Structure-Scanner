package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.FluidStack;


/**
 * Contains information about a structure.
 */
public class StructureInfo {
    private final ResourceLocation id;
    private final LocalizedText displayName;
    private final String providerId;
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

    private PreviewSnapshot previewSnapshot;

    public StructureInfo(ResourceLocation id, LocalizedText displayName, String providerId, int sizeX, int sizeY, int sizeZ) {
        this.id = id;
        this.displayName = displayName;
        this.providerId = providerId;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = Collections.emptyList();
        this.lootTables = Collections.emptyList();
        this.entities = Collections.emptyList();
        this.validBiomes = null;
        this.validDimensions = null;
        this.rarity = null;
        this.previewSnapshot = PreviewSnapshot.empty();
    }

    public ResourceLocation getId() {
        return id;
    }

    public LocalizedText getDisplayName() {
        return displayName;
    }

    public String getModId() {
        return providerId;
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
        if (StructureSearchOverrides.isStructureHiddenInDimension(providerId, id, dimensionId)) return false;
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

    public PreviewSnapshot getPreviewSnapshot() {
        return previewSnapshot;
    }

    /**
     * Set the layer data for the structure viewer.
     * Converts them into a flattened preview snapshot and derives the structure bounds.
     *
     * @param layers List of structure layers (Y-level indexed)
     */
    public void setLayers(List<StructureLayer> layers) {
        this.previewSnapshot = createPreviewSnapshot(layers);

        if (previewSnapshot.isEmpty()) return;

        this.sizeX = previewSnapshot.getMaxX() - previewSnapshot.getMinX() + 1;
        this.sizeY = previewSnapshot.getMaxY() - previewSnapshot.getMinY() + 1;
        this.sizeZ = previewSnapshot.getMaxZ() - previewSnapshot.getMinZ() + 1;
    }

    /**
     * Check if this structure has previewable block data for the structure viewer.
     */
    public boolean hasLayerData() {
        return !previewSnapshot.isEmpty();
    }

    public static PreviewSnapshot createPreviewSnapshot(@Nullable List<StructureLayer> layers) {
        if (layers == null || layers.isEmpty()) return PreviewSnapshot.empty();

        // Compute flattened preview blocks and their bounding box
        int minLayerY = Integer.MAX_VALUE;
        int maxLayerY = Integer.MIN_VALUE;

        for (StructureLayer layer : layers) {
            if (layer == null || layer.width <= 0 || layer.depth <= 0) continue;

            if (layer.y < minLayerY) minLayerY = layer.y;
            if (layer.y > maxLayerY) maxLayerY = layer.y;
        }

        if (minLayerY == Integer.MAX_VALUE) return PreviewSnapshot.empty();

        int yOffset = minLayerY < 0 ? -minLayerY : 0;
        List<PreviewBlockEntry> blocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        // Use the pre-computed layer data as the canonical vertical bounds
        // yOffset is applied to ensure all blocks are non-negative in Y for rendering purposes
        int minY = minLayerY + yOffset;
        int maxY = maxLayerY + yOffset;

        for (StructureLayer layer : layers) {
            if (layer == null || layer.width <= 0 || layer.depth <= 0) continue;

            for (int index = 0; index < layer.blockStates.length; index++) {
                IBlockState state = layer.blockStates[index];
                if (state == null || state.getBlock() == Blocks.AIR || state.getBlock() == Blocks.STRUCTURE_VOID) continue;

                int x = index % layer.width + layer.xOffset;
                int z = index / layer.width + layer.zOffset;
                int y = layer.y + yOffset;

                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);

                blocks.add(new PreviewBlockEntry(new BlockPos(x, y, z), state, layer.blockEntityData[index]));
            }
        }

        if (blocks.isEmpty()) return PreviewSnapshot.empty();

        return new PreviewSnapshot(blocks, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static class PreviewSnapshot {
        private static final PreviewSnapshot EMPTY = new PreviewSnapshot(Collections.emptyList(), 0, 0, 0, 0, 0, 0);

        private final List<PreviewBlockEntry> blocks;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private PreviewSnapshot(List<PreviewBlockEntry> blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.blocks = Collections.unmodifiableList(blocks);
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public static PreviewSnapshot empty() {
            return EMPTY;
        }

        public List<PreviewBlockEntry> getBlocks() {
            return blocks;
        }

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        public int getMinX() {
            return minX;
        }

        public int getMinY() {
            return minY;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxY() {
            return maxY;
        }

        public int getMaxZ() {
            return maxZ;
        }
    }

    public static class PreviewBlockEntry {
        public final BlockPos pos;
        public final IBlockState state;
        @Nullable
        public final NBTTagCompound blockEntityData;

        private PreviewBlockEntry(BlockPos pos, IBlockState state, @Nullable NBTTagCompound blockEntityData) {
            this.pos = pos;
            this.state = state;
            this.blockEntityData = blockEntityData != null && !blockEntityData.isEmpty() ? blockEntityData.copy() : null;
        }
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
        private final NBTTagCompound[] blockEntityData;

        public StructureLayer(int y, int width, int depth, int xOffset, int zOffset) {
            this.y = y;
            this.width = width;

            this.depth = depth;
            this.xOffset = xOffset;
            this.zOffset = zOffset;
            this.blockStates = new IBlockState[width * depth];
            this.blockEntityData = new NBTTagCompound[width * depth];
        }

        public StructureLayer(int y, int width, int depth) {
            this(y, width, depth, 0, 0);
        }

        public void setBlockState(int x, int z, IBlockState state) {
            setBlockState(x, z, state, null);
        }

        public void setBlockState(int x, int z, IBlockState state, @Nullable NBTTagCompound tileEntityData) {
            if (x < 0 || x >= width || z < 0 || z >= depth) return;

            int index = x + z * width;
            blockStates[index] = state;
            blockEntityData[index] = tileEntityData != null && !tileEntityData.isEmpty() ? tileEntityData.copy() : null;
        }

        @Nullable
        public IBlockState getBlockState(int x, int z) {
            if (x < 0 || x >= width || z < 0 || z >= depth) return null;

            return blockStates[x + z * width];
        }

        @Nullable
        public NBTTagCompound getBlockEntityData(int x, int z) {
            if (x < 0 || x >= width || z < 0 || z >= depth) return null;

            NBTTagCompound tileEntityData = blockEntityData[x + z * width];
            return tileEntityData != null ? tileEntityData.copy() : null;
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
        @Nullable
        public final NBTTagCompound blockEntityData;
        public final int count;

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, int count) {
            this(blockState, displayStack, null, null, count);
        }

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, @Nullable FluidStack displayFluid, int count) {
            this(blockState, displayStack, displayFluid, null, count);
        }

        public BlockEntry(IBlockState blockState, @Nullable ItemStack displayStack, @Nullable FluidStack displayFluid,
                @Nullable NBTTagCompound blockEntityData, int count) {
            this.blockState = blockState;
            this.displayStack = displayStack != null && !displayStack.isEmpty() ? displayStack.copy() : null;
            this.displayFluid = displayFluid != null ? displayFluid.copy() : null;
            this.blockEntityData = blockEntityData != null && !blockEntityData.isEmpty() ? blockEntityData.copy() : null;
            this.count = count;
        }

        public BlockEntry withCount(int newCount) {
            return new BlockEntry(blockState, displayStack, displayFluid, blockEntityData, newCount);
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
