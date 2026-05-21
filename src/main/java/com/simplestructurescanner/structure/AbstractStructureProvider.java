package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Shared provider scaffold for the common "catalog + metadata + static content" workflow.
 * Concrete providers still own their worldgen logic, but they do not need to duplicate
 * structure registration, metadata lookup, or simple loot/entity assignment helpers.
 */
public abstract class AbstractStructureProvider implements StructureProvider {

    private final String providerId;
    private final String structureNamespace;
    private final String modName;
    @Nullable
    private final String requiredModId;

    protected final List<ResourceLocation> knownStructures = new ArrayList<>();
    protected final Map<ResourceLocation, StructureInfo> structureInfos = new LinkedHashMap<>();

    protected AbstractStructureProvider(String providerId, String structureNamespace, String modName) {
        this(providerId, structureNamespace, modName, null);
    }

    protected AbstractStructureProvider(String providerId, String structureNamespace, String modName,
            @Nullable String requiredModId) {
        this.providerId = providerId;
        this.structureNamespace = structureNamespace;
        this.modName = modName;
        this.requiredModId = requiredModId;
    }

    @Override
    public final String getProviderId() {
        return providerId;
    }

    @Override
    public final String getModName() {
        return modName;
    }

    @Override
    public boolean isAvailable() {
        return requiredModId == null || Loader.isModLoaded(requiredModId);
    }

    @Override
    public List<ResourceLocation> getStructureIds() {
        return new ArrayList<>(knownStructures);
    }

    @Override
    @Nullable
    public StructureInfo getStructureInfo(ResourceLocation structureId) {
        return structureInfos.get(structureId);
    }

    /**
     * Providers rebuild their structure catalog during postInit and reloads, so the shared
     * collections need an explicit reset before repopulating them.
     */
    protected final void resetStructures() {
        knownStructures.clear();
        structureInfos.clear();
    }

    protected final ResourceLocation createStructureId(String path) {
        return new ResourceLocation(structureNamespace, path);
    }

    protected final StructureInfo registerStructure(String path, String displayNameKey, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = createStructureId(path);
        StructureInfo info = new StructureInfo(id, LocalizedText.translatable(displayNameKey), providerId, sizeX, sizeY, sizeZ);

        knownStructures.add(id);
        structureInfos.put(id, info);

        return info;
    }

    @Nullable
    protected final StructureInfo getMutableStructureInfo(String path) {
        return structureInfos.get(createStructureId(path));
    }

    protected final void setMetadata(String path, @Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, @Nullable LocalizedText rarity) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    protected final void setMetadata(String path, @Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, @Nullable String rarityKey) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarityKey(rarityKey);
    }

    protected final void setBlocks(String path, @Nullable List<BlockEntry> blocks) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setBlocks(blocks);
    }

    protected final void setBlocksIfMissing(String path, @Nullable List<BlockEntry> blocks) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null || !info.getBlocks().isEmpty()) return;

        info.setBlocks(blocks);
    }

    protected final void setLayers(String path, @Nullable List<StructureLayer> layers) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setLayers(layers);
    }

    protected final void setLootTables(String path, LootEntry... lootEntries) {
        setLootTables(path, Arrays.asList(lootEntries));
    }

    protected final void setLootTables(String path, @Nullable List<LootEntry> lootEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setLootTables(lootEntries);
    }

    protected final void setEntities(String path, EntityEntry... entityEntries) {
        setEntities(path, Arrays.asList(entityEntries));
    }

    protected final void setEntities(String path, @Nullable List<EntityEntry> entityEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setEntities(entityEntries);
    }

    protected final LootEntry createLootEntry(String lootTableId, String containerTypeKey) {
        return createLootEntry(new ResourceLocation(lootTableId), containerTypeKey);
    }

    protected final LootEntry createLootEntry(ResourceLocation lootTableId, String containerTypeKey) {
        return new LootEntry(lootTableId, Collections.emptyList(), LocalizedText.translatable(containerTypeKey));
    }

    protected final EntityEntry createEntityEntry(String entityId, int count) {
        return createEntityEntry(entityId, count, false);
    }

    protected final EntityEntry createEntityEntry(String entityId, int count, boolean spawner) {
        return createEntityEntry(new ResourceLocation(entityId), count, spawner);
    }

    protected final EntityEntry createEntityEntry(ResourceLocation entityId, int count) {
        return createEntityEntry(entityId, count, false);
    }

    protected final EntityEntry createEntityEntry(ResourceLocation entityId, int count, boolean spawner) {
        return new EntityEntry(entityId, count, spawner);
    }
}