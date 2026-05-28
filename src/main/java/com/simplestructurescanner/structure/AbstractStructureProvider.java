package com.simplestructurescanner.structure;

import java.io.File;
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

import com.simplestructurescanner.Tags;
import com.simplestructurescanner.config.ModConfig;
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
    private static final String STRUCTURE_OVERRIDE_DIRECTORY = "structures";

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
    public String getProviderId() {
        return providerId;
    }

    @Override
    public String getModName() {
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
    protected void resetStructures() {
        knownStructures.clear();
        structureInfos.clear();
    }

    protected ResourceLocation createStructureId(String path) {
        return new ResourceLocation(structureNamespace, path);
    }

    protected StructureInfo registerStructure(String path, String displayNameKey, int sizeX, int sizeY, int sizeZ) {
        ResourceLocation id = createStructureId(path);
        StructureInfo info = new StructureInfo(id, LocalizedText.translatable(displayNameKey), providerId, sizeX, sizeY, sizeZ);

        knownStructures.add(id);
        structureInfos.put(id, info);

        return info;
    }

    @Nullable
    protected StructureInfo getMutableStructureInfo(String path) {
        return structureInfos.get(createStructureId(path));
    }

    protected void setMetadata(String path, @Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, @Nullable LocalizedText rarity) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarity(rarity);
    }

    protected void setMetadata(String path, @Nullable Set<Biome> biomes,
            @Nullable Set<DimensionInfo> dimensions, @Nullable String rarityKey) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setValidBiomes(biomes);
        info.setValidDimensions(dimensions);
        info.setRarityKey(rarityKey);
    }

    protected void setBlocks(String path, @Nullable List<BlockEntry> blocks) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setBlocks(blocks);
    }

    protected void setBlocksIfMissing(String path, @Nullable List<BlockEntry> blocks) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null || !info.getBlocks().isEmpty()) return;

        info.setBlocks(blocks);
    }

    protected void setLayers(String path, @Nullable List<StructureLayer> layers) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setLayers(layers);
    }

    protected void setLootTables(String path, LootEntry... lootEntries) {
        setLootTables(path, Arrays.asList(lootEntries));
    }

    protected void setLootTables(String path, @Nullable List<LootEntry> lootEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setLootTables(lootEntries);
    }

    protected void setLootTablesIfMissing(String path, LootEntry... lootEntries) {
        setLootTablesIfMissing(path, Arrays.asList(lootEntries));
    }

    protected void setLootTablesIfMissing(String path, @Nullable List<LootEntry> lootEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null || !info.getLootTables().isEmpty()) return;

        info.setLootTables(lootEntries);
    }

    protected void setEntities(String path, EntityEntry... entityEntries) {
        setEntities(path, Arrays.asList(entityEntries));
    }

    protected void setEntities(String path, @Nullable List<EntityEntry> entityEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return;

        info.setEntities(entityEntries);
    }

    protected void setEntitiesIfMissing(String path, EntityEntry... entityEntries) {
        setEntitiesIfMissing(path, Arrays.asList(entityEntries));
    }

    protected void setEntitiesIfMissing(String path, @Nullable List<EntityEntry> entityEntries) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null || !info.getEntities().isEmpty()) return;

        info.setEntities(entityEntries);
    }

    public static void apply(StructureInfo info, StructureNBTParser.ParsedStructure parsed) {
        if (!parsed.blocks.isEmpty()) info.setBlocks(parsed.blocks);
        if (!parsed.layers.isEmpty()) info.setLayers(parsed.layers);
        if (!parsed.entities.isEmpty()) info.setEntities(parsed.entities);
        if (!parsed.lootTables.isEmpty()) info.setLootTables(parsed.lootTables);
    }

    protected boolean applyStructureContentsFromNbt(String path) {
        return applyStructureContentsFromNbt(path, path, path, null);
    }

    protected boolean applyStructureContentsFromNbt(String path, String nbtPath) {
        return applyStructureContentsFromNbt(path, nbtPath, nbtPath, null);
    }

    protected boolean applyStructureContentsFromNbt(String path, String overrideNbtPath,
            String bundledNbtPath) {
        return applyStructureContentsFromNbt(path, overrideNbtPath, bundledNbtPath, null);
    }

    protected boolean applyStructureContentsFromNbt(String path, String overrideNbtPath,
            String bundledNbtPath,
            @Nullable StructureNBTParser.StructureParseExtension extension) {
        StructureInfo info = getMutableStructureInfo(path);
        if (info == null) return false;

        StructureNBTParser.ParsedStructure parsed = loadStructureContentsFromNbt(
            overrideNbtPath, bundledNbtPath, extension);
        if (parsed == null) return false;

        apply(info, parsed);
        return true;
    }

    @Nullable
    protected StructureNBTParser.ParsedStructure loadStructureContentsFromNbt(String nbtPath,
            @Nullable StructureNBTParser.StructureParseExtension extension) {
        return loadStructureContentsFromNbt(nbtPath, nbtPath, extension);
    }

    @Nullable
    protected StructureNBTParser.ParsedStructure loadStructureContentsFromNbt(String overrideNbtPath,
            String bundledNbtPath,
            @Nullable StructureNBTParser.StructureParseExtension extension) {
        String normalizedOverridePath = normalizeStructureNbtPath(overrideNbtPath);
        String normalizedBundledPath = normalizeStructureNbtPath(bundledNbtPath);
        File overrideFile = getStructureOverrideFile(normalizedOverridePath);

        if (overrideFile != null && overrideFile.isFile()) {
            StructureNBTParser.ParsedStructure parsed = StructureNBTParser.parseStructureFile(overrideFile, extension);
            if (parsed != null) return parsed;
        }

        return StructureNBTParser.parseBundledStructure(Tags.MODID,
            providerId + "/" + normalizedBundledPath, extension);
    }

    private static String normalizeStructureNbtPath(String nbtPath) {
        String normalizedPath = nbtPath.replace('\\', '/');
        if (normalizedPath.startsWith("/")) normalizedPath = normalizedPath.substring(1);
        if (normalizedPath.endsWith(".nbt")) normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);

        return normalizedPath;
    }

    @Nullable
    private File getStructureOverrideFile(String normalizedNbtPath) {
        File configRoot = ModConfig.getConfigRootDirectory();
        if (configRoot == null) return null;

        File providerDirectory = new File(new File(configRoot, STRUCTURE_OVERRIDE_DIRECTORY), providerId);
        return new File(providerDirectory, normalizedNbtPath + ".nbt");
    }

    protected LootEntry createLootEntry(String lootTableId, String containerTypeKey) {
        return createLootEntry(new ResourceLocation(lootTableId), containerTypeKey);
    }

    protected LootEntry createLootEntry(ResourceLocation lootTableId, String containerTypeKey) {
        return new LootEntry(lootTableId, Collections.emptyList(), LocalizedText.translatable(containerTypeKey));
    }

    protected EntityEntry createEntityEntry(String entityId, int count) {
        return createEntityEntry(entityId, count, false);
    }

    protected EntityEntry createEntityEntry(String entityId, int count, boolean spawner) {
        return createEntityEntry(new ResourceLocation(entityId), count, spawner);
    }

    protected EntityEntry createEntityEntry(ResourceLocation entityId, int count) {
        return createEntityEntry(entityId, count, false);
    }

    protected EntityEntry createEntityEntry(ResourceLocation entityId, int count, boolean spawner) {
        return new EntityEntry(entityId, count, spawner);
    }
}