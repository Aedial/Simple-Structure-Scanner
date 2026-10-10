package com.simplestructurescanner.structure.providers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.biome.Biome;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.generation.MapGenerationWorld;
import com.simplestructurescanner.structure.StructureNBTParser.StructureContentSink;
import com.simplestructurescanner.structure.util.StructureTranslationKeys;


/**
 * Shared provider scaffold for structure catalogs.
 */
public abstract class AbstractStructureProvider implements StructureProvider {
    protected static final String CHEST_KEY = "gui.structurescanner.loot.chest";
    protected static final String MINECART_CHEST_KEY = "gui.structurescanner.loot.minecart_chest";

    public static final IBlockState AIR = Blocks.AIR.getDefaultState();
    public static final IBlockState GRASS = Blocks.GRASS.getDefaultState();
    public static final IBlockState DIRT = Blocks.DIRT.getDefaultState();
    public static final IBlockState STONE = Blocks.STONE.getDefaultState();
    public static final IBlockState GRAVEL = Blocks.GRAVEL.getDefaultState();
    public static final IBlockState SAND = Blocks.SAND.getDefaultState();
    public static final IBlockState SANDSTONE = Blocks.SANDSTONE.getDefaultState();
    public static final IBlockState SNOW = Blocks.SNOW.getDefaultState();
    public static final IBlockState END_STONE = Blocks.END_STONE.getDefaultState();
    public static final IBlockState WATER = Blocks.WATER.getDefaultState();
    public static final IBlockState LAVA = Blocks.LAVA.getDefaultState();

    /** The unique ID of this structure provider. Used for filtering and identification. Does not need to match the mod ID. */
    private final String providerId;
    /** The namespace used for all structures registered by this provider. Usually matches the provider ID. */
    private final String structureNamespace;
    /** I18n key of the mod providing these structures. */
    private final String modName;
    /** The ID of the mod the structures depends on. Should always be provided for modded structures. */
    @Nullable
    private final String requiredModId;

    protected final List<ResourceLocation> knownStructures = new ArrayList<>();
    protected final List<ResourceLocation> searchableStructures = new ArrayList<>();
    protected final Map<ResourceLocation, StructureInfo> structureInfos = new LinkedHashMap<>();

    public enum Rarity {
        COMMON("common", 0xAAAAAA),
        UNCOMMON("uncommon", 0x55FF55),
        RARE("rare", 0x55AAFF),
        UNIQUE("unique", 0xFF55FF),
        FIXED_POSITION("fixed_position", 0xFFAA00);

        private final String key;
        private final int color;

        Rarity(String key, int color) {
            this.key = key;
            this.color = color;
        }

        public LocalizedText getKey() {
            return LocalizedText.translatable("gui.structurescanner.rarity." + key);
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * Creates a structure provider that is always available, regardless of mod presence.
     * Should **NEVER** be used for modded structures, as the provider will try to load resources
     * that will not exist if the mod is not present, causing errors and crashes.
     * <p>
     * @param providerId Unique ID of this structure provider. Used for filtering and identification.
     *                   Does not need to match the mod ID.
     * @param structureNamespace Namespace used for all structures registered by this provider.
     *                           Usually matches the provider ID.
     * @param modName I18n key of the mod providing these structures.
     */
    @ParametersAreNonnullByDefault
    protected AbstractStructureProvider(String providerId, String structureNamespace, String modName) {
        this.providerId = providerId;
        this.structureNamespace = structureNamespace;
        this.modName = modName;
        this.requiredModId = null;
    }

    /**
     * Creates a structure provider that is only available if the required mod is present.
     * <p>
     * @param providerId Unique ID of this structure provider. Used for filtering and identification. Does not need to match the mod ID.
     * @param structureNamespace Namespace used for all structures registered by this provider. Usually matches the provider ID.
     * @param modName I18n key of the mod providing these structures.
     * @param requiredModId The ID of the mod the structures depends on. Should always be provided for modded structures.
     */
    @ParametersAreNonnullByDefault
    protected AbstractStructureProvider(String providerId, String structureNamespace, String modName, String requiredModId) {
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

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return searchableStructures.contains(structureId);
    }

    /**
     * Providers rebuild their structure catalog during postInit and reloads, so the shared
     * collections need an explicit reset before repopulating them.
     */
    protected void resetStructures() {
        knownStructures.clear();
        structureInfos.clear();
        searchableStructures.clear();
    }

    protected ResourceLocation createStructureId(String path) {
        return new ResourceLocation(structureNamespace, path);
    }

    protected StructureInfo register(String path) {
        return register(createStructureId(path), false);
    }

    protected StructureInfo register(String path, boolean searchable) {
        return register(createStructureId(path), searchable);
    }

    protected StructureInfo register(ResourceLocation id) {
        return register(id, false);
    }

    protected StructureInfo register(ResourceLocation id, boolean searchable) {
        return register(id, LocalizedText.translatable(StructureTranslationKeys.structureNameKey(id)), searchable);
    }

    protected StructureInfo register(ResourceLocation id, LocalizedText displayName, boolean searchable) {
        StructureInfo info = new StructureInfo(id, displayName, providerId);

        knownStructures.add(id);
        structureInfos.put(id, info);

        if (searchable) searchableStructures.add(id);

        return info;
    }

    protected Set<Biome> biomes(Biome... biomes) {
        return Stream.of(biomes).collect(Collectors.toSet());
    }

    protected Set<Biome> hasAnyBiomes(BiomeDictionary.Type... types) {
        return Stream.of(types)
            .flatMap(type -> BiomeDictionary.getBiomes(type).stream())
            .collect(Collectors.toSet());
    }

    protected Set<Biome> hasAllBiomes(BiomeDictionary.Type... types) {
        Set<BiomeDictionary.Type> requiredTypes = new HashSet<>(Arrays.asList(types));

        Set<Biome> biomes = new HashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (BiomeDictionary.getTypes(biome).containsAll(requiredTypes)) biomes.add(biome);
        }

        return biomes;
    }

    protected Set<Biome> hasBiomesBut(Set<Biome> biomes, BiomeDictionary.Type... types) {
        Set<Biome> result = new HashSet<>(biomes);
        for (Biome biome : biomes) {
            for (BiomeDictionary.Type type : types) {
                if (BiomeDictionary.hasType(biome, type)) {
                    result.remove(biome);
                    break;
                }
            }
        }

        return result;
    }

    protected void addChestLoot(StructureContentSink builder, String namespace, String path) {
        addChestLoot(builder, new ResourceLocation(namespace, path));
    }

    protected void addChestLoot(StructureContentSink builder, ResourceLocation lootTableId) {
        builder.addLootEntry(new LootEntry(lootTableId, CHEST_KEY));
    }

    protected static StructureBoundingBox getMapBounds(MapGenerationWorld world) {
        return new StructureBoundingBox(world.getMinX(), 0, world.getMinZ(),
            world.getMaxX(), 255, world.getMaxZ());
    }

}