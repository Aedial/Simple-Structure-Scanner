package com.simplestructurescanner.structure;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

import com.simplestructurescanner.Tags;
import com.simplestructurescanner.config.SimpleStructureScannerConfig;
import com.simplestructurescanner.structure.generation.MapGenerationBuilder;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.StructureContentAccumulator;
import com.simplestructurescanner.structure.providers.AbstractStructureProvider.Rarity;


/**
 * Contains information about a structure.
 */
public class StructureInfo {
    private static final String STRUCTURE_OVERRIDE_DIRECTORY = "structures";

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
    private Rarity rarityValue;
    private LocalizedText rarity;

    private PreviewSnapshot previewSnapshot;
    private boolean contentSourceSelected;
    private boolean configOverrideLoaded;
    private boolean mapContentsLoaded;

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
        this.contentSourceSelected = false;
        this.configOverrideLoaded = false;
        this.mapContentsLoaded = false;
    }

    public StructureInfo(ResourceLocation id, LocalizedText displayName, String providerId) {
        this(id, displayName, providerId, 0, 0, 0);
    }

    public ResourceLocation getId() {
        return id;
    }

    public LocalizedText getDisplayName() {
        return displayName;
    }

    public String getProviderId() {
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

    /**
     * Sets the size of this structure. This should realistically never be called directly,
     * as the size is typically determined by the structure's actual content.
     */
    public StructureInfo withSize(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = Math.max(sizeX, 0);
        this.sizeY = Math.max(sizeY, 0);
        this.sizeZ = Math.max(sizeZ, 0);

        return this;
    }

    public List<BlockEntry> getBlocks() {
        return blocks;
    }

    /**
     * Sets the blocks for this structure.
     * Do not use this method directly; prefer using {@link #withBlocks(List)} instead.
     */
    public void setBlocks(List<BlockEntry> blocks) {
        this.blocks = blocks != null ? blocks : Collections.emptyList();
    }
    
    /**
     * Adds the provided blocks for this structure if no config override was loaded.
     * If you are using a {@link MapGenerationBuilder}, prefer using {@link #withBlocksForMap} instead.
     */
    public StructureInfo withBlocks(BlockEntry... blocks) {
        return withBlocks(blocks != null ? Arrays.asList(blocks) : null);
    }

    /**
     * Adds the provided blocks for this structure if no config override was loaded.
     * If you are using a {@link MapGenerationBuilder}, prefer using {@link #withBlocksForMap} instead.
     */
    public StructureInfo withBlocks(@Nullable List<BlockEntry> blocks) {
        if (configOverrideLoaded || blocks == null || blocks.isEmpty()) return this;

        StructureContentAccumulator contents = new StructureContentAccumulator();
        for (BlockEntry block : this.blocks) contents.addBlock(block);
        for (BlockEntry block : blocks) contents.addBlock(block);
        setBlocks(contents.buildBlocks());

        return this;
    }

    /**
     * Adds the provided blocks to this structure if no config override was loaded
     * and the map was generated without crashing.
     */
    public StructureInfo withBlocksForMap(BlockEntry... blocks) {
        if (!mapContentsLoaded) return this;

        return withBlocks(blocks);
    }

    /**
     * Sets the blocks for this structure if no config override was loaded
     * and the current block list is empty.
     * This may be the case if there was no bundled NBT file for this structure.
     */
    public StructureInfo withFallbackBlocks(@Nullable List<BlockEntry> blocks) {
        if (configOverrideLoaded || !this.blocks.isEmpty()) return this;

        setBlocks(blocks);

        return this;
    }

    public List<LootEntry> getLootTables() {
        return lootTables;
    }

    /**
     * Sets the loot tables for this structure.
     * Do not use this method directly; use the {@link #withLootTables(LootEntry...)}
     * or {@link #withLootTables(List)} methods instead.
     */
    public void setLootTables(List<LootEntry> lootTables) {
        this.lootTables = lootTables != null ? lootTables : Collections.emptyList();
    }

    /**
     * Add the provided loot tables to this structure if no config override was loaded.
     */
    public StructureInfo withLootTables(LootEntry... lootTables) {
        return withLootTables(lootTables != null ? Arrays.asList(lootTables) : null);
    }

    /**
     * Add the provided loot tables to this structure if no config override was loaded.
     */
    public StructureInfo withLootTables(@Nullable List<LootEntry> lootTables) {
        if (configOverrideLoaded || lootTables == null || lootTables.isEmpty()) return this;

        StructureContentAccumulator contents = new StructureContentAccumulator();
        for (LootEntry lootTable : this.lootTables) contents.addLootEntry(lootTable);
        for (LootEntry lootTable : lootTables) contents.addLootEntry(lootTable);
        setLootTables(contents.buildLootEntries());

        return this;
    }

    /**
     * Add the provided loot tables to this structure if no config override was loaded
     * and the map contents was generated without crashing.
     */
    public StructureInfo withLootTablesForMap(LootEntry... lootTables) {
        if (!mapContentsLoaded) return this;

        return withLootTables(lootTables);
    }

    /**
     * Sets the loot tables for this structure if no config override was loaded
     * and the current loot tables for this structure are empty.
     * This may be the case if there is no bundled NBT file for this structure.
     */
    public StructureInfo withFallbackLootTables(LootEntry... lootTables) {
        return withFallbackLootTables(lootTables != null ? Arrays.asList(lootTables) : null);
    }

    /**
     * Sets the loot tables for this structure if no config override was loaded,
     * and the current loot tables for this structure are empty.
     * This may be the case if there is no bundled NBT file for this structure.
     */
    public StructureInfo withFallbackLootTables(@Nullable List<LootEntry> lootTables) {
        if (configOverrideLoaded || !this.lootTables.isEmpty()) return this;

        setLootTables(lootTables);

        return this;
    }

    public List<EntityEntry> getEntities() {
        return entities;
    }

    /**
     * Sets the entities for this structure.
     * Do not use this method directly; use the {@link #withEntities(EntityEntry...)}
     * or {@link #withEntities(List)} methods instead.
     */
    public void setEntities(List<EntityEntry> entities) {
        this.entities = entities != null ? entities : Collections.emptyList();
    }

    /**
     * Adds the provided entities to this structure if no config override was loaded.
     */
    public StructureInfo withEntities(EntityEntry... entities) {
        return withEntities(entities != null ? Arrays.asList(entities) : null);
    }

    /**
     * Adds the provided entities to this structure if no config override was loaded.
     */
    public StructureInfo withEntities(@Nullable List<EntityEntry> entities) {
        if (configOverrideLoaded || entities == null || entities.isEmpty()) return this;

        StructureContentAccumulator contents = new StructureContentAccumulator();
        for (EntityEntry entity : this.entities) contents.addEntity(entity);
        for (EntityEntry entity : entities) contents.addEntity(entity);
        setEntities(contents.buildEntities());

        return this;
    }

    /**
     * Adds the provided entities to this structure if no config override was loaded
     * and the map was generated without crashing.
     */
    public StructureInfo withEntitiesForMap(EntityEntry... entities) {
        if (!mapContentsLoaded) return this;

        return withEntities(entities);
    }

    /**
     * Sets the entities for this structure if no config override was loaded
     * and the current entity list is empty.
     * This may be the case if there was no bundled NBT file for this structure.
     */
    public StructureInfo withFallbackEntities(EntityEntry... entities) {
        return withFallbackEntities(entities != null ? Arrays.asList(entities) : null);
    }

    /**
     * Sets the entities for this structure if no config override was loaded
     * and the current entity list is empty.
     * This may be the case if there was no bundled NBT file for this structure.
     */
    public StructureInfo withFallbackEntities(@Nullable List<EntityEntry> entities) {
        if (configOverrideLoaded || !this.entities.isEmpty()) return this;

        setEntities(entities);

        return this;
    }

    @Nullable
    public Set<Biome> getValidBiomes() {
        return validBiomes;
    }

    /**
     * Sets the valid biomes for this structure.
     * Do not use this method directly; prefer using {@link #withMetadata(Set, Set, LocalizedText)} instead.
     */
    public void setValidBiomes(Set<Biome> validBiomes) {
        this.validBiomes = validBiomes;
    }

    @Nullable
    public Set<DimensionInfo> getValidDimensions() {
        return validDimensions;
    }

    /**
     * Sets the valid dimensions for this structure.
     * Do not use this method directly; prefer using {@link #withMetadata(Set, Set, LocalizedText)} instead.
     */
    public void setValidDimensions(Set<DimensionInfo> validDimensions) {
        this.validDimensions = validDimensions;
    }

    /**
     * Sets the metadata for this structure. The fields can be :
     * <ul>
     *   <li>{@code null}: Indicates that there are no restrictions for this field.</li>
     *   <li>{@code empty}: Indicates that the field is explicitly unknown.</li>
     * </ul>
     * <p>
     * NOTE: Empty rarity is invalid, as it requires a localization key.
     */
    public StructureInfo withMetadata(@Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, @Nullable LocalizedText rarity) {
        return withMetadata(biomes, dimensions).withRarity(rarity);
    }

    /**
     * Sets the metadata for this structure. The fields can be :
     * <ul>
     *   <li>{@code null}: Indicates that there are no restrictions for this field.</li>
     *   <li>{@code empty}: Indicates that the field is explicitly unknown.</li>
     * </ul>
     * <p>
     */
    public StructureInfo withMetadata(@Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, Rarity rarity) {
        return withMetadata(biomes, dimensions).withRarity(rarity);
    }

    /**
     * Sets the metadata for this structure. The fields can be null if unrestricted.
     * Chain with {@link #withRarity(LocalizedText)} if you also want to set the rarity.
     */
    public StructureInfo withMetadata(@Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions) {
        setValidBiomes(biomes);
        setValidDimensions(dimensions);

        return this;
    }

    /**
     * Sets the rarity for this structure. {@link #withMetadata(Set, Set, LocalizedText)} is preferred
     * if the rarity text is short enough, this method exists for readability only.
     */
    public StructureInfo withRarity(@Nullable LocalizedText rarity) {
        setRarity(rarity);

        return this;
    }

    public StructureInfo withRarity(Rarity rarity) {
        this.rarityValue = rarity;

        return withRarity(LocalizedText.translatable("gui.structurescanner.rarity", rarity.getKey()));
    }

    public StructureInfo oneInChunks(int chunks) {
        return withRarity(RarityTextHelper.oneInChunks(chunks));
    }

    /**
     * Check if this structure can generate in the given dimension.
     * If dimension metadata is absent (null), returns true (allowed in all dimensions).
     * If dimension metadata is explicitly unknown (empty), returns false.
     * This method checks against the structure's dimension metadata and any overrides
     * that may hide the structure in specific dimensions.
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

    @Nullable
    public Rarity getRarityValue() {
        return rarityValue;
    }

    /**
     * Sets the rarity for this structure.
     * Do not use this method directly; prefer using {@link #withRarity(LocalizedText)} instead.
     */
    public void setRarity(LocalizedText rarity) {
        this.rarity = rarity;
    }

    /**
     * Sets the rarity for this structure from a raw localization key.
     * Do not use this method directly; prefer using {@link #withRarity(LocalizedText)} instead.
     */
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
     * Sets the structure layers if no config override was loaded.
     * You should realistically never use this method directly;
     * prefer using {@link #fromLayersSupplier(Function)} instead.
     */
    public StructureInfo withLayers(@Nullable List<StructureLayer> layers) {
        if (!configOverrideLoaded) setLayers(layers);

        return this;
    }

    /**
     * Loads the bundled NBT file if no config override exists.
     * The bundled NBT file does not need to exist (it will simply be skipped if absent).
     */
    public StructureInfo fromBundled() {
        if (loadConfigOverride()) return this;

        StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseBundledStructure(
            Tags.MODID, providerId + "/" + id.getPath());
        if (parsed != null) applyContents(parsed);

        return this;
    }

    /**
     * Loads a parsed NBT structure if no config override exists.
     * Use this method if you need to parse the structure beforehand.
     */
    public StructureInfo fromParsedStructure(@Nullable StructureNBTParser.ParsedStructure parsed) {
        if (loadConfigOverride()) return this;

        if (parsed != null) applyContents(parsed);

        return this;
    }

    /**
     * Sets the structure from a parsed NBT structure if no config override was loaded.
     * You should realistically never use this method directly;
     * prefer using {@link #fromParsedStructure(StructureNBTParser.ParsedStructure)} instead.
     */
    public StructureInfo withParsedStructure(@Nullable StructureNBTParser.ParsedStructure parsed) {
        if (configOverrideLoaded || parsed == null) return this;

        applyContents(parsed);

        return this;
    }

    /**
     * Captures the completed map if no config override exists.
     * If the map generation crashed, the structure will not be applied
     * (resulting in an empty structure). Use fallback methods to provide alternative structure data.
     */
    public StructureInfo fromMap(MapGenerationBuilder map) {
        if (loadConfigOverride()) return this;

        applyMap(map);

        return this;
    }

    /**
     * Captures the completed map if no config override exists.
     * If the map generation crashed, it will fall back to the bundled NBT structure (if any).
     * The relevant bundled NBT file does not need to exist, but it would be better to use
     * {@link #fromMap(MapGenerationBuilder)} instead, if it is the case.
     */
    public StructureInfo fromMapWithBundledFallback(MapGenerationBuilder map) {
        if (loadConfigOverride()) return this;

        if (applyMap(map)) return this;

        StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseBundledStructure(
            Tags.MODID, providerId + "/" + id.getPath());
        if (parsed != null) applyContents(parsed);

        return this;
    }

    /**
     * Builds preview layers from the provided supplier if no config override exists.
     */
    public StructureInfo fromLayersSupplier(Function<StructureInfo, List<StructureLayer>> supplier) {
        if (loadConfigOverride()) return this;

        if (supplier == null) return this;

        List<StructureLayer> layers = supplier.apply(this);
        setLayers(layers);

        StructureContentAccumulator contents = new StructureContentAccumulator();
        contents.addContentsFromLayers(layers);
        contents.applyTo(this);

        return this;
    }

    /**
     * Builds content through a provider callback if no config override exists.
     */
    public StructureInfo fromContentsSupplier(Consumer<StructureInfo> supplier) {
        if (loadConfigOverride()) return this;

        if (supplier != null) supplier.accept(this);

        return this;
    }

    /**
     * Builds content from the provided supplier if no config override exists.
     */
    public StructureInfo fromContentsSupplier(Supplier<StructureContentAccumulator> supplier) {
        if (loadConfigOverride()) return this;

        if (supplier == null) return this;

        StructureContentAccumulator contents = supplier.get();
        if (contents != null) contents.applyTo(this);

        return this;
    }

    private boolean loadConfigOverride() {
        if (contentSourceSelected) {
            throw new IllegalStateException("Structure content source has already been selected: " + id);
        }

        contentSourceSelected = true;
        StructureNBTParser.ParsedStructure override = getConfigOverride();
        if (override == null) return false;

        configOverrideLoaded = true;
        applyContents(override);

        return true;
    }

    @Nullable
    private StructureNBTParser.ParsedStructure getConfigOverride() {
        File configRoot = SimpleStructureScannerConfig.getConfigRootDirectory();
        if (configRoot == null) return null;

        File providerDirectory = new File(new File(configRoot, STRUCTURE_OVERRIDE_DIRECTORY), providerId);
        File overrideFile = new File(providerDirectory, id.getPath() + ".nbt");
        if (!overrideFile.isFile()) return null;

        return StructureNBTParser.parseStructureFile(overrideFile);
    }

    private boolean applyMap(@Nullable MapGenerationBuilder map) {
        if (map == null || map.hasGenerationFailed()) return false;

        List<StructureLayer> layers = map.capture();
        if (map.hasGenerationFailed()) return false;

        setLayers(layers);
        StructureContentAccumulator contents = new StructureContentAccumulator();
        contents.addContentsFromLayers(layers);
        contents.addEntityData(map.getGeneratedEntityData());
        contents.applyTo(this);
        mapContentsLoaded = true;

        return true;
    }

    private void applyContents(StructureNBTParser.ParsedStructure parsed) {
        setBlocks(parsed.blocks);
        setLayers(parsed.layers);
        setEntities(parsed.entities);
        setLootTables(parsed.lootTables);
        withSize(parsed.sizeX, parsed.sizeY, parsed.sizeZ);
    }

    /**
     * Sets the structure layers.
     * You should realistically never use this method directly;
     * prefer using {@link #fromLayersSupplier(Function)} instead.
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

    /**
     * Creates a preview snapshot from the given structure layers.
     * Returns an empty snapshot if the layers are null or empty.
     * You should not need to call this method directly; it is used internally when setting structure layers.
     */
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

    /**
     * Builds preview layers from blocks placed at their local positions.
     */
    public static List<StructureLayer> createLayers(Map<BlockPos, IBlockState> blocks,
            @Nullable Map<BlockPos, NBTTagCompound> blockEntityDataByPos) {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos == null || entry.getValue() == null) continue;

            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (minX == Integer.MAX_VALUE) return Collections.emptyList();

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        Map<Integer, StructureLayer> layers = new TreeMap<>();

        for (Map.Entry<BlockPos, IBlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            IBlockState state = entry.getValue();
            if (pos == null || state == null) continue;

            StructureLayer layer = layers.get(pos.getY());
            if (layer == null) {
                layer = new StructureLayer(pos.getY(), width, depth, minX, minZ);
                layers.put(pos.getY(), layer);
            }

            NBTTagCompound blockEntityData = blockEntityDataByPos != null
                ? blockEntityDataByPos.get(pos)
                : null;
            layer.setBlockState(pos.getX() - minX, pos.getZ() - minZ, state, blockEntityData);
        }

        return new ArrayList<>(layers.values());
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

        public LootEntry(String lootTableId, String containerTypeKey) {
            this(new ResourceLocation(lootTableId), containerTypeKey);
        }

        public LootEntry(ResourceLocation lootTableId, String containerTypeKey) {
            this(lootTableId, Collections.emptyList(), LocalizedText.translatable(containerTypeKey));
        }

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

        public EntityEntry(String entityId, int count) {
            this(new ResourceLocation(entityId), count);
        }

        public EntityEntry(String entityId, int count, boolean spawner) {
            this(new ResourceLocation(entityId), count, spawner);
        }

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
