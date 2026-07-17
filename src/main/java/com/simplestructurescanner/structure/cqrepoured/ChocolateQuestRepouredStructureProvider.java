package com.simplestructurescanner.structure.cqrepoured;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureInfo.EntityEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.util.PositionHelper;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;
import com.simplestructurescanner.structure.util.StructureContentAccumulator;
import com.simplestructurescanner.structure.util.PreviewGenerationWorld;
import com.simplestructurescanner.structure.util.StructurePreviewStitcher;
import com.simplestructurescanner.structure.util.StructureTranslationKeys;


public class ChocolateQuestRepouredStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "chocolate_quest";
    private static final String MOD_NAME = "gui.structurescanner.provider.chocolate_quest";
    private static final String MOD_ID = "cqrepoured";

    private static final String CQR_MAIN_CLASS = "team.cqr.cqrepoured.CQRMain";
    private static final String CQR_CONFIG_CLASS = "team.cqr.cqrepoured.config.CQRConfig";
    private static final String WORLD_DUNGEON_GENERATOR_CLASS = "team.cqr.cqrepoured.world.structure.generation.WorldDungeonGenerator";
    private static final String CQ_STRUCTURE_CLASS = "team.cqr.cqrepoured.world.structure.generation.structurefile.CQStructure";
    private static final String CQR_BLOCKS_CLASS = "team.cqr.cqrepoured.init.CQRBlocks";
    private static final String CQR_DUNGEON_GENERATOR_ENUM_CLASS = "team.cqr.cqrepoured.world.structure.generation.EDungeonGenerator";
    private static final String CQR_DUNGEON_SPAWN_TYPE_CLASS = "team.cqr.cqrepoured.world.structure.generation.DungeonDataManager$DungeonSpawnType";
    private static final String CQR_BLOCK_DUNGEON_PART_CLASS = "team.cqr.cqrepoured.world.structure.generation.generation.part.BlockDungeonPart";
    private static final String CQR_ENTITY_DUNGEON_PART_CLASS = "team.cqr.cqrepoured.world.structure.generation.generation.part.EntityDungeonPart";
    private static final String CQR_GENERATABLE_BLOCK_INFO_CLASS = "team.cqr.cqrepoured.world.structure.generation.generation.generatable.GeneratableBlockInfo";
    private static final String CQR_GENERATABLE_ENTITY_INFO_CLASS = "team.cqr.cqrepoured.world.structure.generation.generation.generatable.GeneratableEntityInfo";

    private static final int CHUNK_COORDINATE_SHIFT = 4;
    private static final int DEFAULT_SEARCH_RADIUS_CHUNKS = 192;
    private static final int MAX_SEARCH_RADIUS_CHUNKS = 384;
    private static final long MAX_SCAN_TIME_MS = 10000L;
    private static final int PREVIEW_COLUMNS = 3;
    private static final int PREVIEW_SPACING = 2;
    private static final int GENERATED_PREVIEW_BASE_Y = 64;

    private static final List<ResourceLocation> CQR_NULL_BLOCKS = Arrays.asList(
        new ResourceLocation(MOD_ID, "null_block"),
        new ResourceLocation(MOD_ID, "map_placeholder")
    );
    private static final ResourceLocation CQR_DUMMY_ENTITY = new ResourceLocation(MOD_ID, "dummy");
    private static final ResourceLocation CQR_DUMMY_BOSS_ENTITY = new ResourceLocation(MOD_ID, "dummy_boss");
    private static final PreviewGenerationWorld PREVIEW_WORLD = new PreviewGenerationWorld(1L, GENERATED_PREVIEW_BASE_Y);

    private final Map<ResourceLocation, DungeonDefinition> dungeonsById = new LinkedHashMap<>();
    private final Map<String, InhabitantDefinition> inhabitantsByName = new LinkedHashMap<>();
    private final Map<String, IBlockState> cqrBlockStates = new LinkedHashMap<>();

    private Class<?> worldDungeonGeneratorClass;
    private Class<?> cqStructureClass;
    private Class<?> cqrBlocksClass;
    private Class<?> dungeonGeneratorEnumClass;
    private Class<?> dungeonSpawnTypeClass;
    private Class<?> blockDungeonPartClass;
    private Class<?> entityDungeonPartClass;
    private Class<?> generatableBlockInfoClass;
    private Class<?> generatableEntityInfoClass;

    @Nullable
    private InhabitantDefinition defaultInhabitant;

    public ChocolateQuestRepouredStructureProvider() {
        super(PROVIDER_ID, PROVIDER_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();
        dungeonsById.clear();
        inhabitantsByName.clear();
        cqrBlockStates.clear();
        defaultInhabitant = null;

        try {
            Class<?> cqrMainClass = ReflectionHelper.loadClassRequired(CQR_MAIN_CLASS);
            Class<?> cqrConfigClass = ReflectionHelper.loadClassRequired(CQR_CONFIG_CLASS);
            worldDungeonGeneratorClass = ReflectionHelper.loadClassRequired(WORLD_DUNGEON_GENERATOR_CLASS);
            cqStructureClass = ReflectionHelper.loadClassRequired(CQ_STRUCTURE_CLASS);
            cqrBlocksClass = ReflectionHelper.loadClassRequired(CQR_BLOCKS_CLASS);
            dungeonGeneratorEnumClass = ReflectionHelper.loadClassRequired(CQR_DUNGEON_GENERATOR_ENUM_CLASS);
            dungeonSpawnTypeClass = ReflectionHelper.loadClassRequired(CQR_DUNGEON_SPAWN_TYPE_CLASS);
            blockDungeonPartClass = ReflectionHelper.loadClassRequired(CQR_BLOCK_DUNGEON_PART_CLASS);
            entityDungeonPartClass = ReflectionHelper.loadClassRequired(CQR_ENTITY_DUNGEON_PART_CLASS);
            generatableBlockInfoClass = ReflectionHelper.loadClassRequired(CQR_GENERATABLE_BLOCK_INFO_CLASS);
            generatableEntityInfoClass = ReflectionHelper.loadClassRequired(CQR_GENERATABLE_ENTITY_INFO_CLASS);

            File dungeonFolder = (File) ReflectionHelper.getStaticField(cqrMainClass, "CQ_DUNGEON_FOLDER");
            File gridFolder = (File) ReflectionHelper.getStaticField(cqrMainClass, "CQ_DUNGEON_GRID_FOLDER");
            File structureFolder = (File) ReflectionHelper.getStaticField(cqrMainClass, "CQ_STRUCTURE_FILES_FOLDER");
            File inhabitantFolder = (File) ReflectionHelper.getStaticField(cqrMainClass, "CQ_INHABITANT_FOLDER");

            loadInhabitants(inhabitantFolder, cqrConfigClass);
            Map<String, GridInfo> gridInfos = loadGridInfos(gridFolder);
            loadDungeons(dungeonFolder, structureFolder, gridInfos);

            SimpleStructureScanner.LOGGER.info("Loaded {} Chocolate Quest Repoured dungeons", structureInfos.size());
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.error("Failed to initialize Chocolate Quest Repoured structure provider", e);
        }
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return dungeonsById.containsKey(structureId);
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        DungeonDefinition dungeon = dungeonsById.get(structureId);
        if (world == null || dungeon == null) return null;

        ScanOutcome scanOutcome = scanDungeons(world, dungeon, pos, Integer.MAX_VALUE);
        if (scanOutcome.positions.isEmpty()) return null;

        PositionHelper.sortByHorizontalDistance(scanOutcome.positions, pos);
        PositionHelper.FilteredPositionResult selection = PositionHelper.selectFilteredPosition(
            scanOutcome.positions, skipCount, locationFilter);
        if (selection == null) return null;

        return new StructureLocation(selection.getPosition(), skipCount, selection.getTotalMatches(), true);
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        DungeonDefinition dungeon = dungeonsById.get(structureId);
        if (world == null || dungeon == null) return Collections.emptyList();

        ScanOutcome scanOutcome = scanDungeons(world, dungeon, pos, maxResults);
        PositionHelper.sortByHorizontalDistance(scanOutcome.positions, pos);
        if (scanOutcome.positions.size() <= maxResults) return scanOutcome.positions;

        return new ArrayList<>(scanOutcome.positions.subList(0, maxResults));
    }

    private void loadInhabitants(@Nullable File inhabitantFolder, Class<?> cqrConfigClass) {
        if (inhabitantFolder != null) {
            for (File file : listConfigFiles(inhabitantFolder)) {
                Properties properties = loadProperties(file);
                if (properties == null) continue;

                InhabitantDefinition inhabitant = InhabitantDefinition.fromProperties(properties);
                if (inhabitant == null) continue;

                inhabitantsByName.put(inhabitant.name.toUpperCase(Locale.ROOT), inhabitant);
            }
        }

        defaultInhabitant = buildDefaultInhabitant(cqrConfigClass);
        if (defaultInhabitant != null) {
            inhabitantsByName.put(defaultInhabitant.name, defaultInhabitant);
        }
    }

    /**
     * Build the default inhabitant definition from the CQR config, if available.
     * This is used to replace dummy entities in dungeons that don't specify a custom inhabitant.
     * If we can't read the config, we return null and fall back to an empty inhabitant definition.
     */
    @Nullable
    private InhabitantDefinition buildDefaultInhabitant(Class<?> cqrConfigClass) {
        Set<ResourceLocation> entityIds = new LinkedHashSet<>();
        Set<ResourceLocation> bossIds = new LinkedHashSet<>();

        try {
            Object generalConfig = ReflectionHelper.getStaticField(cqrConfigClass, "general");
            Object value = ReflectionHelper.getField(generalConfig, generalConfig.getClass(), "defaultInhabitantConfig");
            if (value instanceof String[]) {
                for (String entry : (String[]) value) {
                    if (entry == null || entry.trim().isEmpty()) continue;
                    for (String inhabitantName : entry.split(",")) {
                        InhabitantDefinition inhabitant = inhabitantsByName.get(
                            inhabitantName.trim().toUpperCase(Locale.ROOT));
                        if (inhabitant == null) continue;

                        entityIds.addAll(inhabitant.entityIds);
                        bossIds.addAll(inhabitant.bossIds);
                    }
                }
            }
        } catch (ReflectionException e) {
            return null;
        }

        return new InhabitantDefinition("DEFAULT", new ArrayList<>(entityIds), new ArrayList<>(bossIds));
    }

    private Map<String, GridInfo> loadGridInfos(@Nullable File gridFolder) {
        if (gridFolder == null) return Collections.emptyMap();

        Map<String, GridInfo> gridInfos = new LinkedHashMap<>();
        for (File file : listConfigFiles(gridFolder)) {
            Properties properties = loadProperties(file);
            if (properties == null) continue;

            int distance = parseInt(properties.getProperty("distance"), 0);
            int chance = parseInt(properties.getProperty("chance"), 100);
            if (distance <= 0) continue;

            for (String dungeonName : splitCsv(properties.getProperty("dungeons"))) {
                GridInfo next = new GridInfo(distance, chance);
                GridInfo current = gridInfos.get(dungeonName);
                if (current == null || next.approximateRarityChunks() < current.approximateRarityChunks()) {
                    gridInfos.put(dungeonName, next);
                }
            }
        }

        return gridInfos;
    }

    private void loadDungeons(@Nullable File dungeonFolder, @Nullable File structureFolder,
            Map<String, GridInfo> gridInfos) {
        if (dungeonFolder == null || structureFolder == null) return;

        Set<String> usedPaths = new LinkedHashSet<>();
        Set<String> seenDungeonNames = new LinkedHashSet<>();
        for (File file : listConfigFiles(dungeonFolder)) {
            Properties properties = loadProperties(file);
            if (properties == null) continue;

            String rawName = stripExtension(file.getName());
            if (!seenDungeonNames.add(rawName.toUpperCase(Locale.ROOT))) continue;

            DungeonDefinition dungeon = createDungeonDefinition(file, properties, structureFolder,
                gridInfos.get(rawName), usedPaths);
            if (dungeon == null) continue;

            registerDungeon(dungeon);
        }
    }

    @Nullable
    private DungeonDefinition createDungeonDefinition(File file, Properties properties, File structureFolder,
            @Nullable GridInfo gridInfo, Set<String> usedPaths) {
        String rawName = stripExtension(file.getName());
        if (!parseBoolean(properties.getProperty("enabled"), true)) return null;

        int weight = parseInt(properties.getProperty("weight"), 0);
        int chance = parseInt(properties.getProperty("chance"), 0);
        if (weight <= 0 || chance <= 0) return null;

        if (isDependencyMissing(properties.getProperty("modDependencies"))) return null;

        String generatorType = properties.getProperty("generator", "template_surface").trim().toLowerCase(Locale.ROOT);
        String path = createUniquePath(rawName, usedPaths);
        ResourceLocation id = createStructureId(path);
        LocalizedText displayName = LocalizedText.translatable(StructureTranslationKeys.structureNameKey(id));
        String dungeonMob = properties.getProperty("dummyReplacement", "DEFAULT").trim().toUpperCase(Locale.ROOT);
        List<File> structureFiles = collectStructureFiles(properties, structureFolder);
        boolean fixedPosition = !splitSemicolon(properties.getProperty("lockedPositions")).isEmpty();
        Set<DimensionInfo> dimensions = resolveDimensions(properties);
        Set<Biome> biomes = resolveBiomes(properties);

        LocalizedText rarity = fixedPosition ? RarityTextHelper.fixedPosition()
            : resolveRarity(gridInfo, chance);
        int searchRadiusChunks = resolveSearchRadiusChunks(gridInfo, fixedPosition);

        return new DungeonDefinition(id, rawName, displayName, generatorType, dungeonMob,
            structureFiles, dimensions, biomes, rarity, properties, searchRadiusChunks);
    }

    private void registerDungeon(DungeonDefinition dungeon) {
        StructureInfo info = new StructureInfo(dungeon.id, dungeon.displayName,
            PROVIDER_ID, 0, 0, 0);

        if (dungeon.dimensions != null) info.setValidDimensions(dungeon.dimensions);
        if (dungeon.biomes != null) info.setValidBiomes(dungeon.biomes);
        if (dungeon.rarity != null) info.setRarity(dungeon.rarity);

        knownStructures.add(dungeon.id);
        structureInfos.put(dungeon.id, info);
        dungeonsById.put(dungeon.id, dungeon);

        populateDungeonContents(dungeon, info);
    }

    private boolean isRandomizedDungeon(DungeonDefinition dungeon) {
        if (!"randomized_castle".equals(dungeon.generatorType)) return false;

        return true;
    }

    private void populateDungeonContents(DungeonDefinition dungeon, StructureInfo info) {
        StructureContentAccumulator contents = new StructureContentAccumulator();
        StructurePreviewStitcher preview = new StructurePreviewStitcher();
        InhabitantDefinition inhabitant = resolveInhabitant(dungeon.dungeonMob);

        // Most dungeons already rebuild their content catalog from the static NBT files below.
        // In those cases we only need generated preview layers, not a second copy of block/entity content.
        boolean generatedPreviewNeedsContents = dungeon.structureFiles.isEmpty() && isRandomizedDungeon(dungeon);
        StructureContentAccumulator.GeneratedPreviewData generatedPreviewData = buildGeneratedPreviewData(
            dungeon, inhabitant, generatedPreviewNeedsContents);
        // TODO: Preview is about 2/3rd of the load time. Maybe lazy load?
        //       Would add a few seconds to the preview, which is a fair bit.
        List<StructureInfo.StructureLayer> generatedPreviewLayers = generatedPreviewData != null
            ? generatedPreviewData.getLayers()
            : null;

        // To ensure we show everything that could be generated, we parse every structure file
        // and merge the results. There is no way to give a realistic %chance for each block or entity,
        // as we'd need to run the generation logic for many seeds, which is a huge performance hit.
        // This step also handles the preview stitching for the gallery view, if we don't have a generated preview.
        if (!dungeon.structureFiles.isEmpty()) {
            int previewColumn = 0;
            int previewX = 0;
            int previewZ = 0;
            int rowDepth = 0;
            boolean includeParsedLayers = generatedPreviewLayers == null;

            for (File structureFile : dungeon.structureFiles) {
                // Keep parsing every structure file here to show the full superset of blocks, entities,
                // and loot pieces, instead of only the first realized layout.
                StructureNBTParser.ParsedStructure parsed = parseCqStructure(
                    structureFile, inhabitant, includeParsedLayers);
                if (parsed == null) continue;

                contents.add(parsed);
                if (generatedPreviewLayers == null) {
                    preview.addParsedStructure(parsed, new BlockPos(previewX, 0, previewZ));
                }

                int width = Math.max(parsed.sizeX, 1);
                int depth = Math.max(parsed.sizeZ, 1);
                rowDepth = Math.max(rowDepth, depth);
                previewColumn++;

                if (previewColumn >= PREVIEW_COLUMNS) {
                    previewColumn = 0;
                    previewX = 0;
                    previewZ += rowDepth + PREVIEW_SPACING;
                    rowDepth = 0;
                } else {
                    previewX += width + PREVIEW_SPACING;
                }
            }

        } else if (generatedPreviewNeedsContents) {
            // Randomized dungeons have no static structure file to parse,
            // so we build the preview layers from the generated layout instead.
            if (generatedPreviewData != null) contents.fromPreviewData(generatedPreviewData);
        }

        info.setBlocks(contents.buildBlocks());
        info.setEntities(rewriteDummyEntities(dungeon.id.toString(), contents.buildEntities(), inhabitant));
        info.setLootTables(contents.buildLootEntries());
        List<StructureInfo.StructureLayer> layers = generatedPreviewLayers != null
            ? generatedPreviewLayers
            : preview.buildLayers();
        if (!layers.isEmpty()) info.setLayers(layers);
    }

    /**
     * Try to generate the dungeon in a fake world to capture the resulting blocks for a preview.
     * This avoids the need to implement a full placement logic for each dungeon type, and ensures
     * that the preview matches the actual generated layout. If generation fails or is not supported,
     * the fallback is to use the gallery preview from the structure files instead.
     */
    @Nullable
    private StructureContentAccumulator.GeneratedPreviewData buildGeneratedPreviewData(DungeonDefinition dungeon,
            @Nullable InhabitantDefinition inhabitant, boolean includeContents) {
        try {
            Object generatableDungeon = createGeneratedPreviewDungeon(dungeon);
            if (generatableDungeon == null) return null;

            StructurePreviewStitcher preview = new StructurePreviewStitcher();
            StructureContentAccumulator contents = includeContents ? new StructureContentAccumulator() : null;
            populateGeneratedPreviewData(preview, contents, generatableDungeon, inhabitant);

            List<StructureInfo.StructureLayer> layers = preview.buildLayers();
            if (!includeContents && layers.isEmpty()) return null;

            List<BlockEntry> blocks = includeContents && contents != null
                ? contents.buildBlocks()
                : Collections.emptyList();
            List<EntityEntry> entities = includeContents && contents != null
                ? contents.buildEntities()
                : Collections.emptyList();
            List<LootEntry> lootEntries = includeContents && contents != null
                ? contents.buildLootEntries()
                : Collections.emptyList();
            if (includeContents && layers.isEmpty() && blocks.isEmpty() && entities.isEmpty() && lootEntries.isEmpty()) {
                return null;
            }

            return new StructureContentAccumulator.GeneratedPreviewData(blocks, entities, lootEntries, layers);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.debug(
                "Falling back to gallery preview for CQR dungeon {} ({})",
                dungeon.rawName, dungeon.generatorType, e
            );
            return null;
        }
    }

    @Nullable
    private Object createGeneratedPreviewDungeon(DungeonDefinition dungeon) throws ReflectionException {
        Object generatorType = resolveDungeonGeneratorType(dungeon.generatorType);
        if (generatorType == null) return null;

        Object dungeonSpawnType = resolveDungeonSpawnType();
        if (dungeonSpawnType == null) return null;

        Object dungeonInstance = ReflectionHelper.invokeRequired(generatorType, "createDungeon",
            new Class<?>[]{String.class, Properties.class}, dungeon.rawName, dungeon.properties);
        if (dungeonInstance == null) return null;

        synchronized (PREVIEW_WORLD) {
            Object generator = ReflectionHelper.invokeRequired(dungeonInstance, "createDungeonGenerator",
                new Class<?>[]{World.class, int.class, int.class, int.class, Random.class, dungeonSpawnType.getClass()},
                PREVIEW_WORLD,
                0,
                GENERATED_PREVIEW_BASE_Y,
                0,
                new Random(dungeon.id.toString().hashCode()),
                dungeonSpawnType
            );
            return generator != null ? ReflectionHelper.invokeRequired(generator, "get") : null;
        }
    }

    private void populateGeneratedPreviewData(StructurePreviewStitcher preview,
            @Nullable StructureContentAccumulator contents, Object generatableDungeon,
            @Nullable InhabitantDefinition inhabitant)
            throws ReflectionException {
        List<?> parts = ReflectionHelper.getListField(generatableDungeon, generatableDungeon.getClass(), "parts");
        if (parts == null) return;

        for (Object part : parts) {
            if (blockDungeonPartClass.isInstance(part)) {
                populateGeneratedBlockPart(preview, contents, part, generatableBlockInfoClass, inhabitant);
                continue;
            }

            if (contents != null && entityDungeonPartClass.isInstance(part)) {
                populateGeneratedEntityPart(contents, part, generatableEntityInfoClass);
            }
        }
    }

    private void populateGeneratedBlockPart(StructurePreviewStitcher preview,
            @Nullable StructureContentAccumulator contents, Object part, Class<?> generatableBlockInfoClass,
            @Nullable InhabitantDefinition inhabitant)
            throws ReflectionException {
        Object chunksObject = ReflectionHelper.invokeRequired(part, "getChunks");
        if (!(chunksObject instanceof Iterable)) return;

        for (Object chunkInfo : (Iterable<?>) chunksObject) {
            List<?> blocks = ReflectionHelper.getListField(chunkInfo, chunkInfo.getClass(), "blocks");
            if (blocks == null) continue;

            for (Object blockInfo : blocks) {
                if (!generatableBlockInfoClass.isInstance(blockInfo)) continue;

                int x = (Integer) ReflectionHelper.invokeRequired(blockInfo, "getX");
                int y = (Integer) ReflectionHelper.invokeRequired(blockInfo, "getY");
                int z = (Integer) ReflectionHelper.invokeRequired(blockInfo, "getZ");
                IBlockState state = (IBlockState) ReflectionHelper.invokeRequired(blockInfo, "getState");
                if (!shouldIncludeState(state)) continue;

                TileEntity tileEntity = (TileEntity) ReflectionHelper.invokeRequired(blockInfo, "getTileEntity");
                NBTTagCompound tileEntityData = createPreviewTileEntityData(tileEntity);
                if (contents != null) addGeneratedBlock(contents, state, tileEntityData, inhabitant);
                preview.setBlock(x, y - GENERATED_PREVIEW_BASE_Y, z, state, tileEntityData);
            }
        }
    }

    private void populateGeneratedEntityPart(StructureContentAccumulator contents, Object part,
            Class<?> generatableEntityInfoClass) throws ReflectionException {
        Object entitiesObject = ReflectionHelper.invokeRequired(part, "getEntities");
        if (!(entitiesObject instanceof Iterable)) return;

        for (Object entityInfo : (Iterable<?>) entitiesObject) {
            if (!generatableEntityInfoClass.isInstance(entityInfo)) continue;

            Object entityObject = ReflectionHelper.invokeRequired(entityInfo, "getEntity");
            if (!(entityObject instanceof Entity)) continue;

            NBTTagCompound entityData = new NBTTagCompound();
            if (!((Entity) entityObject).writeToNBTOptional(entityData)) continue;

            StructureNBTParser.handleDefaultEntity(contents, entityData);
        }
    }

        private void addGeneratedBlock(StructureContentAccumulator contents, IBlockState state,
            @Nullable NBTTagCompound tileEntityData, @Nullable InhabitantDefinition inhabitant) {
        contents.addBlockCount(
            StructureNBTParser.createDisplayedBlockKey(
                state,
                StructureNBTParser.createDisplayFluid(state),
                StructureNBTParser.createDisplayStack(state, tileEntityData)
            ),
            state,
            tileEntityData
        );
        if (tileEntityData == null) return;

        if (isSpawnerTileEntityData(state, tileEntityData)) {
            extractSpawnerEntities(contents, tileEntityData, inhabitant);
            return;
        }

        StructureNBTParser.handleDefaultBlockEntity(contents, state, state.getBlock(), tileEntityData.copy());
    }

    private boolean isSpawnerTileEntityData(IBlockState state, NBTTagCompound tileEntityData) {
        if (state.getBlock() == Blocks.MOB_SPAWNER) return true;
        if (tileEntityData.hasKey("inventory", Constants.NBT.TAG_COMPOUND)) return true;
        if (tileEntityData.hasKey("SpawnPotentials", Constants.NBT.TAG_LIST)) return true;
        if (tileEntityData.hasKey("SpawnData", Constants.NBT.TAG_COMPOUND)) return true;

        return tileEntityData.hasKey("EntityId", Constants.NBT.TAG_STRING)
            && !tileEntityData.getString("EntityId").isEmpty();
    }

    @Nullable
    private NBTTagCompound createPreviewTileEntityData(@Nullable TileEntity tileEntity) {
        if (tileEntity == null) return null;

        NBTTagCompound tileEntityData = tileEntity.writeToNBT(new NBTTagCompound());
        tileEntityData.removeTag("x");
        tileEntityData.removeTag("y");
        tileEntityData.removeTag("z");
        return tileEntityData.isEmpty() ? null : tileEntityData;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveDungeonGeneratorType(String generatorType) throws ReflectionException {
        try {
            return Enum.valueOf((Class<Enum>) dungeonGeneratorEnumClass, generatorType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object resolveDungeonSpawnType() throws ReflectionException {
        try {
            return Enum.valueOf((Class<Enum>) dungeonSpawnTypeClass, "DUNGEON_GENERATION");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<EntityEntry> rewriteDummyEntities(String dungeonId, List<EntityEntry> entities, @Nullable InhabitantDefinition inhabitant) {
        Map<String, EntityEntry> rewritten = new LinkedHashMap<>();

        for (EntityEntry entityEntry : entities) {
            if (entityEntry == null || entityEntry.entityId == null || entityEntry.count <= 0) continue;

            if (!CQR_DUMMY_ENTITY.equals(entityEntry.entityId)
                    && !CQR_DUMMY_BOSS_ENTITY.equals(entityEntry.entityId)) {
                mergeEntityEntry(rewritten, entityEntry.entityId, entityEntry.count, entityEntry.spawner);
                continue;
            }

            List<ResourceLocation> replacementIds = CQR_DUMMY_BOSS_ENTITY.equals(entityEntry.entityId)
                ? getBossReplacementIds(inhabitant, entityEntry.spawner)
                : getDummyReplacementIds(inhabitant, entityEntry.spawner);
            distributeEntityEntries(rewritten, replacementIds, dungeonId, entityEntry.count, entityEntry.spawner);
        }

        List<EntityEntry> results = new ArrayList<>(rewritten.values());
        results.sort((first, second) -> Integer.compare(second.count, first.count));
        return results;
    }

    private List<ResourceLocation> getDummyReplacementIds(@Nullable InhabitantDefinition inhabitant, boolean spawner) {
        if (inhabitant == null || inhabitant.entityIds.isEmpty()) return Collections.emptyList();

        List<ResourceLocation> replacementIds = new ArrayList<>();
        for (ResourceLocation entityId : inhabitant.entityIds) {
            if (spawner || StructureNBTParser.shouldIncludeStructureEntity(entityId)) replacementIds.add(entityId);
        }

        return replacementIds;
    }

    private List<ResourceLocation> getBossReplacementIds(@Nullable InhabitantDefinition inhabitant, boolean spawner) {
        if (inhabitant == null || inhabitant.bossIds.isEmpty()) return Collections.emptyList();

        List<ResourceLocation> replacementIds = new ArrayList<>();
        for (ResourceLocation entityId : inhabitant.bossIds) {
            if (spawner || StructureNBTParser.shouldIncludeStructureEntity(entityId)) replacementIds.add(entityId);
        }

        return replacementIds;
    }

    // CQR selects one entry from each inhabitant pool, so placeholders should keep their total count.
    // Distribute `totalCount` across the available replacements using randomized sampling
    // (multinomial-style): simulate `totalCount` independent draws and count occurrences.
    private void distributeEntityEntries(Map<String, EntityEntry> target, List<ResourceLocation> replacementIds,
            String dungeonId, int totalCount, boolean spawner) {
        if (replacementIds.isEmpty() || totalCount <= 0) return;

        int size = replacementIds.size();
        int[] counts = new int[size];


        Random rnd = new Random(dungeonId.hashCode());
        for (int i = 0; i < totalCount; i++) counts[rnd.nextInt(size)]++;

        for (int i = 0; i < size; i++) {
            int distributedCount = counts[i];
            if (distributedCount == 0) continue;

            mergeEntityEntry(target, replacementIds.get(i), distributedCount, spawner);
        }
    }

    private void mergeEntityEntry(Map<String, EntityEntry> target, ResourceLocation entityId, int count,
            boolean spawner) {
        if (entityId == null || count <= 0) return;

        String key = entityId.toString() + '|' + spawner;
        EntityEntry current = target.get(key);
        if (current == null) {
            target.put(key, new EntityEntry(entityId, count, spawner));
            return;
        }

        target.put(key, new EntityEntry(entityId, current.count + count, spawner));
    }

    @Nullable
    private StructureNBTParser.ParsedStructure parseCqStructure(File structureFile,
            @Nullable InhabitantDefinition inhabitant, boolean includeLayers) {
        try {
            Object cqStructure = ReflectionHelper.invokeStaticRequired(cqStructureClass, "createFromFile",
                new Class<?>[]{File.class}, structureFile);
            Object sizeObject = ReflectionHelper.invokeRequired(cqStructure, "getSize");
            if (!(sizeObject instanceof BlockPos)) return null;

            BlockPos size = (BlockPos) sizeObject;
            int sizeX = Math.max(size.getX(), 0);
            int sizeY = Math.max(size.getY(), 0);
            int sizeZ = Math.max(size.getZ(), 0);
            // TODO: NBT is the other 1/3rd. This one might be more complicated without multithreading
            StructureNBTParser.ParsedStructureBuilder builder = new StructureNBTParser.ParsedStructureBuilder(
                sizeX, sizeY, sizeZ, includeLayers);

            List<?> blockInfoList = (List<?>) ReflectionHelper.invokeRequired(cqStructure, "getBlockInfoList");
            int layerSize = sizeY * sizeZ;
            int maxEntries = Math.min(blockInfoList.size(), sizeX * sizeY * sizeZ);

            for (int index = 0; index < maxEntries; index++) {
                Object preparable = blockInfoList.get(index);
                if (preparable == null) continue;

                int x = layerSize > 0 ? index / layerSize : 0;
                int remainder = layerSize > 0 ? index % layerSize : 0;
                int y = sizeZ > 0 ? remainder / sizeZ : 0;
                int z = sizeZ > 0 ? remainder % sizeZ : 0;

                addPreparedBlock(builder, preparable, x, y, z, inhabitant);
            }

            List<?> entityInfoList = (List<?>) ReflectionHelper.invokeRequired(cqStructure, "getEntityInfoList");
            for (Object entityInfo : entityInfoList) {
                Object entityData = ReflectionHelper.invokeRequired(entityInfo, "getEntityData");
                if (entityData instanceof NBTTagCompound) {
                    addPreparedEntity(builder, (NBTTagCompound) entityData, inhabitant);
                }
            }

            return builder.build();
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to parse CQR structure {}", structureFile.getAbsolutePath(), e);
            return null;
        }
    }

    private void addPreparedEntity(StructureNBTParser.ParsedStructureBuilder builder, NBTTagCompound entityData,
            @Nullable InhabitantDefinition inhabitant) {
        addEntityId(builder, entityData.getString("id"), inhabitant, false);
    }

    private void addPreparedBlock(StructureNBTParser.ParsedStructureBuilder builder, Object preparable,
            int x, int y, int z, @Nullable InhabitantDefinition inhabitant) throws ReflectionException {
        String className = preparable.getClass().getSimpleName();

        switch (className) {
            case "PreparableEmptyInfo":
            case "PreparableMapInfo":
                return;
            case "PreparableLootChestInfo":
                addLootChest(builder, preparable, x, y, z);
                return;
            case "PreparableSpawnerInfo":
                addSpawner(builder, preparable, x, y, z, inhabitant);
                return;
            case "PreparableBossInfo":
                addBoss(builder, preparable, x, y, z, inhabitant);
                return;
            case "PreparableForceFieldNexusInfo":
                addSyntheticBlock(builder, x, y, z, getCqrDefaultState("FORCE_FIELD_NEXUS"), null);
                return;
            case "PreparableTNTCQRInfo":
                addSyntheticBlock(builder, x, y, z, Blocks.TNT.getDefaultState(), null);
                return;
        }

        IBlockState state = tryGetState(preparable);
        if (!shouldIncludeState(state)) return;

        NBTTagCompound tileEntityData = tryGetTileEntityData(preparable);
        addStandardBlock(builder, x, y, z, state, tileEntityData, inhabitant);
    }

    private void addLootChest(StructureNBTParser.ParsedStructureBuilder builder, Object preparable,
            int x, int y, int z) throws ReflectionException {
        Object lootTable = ReflectionHelper.invokeRequired(preparable, "getLootTable");
        Object facingObject = ReflectionHelper.invokeRequired(preparable, "getFacing");
        if (!(lootTable instanceof ResourceLocation) || !(facingObject instanceof EnumFacing)) return;

        IBlockState state = Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, (EnumFacing) facingObject);
        addSyntheticBlock(builder, x, y, z, state, null);
        builder.addLootEntry(createLootEntry((ResourceLocation) lootTable, "gui.structurescanner.loot.chest"));
    }

    private void addSpawner(StructureNBTParser.ParsedStructureBuilder builder, Object preparable,
            int x, int y, int z, @Nullable InhabitantDefinition inhabitant) {
        NBTTagCompound tileEntityData = tryGetTileEntityData(preparable);
        IBlockState state = getCqrDefaultState("SPAWNER");
        if (state == null) state = Blocks.MOB_SPAWNER.getDefaultState();

        addSyntheticBlock(builder, x, y, z, state, tileEntityData);
        if (tileEntityData != null) extractSpawnerEntities(builder, tileEntityData, inhabitant);
    }

    private void addBoss(StructureNBTParser.ParsedStructureBuilder builder, Object preparable,
            int x, int y, int z, @Nullable InhabitantDefinition inhabitant) throws ReflectionException {
        Object bossTagObject = ReflectionHelper.invokeRequired(preparable, "getBossTag");
        NBTTagCompound bossTag = bossTagObject instanceof NBTTagCompound ? (NBTTagCompound) bossTagObject : null;
        IBlockState state = getCqrDefaultState("BOSS_BLOCK");
        if (state != null) addSyntheticBlock(builder, x, y, z, state, null);

        extractBossEntities(builder, bossTag, inhabitant);
    }

    private void extractSpawnerEntities(StructureNBTParser.StructureContentSink builder,
            NBTTagCompound tileEntityData, @Nullable InhabitantDefinition inhabitant) {
        if (tileEntityData.hasKey("inventory", Constants.NBT.TAG_COMPOUND)) {
            NBTTagList items = tileEntityData.getCompoundTag("inventory")
                .getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound itemTag = items.getCompoundTagAt(i);
                if (!itemTag.hasKey("tag", Constants.NBT.TAG_COMPOUND)) continue;

                NBTTagCompound stackTag = itemTag.getCompoundTag("tag");
                if (!stackTag.hasKey("EntityIn", Constants.NBT.TAG_COMPOUND)) continue;

                addEntityIdsFromTag(builder, stackTag.getCompoundTag("EntityIn"), inhabitant, true);
            }
            return;
        }

        extractVanillaSpawnerEntities(builder, tileEntityData, inhabitant);
    }

    private void extractVanillaSpawnerEntities(StructureNBTParser.StructureContentSink builder,
            NBTTagCompound tileEntityData, @Nullable InhabitantDefinition inhabitant) {
        for (ResourceLocation foundId : StructureNBTParser.collectSpawnerEntityIds(tileEntityData)) {
            addEntityId(builder, foundId.toString(), inhabitant, true);
        }
    }

    private void extractBossEntities(StructureNBTParser.StructureContentSink builder,
            @Nullable NBTTagCompound bossTag, @Nullable InhabitantDefinition inhabitant) {
        if (bossTag != null && !bossTag.isEmpty()) {
            addEntityIdsFromTag(builder, bossTag, inhabitant, false);
            return;
        }

        if (inhabitant == null) return;
        if (!inhabitant.bossIds.isEmpty()) {
            builder.addEntity(CQR_DUMMY_BOSS_ENTITY, false);
            return;
        }

        if (!inhabitant.entityIds.isEmpty()) builder.addEntity(CQR_DUMMY_ENTITY, false);
    }

    private void addEntityIdsFromTag(StructureNBTParser.StructureContentSink builder, NBTTagCompound entityTag,
            @Nullable InhabitantDefinition inhabitant, boolean spawner) {
        addEntityId(builder, entityTag.getString("id"), inhabitant, spawner);
    }

    private void addEntityId(StructureNBTParser.StructureContentSink builder, String entityIdText,
            @Nullable InhabitantDefinition inhabitant, boolean spawner) {
        if (entityIdText.isEmpty()) return;

        ResourceLocation entityId = new ResourceLocation(entityIdText);
        if (!CQR_DUMMY_ENTITY.equals(entityId)) {
            if (spawner || StructureNBTParser.shouldIncludeStructureEntity(entityId)) {
                builder.addEntity(entityId, spawner);
            }
            return;
        }

        if (inhabitant == null) return;

        // Keep the placeholder until the final aggregation pass so the total count stays stable.
        builder.addEntity(CQR_DUMMY_ENTITY, spawner);
    }

    @Nullable
    private IBlockState tryGetState(Object preparable) {
        try {
            Object value = ReflectionHelper.invokeRequired(preparable, "getState");
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectionException e) {
            return null;
        }
    }

    @Nullable
    private NBTTagCompound tryGetTileEntityData(Object preparable) {
        try {
            Object value = ReflectionHelper.invokeRequired(preparable, "getTileEntityData");
            return value instanceof NBTTagCompound ? (NBTTagCompound) value : null;
        } catch (ReflectionException e) {
            return null;
        }
    }

    private void addStandardBlock(StructureNBTParser.ParsedStructureBuilder builder, int x, int y, int z,
            IBlockState state, @Nullable NBTTagCompound tileEntityData,
            @Nullable InhabitantDefinition inhabitant) {
        addSyntheticBlock(builder, x, y, z, state, tileEntityData);
        if (tileEntityData == null) return;

        if (state.getBlock() == Blocks.MOB_SPAWNER) {
            extractSpawnerEntities(builder, tileEntityData, inhabitant);
            return;
        }

        StructureNBTParser.handleDefaultBlockEntity(builder, state, state.getBlock(), tileEntityData.copy());
    }

    private void addSyntheticBlock(StructureNBTParser.ParsedStructureBuilder builder, int x, int y, int z,
            @Nullable IBlockState state, @Nullable NBTTagCompound tileEntityData) {
        if (!shouldIncludeState(state)) return;

        builder.addBlockCount(
            StructureNBTParser.createDisplayedBlockKey(
                state,
                StructureNBTParser.createDisplayFluid(state),
                StructureNBTParser.createDisplayStack(state, tileEntityData)
            ),
            state,
            tileEntityData
        );
        builder.setLayerBlock(x, y, z, state, tileEntityData);
    }

    private boolean shouldIncludeState(@Nullable IBlockState state) {
        if (state == null) return false;
        if (StructureNBTParser.isInvisibleBlock(state.getBlock())) return false;

        ResourceLocation blockId = state.getBlock().getRegistryName();
        if (blockId != null && CQR_NULL_BLOCKS.contains(blockId)) return false;

        return true;
    }

    @Nullable
    private IBlockState getCqrDefaultState(String fieldName) {
        IBlockState cached = cqrBlockStates.get(fieldName);
        if (cached != null) return cached;

        try {
            Object block = ReflectionHelper.getStaticField(cqrBlocksClass, fieldName);
            if (!(block instanceof Block)) return null;

            IBlockState state = ((Block) block).getDefaultState();
            cqrBlockStates.put(fieldName, state);
            return state;
        } catch (ReflectionException e) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private IBlockState parseBlockStateString(@Nullable String rawValue) {
        if (rawValue == null) return null;

        String value = rawValue.trim();
        if (value.isEmpty()) return null;

        int meta = 0;
        int lastColon = value.lastIndexOf(':');
        if (lastColon > 0) {
            String metaCandidate = value.substring(lastColon + 1);
            try {
                meta = Integer.parseInt(metaCandidate);
                value = value.substring(0, lastColon);
            } catch (NumberFormatException ignored) {
            }
        }

        Block block = Block.REGISTRY.getObject(new ResourceLocation(value.toLowerCase(Locale.ROOT)));

        try {
            return block.getStateFromMeta(meta);
        } catch (Exception e) {
            return block.getDefaultState();
        }
    }

    private ScanOutcome scanDungeons(World world, DungeonDefinition dungeon, BlockPos origin, int maxResults) {
        List<BlockPos> positions = new ArrayList<>();
        int originChunkX = origin.getX() >> CHUNK_COORDINATE_SHIFT;
        int originChunkZ = origin.getZ() >> CHUNK_COORDINATE_SHIFT;
        long startTime = System.currentTimeMillis();

        for (int radius = 0; radius <= dungeon.searchRadiusChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if (System.currentTimeMillis() - startTime > MAX_SCAN_TIME_MS) {
                        return new ScanOutcome(positions, true);
                    }

                    int chunkX = originChunkX + dx;
                    int chunkZ = originChunkZ + dz;
                    String foundName = getDungeonAt(world, chunkX, chunkZ);
                    if (!dungeon.rawName.equalsIgnoreCase(foundName)) continue;

                    positions.add(new BlockPos((chunkX << CHUNK_COORDINATE_SHIFT) + 8, 0,
                        (chunkZ << CHUNK_COORDINATE_SHIFT) + 8));
                    if (positions.size() >= maxResults) return new ScanOutcome(positions, false);
                }
            }
        }

        return new ScanOutcome(positions, false);
    }

    @Nullable
    private String getDungeonAt(World world, int chunkX, int chunkZ) {
        try {
            Object dungeon = ReflectionHelper.invokeStaticRequired(worldDungeonGeneratorClass, "getDungeonAt",
                new Class<?>[]{World.class, int.class, int.class}, world, chunkX, chunkZ);
            if (dungeon == null) return null;

            Object value = ReflectionHelper.invokeRequired(dungeon, "getDungeonName");
            return value instanceof String ? (String) value : null;
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to query CQR dungeon at chunk {},{}", chunkX, chunkZ, e);
            return null;
        }
    }

    private InhabitantDefinition resolveInhabitant(String name) {
        InhabitantDefinition inhabitant = inhabitantsByName.get(name.toUpperCase(Locale.ROOT));
        if (inhabitant != null) return inhabitant;
        return defaultInhabitant;
    }

    private Set<DimensionInfo> resolveDimensions(Properties properties) {
        List<Integer> allowedDimensions = parseIntegerList(properties.getProperty("allowedDims"));
        boolean blacklist = parseBoolean(properties.getProperty("allowedDimsAsBlacklist"), false);

        if (allowedDimensions.isEmpty()) {
            if (blacklist) return null;
            return Collections.emptySet();
        }

        Set<DimensionInfo> dimensions = new LinkedHashSet<>();
        if (!blacklist) {
            for (Integer dimensionId : allowedDimensions) dimensions.add(new DimensionInfo(dimensionId));

            return dimensions;
        }

        for (Integer dimensionId : DimensionManager.getIDs()) {
            if (dimensionId == null || allowedDimensions.contains(dimensionId)) continue;
            dimensions.add(new DimensionInfo(dimensionId));
        }

        return dimensions.isEmpty() ? null : dimensions;
    }

    @Nullable
    private Set<Biome> resolveBiomes(Properties properties) {
        boolean allowedInAllBiomes = parseBoolean(properties.getProperty("allowedInAllBiomes"), false);
        List<ResourceLocation> allowedBiomes = parseBiomeIds(properties.getProperty("allowedBiomes"));
        List<ResourceLocation> disallowedBiomes = parseBiomeIds(properties.getProperty("disallowedBiomes"));
        List<String> allowedBiomeTypes = splitCsv(properties.getProperty("allowedBiomeTypes"));
        List<String> disallowedBiomeTypes = splitCsv(properties.getProperty("disallowedBiomeTypes"));

        if (allowedInAllBiomes && disallowedBiomes.isEmpty() && disallowedBiomeTypes.isEmpty()) return null;

        Set<Biome> matchingBiomes = new LinkedHashSet<>();
        for (Biome biome : Biome.REGISTRY) {
            if (biome == null || biome.getRegistryName() == null) continue;

            if (isValidBiome(biome, allowedInAllBiomes, allowedBiomes, allowedBiomeTypes,
                    disallowedBiomes, disallowedBiomeTypes)) {
                matchingBiomes.add(biome);
            }
        }

        return matchingBiomes.isEmpty() ? Collections.emptySet() : matchingBiomes;
    }

    private boolean isValidBiome(Biome biome, boolean allowedInAllBiomes,
            List<ResourceLocation> allowedBiomes, List<String> allowedBiomeTypes,
            List<ResourceLocation> disallowedBiomes, List<String> disallowedBiomeTypes) {
        ResourceLocation biomeId = biome.getRegistryName();
        Set<BiomeDictionary.Type> biomeTypes = BiomeDictionary.getTypes(biome);
        boolean allowed = allowedInAllBiomes;

        if (!allowed) {
            for (ResourceLocation allowedBiome : allowedBiomes) {
                if (allowedBiome.equals(biomeId)) {
                    allowed = true;
                    break;
                }
            }
        }

        if (!allowed) {
            for (BiomeDictionary.Type biomeType : biomeTypes) {
                for (String allowedType : allowedBiomeTypes) {
                    if (allowedType.equalsIgnoreCase(biomeType.getName())) {
                        allowed = true;
                        break;
                    }
                }

                if (allowed) break;
            }
        }

        if (!allowed) return false;

        for (ResourceLocation disallowedBiome : disallowedBiomes) {
            if (disallowedBiome.equals(biomeId)) return false;
        }

        for (BiomeDictionary.Type biomeType : biomeTypes) {
            for (String disallowedType : disallowedBiomeTypes) {
                if (disallowedType.equalsIgnoreCase(biomeType.getName())) return false;
            }
        }

        return true;
    }

    @Nullable
    private LocalizedText resolveRarity(@Nullable GridInfo gridInfo, int dungeonChance) {
        if (gridInfo == null) return null;

        double chunks = gridInfo.approximateRarityChunks();
        if (dungeonChance > 0 && dungeonChance < 100) chunks *= 100.0D / dungeonChance;

        return RarityTextHelper.oneInChunks(chunks);
    }

    private int resolveSearchRadiusChunks(@Nullable GridInfo gridInfo, boolean fixedPosition) {
        if (fixedPosition) return MAX_SEARCH_RADIUS_CHUNKS;
        if (gridInfo == null) return DEFAULT_SEARCH_RADIUS_CHUNKS;

        int radius = Math.max(DEFAULT_SEARCH_RADIUS_CHUNKS, gridInfo.distance * 6);
        return Math.min(radius, MAX_SEARCH_RADIUS_CHUNKS);
    }

    private boolean isDependencyMissing(@Nullable String dependencies) {
        for (String dependency : splitCsv(dependencies)) {
            if (!Loader.isModLoaded(dependency)) return true;
        }

        return false;
    }

    private List<File> collectStructureFiles(Properties properties, File structureRoot) {
        Set<File> files = new LinkedHashSet<>();

        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith("Folder")) continue;

            String relativeFolder = properties.getProperty(key, "").trim();
            if (relativeFolder.isEmpty()) continue;

            File folder = new File(structureRoot, relativeFolder);
            if (!folder.exists()) continue;

            collectNbtFiles(folder, files);
        }

        List<File> results = new ArrayList<>(files);
        results.sort((first, second) -> first.getAbsolutePath().compareToIgnoreCase(second.getAbsolutePath()));
        return results;
    }

    private void collectNbtFiles(File current, Set<File> target) {
        if (current.isFile()) {
            if (current.getName().endsWith(".nbt")) target.add(current);
            return;
        }

        File[] children = current.listFiles();
        if (children == null) return;

        for (File child : children) collectNbtFiles(child, target);
    }

    private List<File> listConfigFiles(@Nullable File root) {
        if (root == null || !root.exists()) return Collections.emptyList();

        List<File> files = new ArrayList<>();
        collectConfigFiles(root, files);
        files.sort((first, second) -> first.getAbsolutePath().compareToIgnoreCase(second.getAbsolutePath()));
        return files;
    }

    private void collectConfigFiles(File current, List<File> target) {
        if (current.isFile()) {
            String name = current.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".properties") || name.endsWith(".prop") || name.endsWith(".cfg")) {
                target.add(current);
            }
            return;
        }

        File[] children = current.listFiles();
        if (children == null) return;

        for (File child : children) collectConfigFiles(child, target);
    }

    @Nullable
    private Properties loadProperties(File file) {
        Properties properties = new Properties();
        try (FileInputStream stream = new FileInputStream(file)) {
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to read CQR config {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private List<String> splitCsv(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return Collections.emptyList();

        List<String> values = new ArrayList<>();
        for (String token : rawValue.split(",")) {
            String value = token.trim();
            if (!value.isEmpty()) values.add(value);
        }

        return values;
    }

    private List<String> splitSemicolon(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return Collections.emptyList();

        List<String> values = new ArrayList<>();
        for (String token : rawValue.split(";")) {
            String value = token.trim();
            if (!value.isEmpty()) values.add(value);
        }

        return values;
    }

    private List<Integer> parseIntegerList(@Nullable String rawValue) {
        List<Integer> values = new ArrayList<>();
        for (String token : splitCsv(rawValue)) {
            try {
                values.add(Integer.parseInt(token));
            } catch (NumberFormatException ignored) {
            }
        }

        return values;
    }

    private List<ResourceLocation> parseBiomeIds(@Nullable String rawValue) {
        List<ResourceLocation> values = new ArrayList<>();
        for (String token : splitCsv(rawValue)) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
            values.add(new ResourceLocation(normalized));
        }

        return values;
    }

    private boolean parseBoolean(@Nullable String rawValue, boolean defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return defaultValue;
        return Boolean.parseBoolean(rawValue.trim());
    }

    private int parseInt(@Nullable String rawValue, int defaultValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String createUniquePath(String rawName, Set<String> usedPaths) {
        String basePath = sanitizePath(rawName);
        String path = basePath;
        int suffix = 2;

        while (!usedPaths.add(path)) path = basePath + '_' + suffix++;

        return path;
    }

    private String sanitizePath(String rawName) {
        String normalized = rawName.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9/_\\.-]", "_")
            .replaceAll("_+", "_");
        return normalized.isEmpty() ? "unnamed_dungeon" : normalized;
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(0, index) : fileName;
    }

    private static final class DungeonDefinition {
        private final ResourceLocation id;
        private final String rawName;
        private final LocalizedText displayName;
        private final String generatorType;
        private final String dungeonMob;
        private final List<File> structureFiles;
        @Nullable
        private final Set<DimensionInfo> dimensions;
        @Nullable
        private final Set<Biome> biomes;
        @Nullable
        private final LocalizedText rarity;
        private final Properties properties;
        private final int searchRadiusChunks;

        private DungeonDefinition(ResourceLocation id, String rawName, LocalizedText displayName,
                String generatorType, String dungeonMob, List<File> structureFiles,
                @Nullable Set<DimensionInfo> dimensions, @Nullable Set<Biome> biomes,
                @Nullable LocalizedText rarity, Properties properties, int searchRadiusChunks) {
            this.id = id;
            this.rawName = rawName;
            this.displayName = displayName;
            this.generatorType = generatorType;
            this.dungeonMob = dungeonMob;
            this.structureFiles = structureFiles;
            this.dimensions = dimensions;
            this.biomes = biomes;
            this.rarity = rarity;
            this.properties = properties;
            this.searchRadiusChunks = searchRadiusChunks;
        }
    }

    private static final class InhabitantDefinition {
        private final String name;
        private final List<ResourceLocation> entityIds;
        private final List<ResourceLocation> bossIds;

        private InhabitantDefinition(String name, List<ResourceLocation> entityIds,
                List<ResourceLocation> bossIds) {
            this.name = name.toUpperCase(Locale.ROOT);
            this.entityIds = entityIds;
            this.bossIds = bossIds;
        }

        @Nullable
        private static InhabitantDefinition fromProperties(Properties properties) {
            String name = properties.getProperty("name", "").trim();
            if (name.isEmpty()) return null;

            return new InhabitantDefinition(name,
                parseResourceLocations(properties.getProperty("possibleEntities")),
                parseResourceLocations(properties.getProperty("possibleBosses")));
        }

        private static List<ResourceLocation> parseResourceLocations(@Nullable String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) return Collections.emptyList();

            List<ResourceLocation> values = new ArrayList<>();
            for (String token : rawValue.split(",")) {
                String value = token.trim();
                if (value.isEmpty()) continue;
                values.add(new ResourceLocation(value));
            }

            return values;
        }
    }

    private static final class GridInfo {
        private final int distance;
        private final int chance;

        private GridInfo(int distance, int chance) {
            this.distance = distance;
            this.chance = chance;
        }

        private double approximateRarityChunks() {
            double chunks = (long) distance * distance;
            if (chance > 0 && chance < 100) chunks *= 100.0D / chance;
            return chunks;
        }
    }

    private static final class ScanOutcome {
        private final List<BlockPos> positions;
        private final boolean timedOut;

        private ScanOutcome(List<BlockPos> positions, boolean timedOut) {
            this.positions = positions;
            this.timedOut = timedOut;
        }
    }
}