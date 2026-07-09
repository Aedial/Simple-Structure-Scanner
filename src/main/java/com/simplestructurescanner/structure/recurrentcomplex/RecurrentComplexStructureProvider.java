package com.simplestructurescanner.structure.recurrentcomplex;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.AbstractStructureProvider;
import com.simplestructurescanner.structure.DimensionInfo;
import com.simplestructurescanner.structure.LocalizedText;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureSearchOverrides;
import com.simplestructurescanner.structure.util.RarityTextHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;
import com.simplestructurescanner.structure.util.StructureContentAccumulator;
import com.simplestructurescanner.structure.util.StructurePreviewStitcher;
import com.simplestructurescanner.structure.util.StructureTranslationKeys;


/**
 * Structure provider for Recurrent Complex.
 * <p>
 * The mod mixes true world-entry structures with maze pieces, list entries,
 * and other subcomponents. This provider keeps merged blocks, entities, and
 * loot as a superset of what the archive can spawn, then builds a separate
 * stitched preview that stays deterministic even when RC would randomize the
 * final composition at runtime.
 */
public class RecurrentComplexStructureProvider extends AbstractStructureProvider {

    private static final String PROVIDER_ID = "reccomplex";
    private static final String MOD_ID = "reccomplex";
    private static final String MOD_NAME = "gui.structurescanner.provider.recurrentcomplex";

    private static final String STRUCTURE_REGISTRY_CLASS = "ivorius.reccomplex.world.gen.feature.structure.StructureRegistry";
    private static final String GENERIC_STRUCTURE_CLASS = "ivorius.reccomplex.world.gen.feature.structure.generic.GenericStructure";
    private static final String BLOCK_POSITIONS_CLASS = "ivorius.ivtoolkit.blocks.BlockPositions";
    private static final String NATURAL_GENERATION_CLASS = "ivorius.reccomplex.world.gen.feature.structure.generic.generation.NaturalGeneration";
    private static final String STATIC_GENERATION_CLASS = "ivorius.reccomplex.world.gen.feature.structure.generic.generation.StaticGeneration";
    private static final String LIST_GENERATION_CLASS = "ivorius.reccomplex.world.gen.feature.structure.generic.generation.ListGeneration";
    private static final String MAZE_GENERATION_CLASS = "ivorius.reccomplex.world.gen.feature.structure.generic.generation.MazeGeneration";

    private static final String SCRIPT_ID_MULTI = "multi";
    private static final String SCRIPT_ID_STRUCTURE_GENERATOR = "strucGen";
    private static final String SCRIPT_ID_MAZE_GENERATOR = "mazeGen";
    private static final String SCRIPT_ID_HOLDER = "holder";

    private static final int PREVIEW_SPACING = 2;
    private static final int MAZE_PREVIEW_COLUMNS = 3;

    private static final ResourceLocation GENERIC_SPACE_BLOCK_ID = new ResourceLocation(MOD_ID, "generic_space");
    private static final ResourceLocation GENERIC_SOLID_BLOCK_ID = new ResourceLocation(MOD_ID, "generic_solid");

    private static final StructureNBTParser.StructureParseExtension DEFAULT_PARSE_EXTENSION = new StructureNBTParser.StructureParseExtension() {
        @Override
        public boolean shouldCountBlock(@Nullable IBlockState state, @Nullable Block block) {
            if (isRcMetaBlock(block)) return false;

            return StructureNBTParser.StructureParseExtension.super.shouldCountBlock(state, block);
        }

        @Override
        public boolean shouldStoreLayerBlock(@Nullable IBlockState state, @Nullable Block block) {
            if (isRcMetaBlock(block)) return false;

            return StructureNBTParser.StructureParseExtension.super.shouldStoreLayerBlock(state, block);
        }
    };

    public RecurrentComplexStructureProvider() {
        super(PROVIDER_ID, MOD_ID, MOD_NAME, MOD_ID);
    }

    @Override
    public void postInit() {
        resetStructures();

        try {
            loadStructures();
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.error("Failed to load Recurrent Complex structures", e);
        }
    }

    private void loadStructures() throws ReflectionException {
        Object registry = getStructureRegistry();
        List<String> activeStructureIds = new ArrayList<>(getActiveStructureIds(registry));
        activeStructureIds.sort(String.CASE_INSENSITIVE_ORDER);

        for (String rawId : activeStructureIds) {
            try {
                loadStructure(registry, rawId);
            } catch (ReflectionException e) {
                SimpleStructureScanner.LOGGER.warn("Skipping Recurrent Complex structure '{}': {}", rawId, e.getMessage());
            }
        }

        SimpleStructureScanner.LOGGER.info("Loaded {} Recurrent Complex structures", structureInfos.size());
    }

    private void loadStructure(Object registry, String rawId) throws ReflectionException {
        Object structure = getActiveStructure(registry, rawId);
        if (structure == null) return;

        List<?> generationTypes = getGenerationTypes(structure);
        if (generationTypes == null || generationTypes.isEmpty()) return;

        GenerationMetadata metadata = collectGenerationMetadata(generationTypes);
        if (!metadata.isTopLevel()) return;

        ResourceLocation structureId = createStructureId(registry, rawId);
        if (StructureSearchOverrides.isStructureHidden(PROVIDER_ID, structureId)) return;
        if (structureInfos.containsKey(structureId)) {
            SimpleStructureScanner.LOGGER.warn("Skipping duplicate Recurrent Complex structure id: {}", structureId);
            return;
        }

        StructureInfo info = createStructureInfo(structureId, rawId, getStructureSize(structure));
        if (metadata.validBiomes != null) info.setValidBiomes(metadata.validBiomes);
        if (metadata.validDimensions != null) info.setValidDimensions(metadata.validDimensions);
        if (metadata.rarity != null) info.setRarity(metadata.rarity);

        knownStructures.add(structureId);
        structureInfos.put(structureId, info);

        try {
            populateStructureContents(info, registry, structure);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to merge Recurrent Complex contents for '{}': {}", structureId, e.getMessage());
        }

        try {
            populateStructurePreview(info, registry, structure);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to stitch Recurrent Complex preview for '{}': {}", structureId, e.getMessage());
        }
    }

    private Object getStructureRegistry() throws ReflectionException {
        Class<?> registryClass = ReflectionHelper.loadClassRequired(STRUCTURE_REGISTRY_CLASS);
        return ReflectionHelper.getStaticField(registryClass, "INSTANCE");
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getActiveStructureIds(Object registry) throws ReflectionException {
        return (Collection<String>) invokeRequired(registry, "activeIDs");
    }

    @Nullable
    private Object getActiveStructure(Object registry, String rawId) throws ReflectionException {
        return invokeRequired(registry, "getActive", new Class<?>[]{String.class}, rawId);
    }

    @Nullable
    private List<?> getGenerationTypes(Object structure) throws ReflectionException {
        return ReflectionHelper.getListField(structure, structure.getClass(), "generationTypes");
    }

    private ResourceLocation createStructureId(Object registry, String rawId) throws ReflectionException {
        Object status = invokeRequired(registry, "status", new Class<?>[]{String.class}, rawId);
        String domain = status != null ? (String) invokeRequired(status, "getDomain") : MOD_ID;
        return new ResourceLocation(domain, rawId);
    }

    private StructureInfo createStructureInfo(ResourceLocation structureId, String rawId, int[] size) {
        return new StructureInfo(
            structureId,
            LocalizedText.translatable(StructureTranslationKeys.normalizedStructureNameKey(MOD_ID, rawId)),
            PROVIDER_ID,
            size.length > 0 ? size[0] : 0,
            size.length > 1 ? size[1] : 0,
            size.length > 2 ? size[2] : 0
        );
    }

    private void populateStructureContents(StructureInfo info, Object registry, Object structure) throws ReflectionException {
        StructureContentAccumulator contents = new StructureContentAccumulator();
        mergeStructureContents(contents, registry, structure, new LinkedHashSet<>());
        contents.applyTo(info);
    }

    private void populateStructurePreview(StructureInfo info, Object registry, Object structure) throws ReflectionException {
        StructurePreviewStitcher preview = new StructurePreviewStitcher();
        mergeStructurePreview(preview, registry, structure, BlockPos.ORIGIN, PreviewTransform.identity(), new LinkedHashSet<>());

        List<StructureInfo.StructureLayer> layers = preview.buildLayers();
        if (!layers.isEmpty()) info.setLayers(layers);
    }

    private void mergeStructurePreview(StructurePreviewStitcher preview, Object registry, Object structure,
            BlockPos origin, PreviewTransform transform, Set<String> recursionStack) throws ReflectionException {
        String structureKey = getStructureKey(registry, structure);
        if (!recursionStack.add(structureKey)) {
            SimpleStructureScanner.LOGGER.debug("Skipping recursive Recurrent Complex preview reference {}", structureKey);
            return;
        }

        try {
            Object worldData = getWorldData(structure);
            if (worldData == null) return;

            mergeWorldDataPreview(preview, registry, worldData, normalizeSize(getStructureSize(structure)), origin, transform, recursionStack);
        } finally {
            recursionStack.remove(structureKey);
        }
    }

    private void mergeStructureContents(StructureNBTParser.StructureContentSink contents, Object registry,
            Object structure, Set<String> recursionStack) throws ReflectionException {
        String structureKey = getStructureKey(registry, structure);
        if (!recursionStack.add(structureKey)) {
            SimpleStructureScanner.LOGGER.debug("Skipping recursive Recurrent Complex structure reference {}", structureKey);
            return;
        }

        try {
            Object worldData = getWorldData(structure);
            if (worldData == null) return;

            mergeWorldData(contents, registry, worldData, recursionStack);
        } finally {
            recursionStack.remove(structureKey);
        }
    }

    @Nullable
    private Object getWorldData(Object structure) throws ReflectionException {
        try {
            return invokeRequired(structure, "constructWorldData");
        } catch (ReflectionException e) {
            Object worldDataCompound = ReflectionHelper.getField(structure, structure.getClass(), "worldDataCompound");
            if (!(worldDataCompound instanceof NBTTagCompound)) throw e;

            return constructWorldData((NBTTagCompound) worldDataCompound);
        }
    }

    private Object constructWorldData(NBTTagCompound worldDataCompound) throws ReflectionException {
        try {
            Class<?> genericStructureClass = ReflectionHelper.loadClassRequired(GENERIC_STRUCTURE_CLASS);
            Object structure = genericStructureClass.getDeclaredConstructor().newInstance();
            setFieldRequired(structure, genericStructureClass, "worldDataCompound", worldDataCompound.copy());
            return invokeRequired(structure, "constructWorldData");
        } catch (ReflectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ReflectionException("Failed to construct Recurrent Complex world data", e);
        }
    }

    private void mergeWorldData(StructureNBTParser.StructureContentSink contents, Object registry,
            Object worldData, Set<String> recursionStack) throws ReflectionException {
        Object blockCollection = ReflectionHelper.getField(worldData, worldData.getClass(), "blockCollection");
        List<NBTTagCompound> tileEntities = getCompoundListField(worldData, "tileEntities");
        List<NBTTagCompound> entities = getCompoundListField(worldData, "entities");

        Set<BlockPos> scriptPositions = collectScriptPositions(tileEntities);
        mergeBlockCollection(contents, blockCollection, scriptPositions);

        for (NBTTagCompound tileEntity : tileEntities) {
            if (tileEntity == null) continue;

            if (isScriptTileEntity(tileEntity)) {
                mergeWorldScripts(contents, registry, tileEntity.getCompoundTag("script"), recursionStack);
                continue;
            }

            IBlockState state = getBlockState(blockCollection, getTileEntityPos(tileEntity));
            NBTTagCompound sanitizedTileEntity = tileEntity.copy();
            stripGeneratingItems(contents, state, sanitizedTileEntity);
            Block block = state != null ? state.getBlock() : null;
            StructureNBTParser.handleDefaultBlockEntity(contents, state, block, sanitizedTileEntity);
        }

        for (NBTTagCompound entity : entities) {
            if (entity == null) continue;
            StructureNBTParser.handleDefaultEntity(contents, entity);
        }
    }

    private void stripGeneratingItems(StructureNBTParser.StructureContentSink contents,
            @Nullable IBlockState state, NBTTagCompound tileEntity) {
        if (!tileEntity.hasKey("Items", Constants.NBT.TAG_LIST)) return;

        NBTTagList items = tileEntity.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        NBTTagList remainingItems = new NBTTagList();
        boolean removedGenerator = false;

        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound itemTag = items.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(itemTag);

            if (!RecurrentComplexLootResolver.isGeneratingItem(stack)) {
                remainingItems.appendTag(itemTag.copy());
                continue;
            }

            StructureInfo.LootEntry generatedLoot = RecurrentComplexLootResolver.createLootEntry(state, stack);
            if (generatedLoot != null) contents.addLootEntry(generatedLoot);
            removedGenerator = true;
        }

        if (!removedGenerator) return;
        if (remainingItems.tagCount() > 0) {
            tileEntity.setTag("Items", remainingItems);
            return;
        }

        tileEntity.removeTag("Items");
    }

    private void mergeWorldDataPreview(StructurePreviewStitcher preview, Object registry, Object worldData,
            int[] size, BlockPos origin, PreviewTransform transform, Set<String> recursionStack) throws ReflectionException {
        Object blockCollection = ReflectionHelper.getField(worldData, worldData.getClass(), "blockCollection");
        int[] worldDataSize = hasUsableSize(size) ? size : getBlockCollectionSize(blockCollection);
        List<NBTTagCompound> tileEntities = getCompoundListField(worldData, "tileEntities");

        Set<BlockPos> scriptPositions = collectScriptPositions(tileEntities);
        addPreviewBlockCollection(preview, blockCollection, worldDataSize, origin, transform, scriptPositions);

        for (NBTTagCompound tileEntity : tileEntities) {
            if (!isScriptTileEntity(tileEntity)) continue;

            BlockPos localPos = getTileEntityPos(tileEntity);
            BlockPos worldPos = offsetPos(origin, transform.applyLocal(localPos, worldDataSize));
            mergeWorldScriptsPreview(preview, registry, tileEntity.getCompoundTag("script"), worldPos, transform, recursionStack);
        }
    }

    private static boolean isRcMetaBlock(@Nullable Block block) {
        if (block == null) return false;

        ResourceLocation blockId = block.getRegistryName();
        if (blockId == null) return false;

        // RC stores editor-time placeholders like Negative Space, Natural Air, Floor, and Barrier
        // in these two typed blocks. They should not leak into the final block list or preview.
        return GENERIC_SPACE_BLOCK_ID.equals(blockId) || GENERIC_SOLID_BLOCK_ID.equals(blockId);
    }

    private void mergeBlockCollection(StructureNBTParser.StructureContentSink contents, @Nullable Object blockCollection,
            Set<BlockPos> scriptPositions) throws ReflectionException {
        if (blockCollection == null) return;

        Object area = invokeRequired(blockCollection, "area");
        if (!(area instanceof Iterable)) {
            throw new ReflectionException("Unexpected Recurrent Complex block area payload: " + area);
        }

        // Script blocks are control nodes that spawn child structures, so counting them would pollute the merged metadata.
        for (Object posObject : (Iterable<?>) area) {
            if (!(posObject instanceof BlockPos)) continue;

            BlockPos pos = (BlockPos) posObject;
            if (scriptPositions.contains(pos)) continue;

            IBlockState state = getBlockState(blockCollection, pos);
            Block block = state != null ? state.getBlock() : null;
            if (!DEFAULT_PARSE_EXTENSION.shouldCountBlock(state, block)) continue;

            contents.addBlockCount(DEFAULT_PARSE_EXTENSION.getBlockCountKey(state, block, null), state);
        }
    }

    private void addPreviewBlockCollection(StructurePreviewStitcher preview, @Nullable Object blockCollection,
            int[] size, BlockPos origin, PreviewTransform transform, Set<BlockPos> scriptPositions) throws ReflectionException {
        if (blockCollection == null) return;

        Object area = invokeRequired(blockCollection, "area");
        if (!(area instanceof Iterable)) {
            throw new ReflectionException("Unexpected Recurrent Complex block area payload: " + area);
        }

        for (Object posObject : (Iterable<?>) area) {
            if (!(posObject instanceof BlockPos)) continue;

            BlockPos localPos = (BlockPos) posObject;
            if (scriptPositions.contains(localPos)) continue;

            IBlockState state = getBlockState(blockCollection, localPos);
            Block block = state != null ? state.getBlock() : null;
            if (!DEFAULT_PARSE_EXTENSION.shouldStoreLayerBlock(state, block)) continue;

            preview.setBlock(offsetPos(origin, transform.applyLocal(localPos, size)), state);
        }
    }

    @Nullable
    private IBlockState getBlockState(@Nullable Object blockCollection, BlockPos pos) throws ReflectionException {
        if (blockCollection == null) return null;

        Object value = invokeRequired(blockCollection, "getBlockState", new Class<?>[]{BlockPos.class}, pos);
        if (value == null || value instanceof IBlockState) return (IBlockState) value;

        throw new ReflectionException("Unexpected Recurrent Complex block state payload: " + value);
    }

    private List<NBTTagCompound> getCompoundListField(Object target, String fieldName) throws ReflectionException {
        List<?> values = ReflectionHelper.getListField(target, target.getClass(), fieldName);
        if (values == null || values.isEmpty()) return Collections.emptyList();

        List<NBTTagCompound> compounds = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof NBTTagCompound) compounds.add((NBTTagCompound) value);
        }

        return compounds;
    }

    private Set<BlockPos> collectScriptPositions(List<NBTTagCompound> tileEntities) {
        Set<BlockPos> positions = new HashSet<>();
        for (NBTTagCompound tileEntity : tileEntities) {
            if (!isScriptTileEntity(tileEntity)) continue;
            positions.add(getTileEntityPos(tileEntity));
        }

        return positions;
    }

    private boolean isScriptTileEntity(@Nullable NBTTagCompound tileEntity) {
        return tileEntity != null && tileEntity.hasKey("script", Constants.NBT.TAG_COMPOUND);
    }

    private BlockPos getTileEntityPos(NBTTagCompound tileEntity) {
        return new BlockPos(tileEntity.getInteger("x"), tileEntity.getInteger("y"), tileEntity.getInteger("z"));
    }

    private void mergeWorldScriptsPreview(StructurePreviewStitcher preview, Object registry,
            NBTTagCompound scriptData, BlockPos origin, PreviewTransform transform,
            Set<String> recursionStack) throws ReflectionException {
        if (!scriptData.hasKey("scripts", Constants.NBT.TAG_LIST)) return;

        NBTTagList scripts = scriptData.getTagList("scripts", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < scripts.tagCount(); i++) {
            NBTTagCompound scriptTag = scripts.getCompoundTagAt(i);
            if (!scriptTag.hasKey("data", Constants.NBT.TAG_COMPOUND)) continue;

            String scriptId = scriptTag.getString("id");
            NBTTagCompound data = scriptTag.getCompoundTag("data");

            if (SCRIPT_ID_MULTI.equals(scriptId)) {
                mergeWorldScriptsPreview(preview, registry, data, origin, transform, recursionStack);
                continue;
            }

            if (SCRIPT_ID_STRUCTURE_GENERATOR.equals(scriptId)) {
                mergeStructureGeneratorPreview(preview, registry, data, origin, transform, recursionStack);
                continue;
            }

            if (SCRIPT_ID_MAZE_GENERATOR.equals(scriptId)) {
                mergeMazePreview(preview, registry, data, origin, transform, recursionStack);
                continue;
            }

            if (SCRIPT_ID_HOLDER.equals(scriptId)) {
                mergeHolderPreview(preview, registry, data, origin, transform, recursionStack);
            }
        }
    }

    private void mergeWorldScripts(StructureNBTParser.StructureContentSink contents, Object registry,
            NBTTagCompound scriptData, Set<String> recursionStack) throws ReflectionException {
        if (!scriptData.hasKey("scripts", Constants.NBT.TAG_LIST)) return;

        NBTTagList scripts = scriptData.getTagList("scripts", Constants.NBT.TAG_COMPOUND);

        // RC composes larger structures by chaining world scripts; follow that graph and union the referenced content.
        for (int i = 0; i < scripts.tagCount(); i++) {
            NBTTagCompound scriptTag = scripts.getCompoundTagAt(i);
            if (!scriptTag.hasKey("data", Constants.NBT.TAG_COMPOUND)) continue;

            String scriptId = scriptTag.getString("id");
            NBTTagCompound data = scriptTag.getCompoundTag("data");

            if (SCRIPT_ID_MULTI.equals(scriptId)) {
                mergeWorldScripts(contents, registry, data, recursionStack);
                continue;
            }

            if (SCRIPT_ID_STRUCTURE_GENERATOR.equals(scriptId)) {
                mergeStructureGeneratorScript(contents, registry, data, recursionStack);
                continue;
            }

            if (SCRIPT_ID_MAZE_GENERATOR.equals(scriptId)) {
                mergeMazeScript(contents, registry, data, recursionStack);
                continue;
            }

            if (SCRIPT_ID_HOLDER.equals(scriptId)) {
                mergeHolderScript(contents, registry, data, recursionStack);
            }
        }
    }

    private void mergeStructureGeneratorScript(StructureNBTParser.StructureContentSink contents, Object registry,
            NBTTagCompound data, Set<String> recursionStack) throws ReflectionException {
        boolean simpleMode = !data.hasKey("simpleMode", Constants.NBT.TAG_BYTE) || data.getBoolean("simpleMode");

        if (simpleMode) {
            NBTTagList structures = data.getTagList("structures", Constants.NBT.TAG_STRING);
            for (int i = 0; i < structures.tagCount(); i++) {
                String structureName = structures.getStringTagAt(i);
                if (structureName.isEmpty()) continue;

                Object childStructure = getStructureById(registry, structureName);
                if (childStructure != null) mergeStructureContents(contents, registry, childStructure, recursionStack);
            }

            return;
        }

        String structureListId = data.getString("structureListID");
        if (structureListId.isEmpty()) return;

        EnumFacing front = data.hasKey("front", Constants.NBT.TAG_STRING) ? EnumFacing.byName(data.getString("front")) : null;
        for (Object childStructure : getListStructures(registry, structureListId, front)) {
            mergeStructureContents(contents, registry, childStructure, recursionStack);
        }
    }

    private void mergeStructureGeneratorPreview(StructurePreviewStitcher preview, Object registry,
            NBTTagCompound data, BlockPos origin, PreviewTransform transform,
            Set<String> recursionStack) throws ReflectionException {
        boolean simpleMode = !data.hasKey("simpleMode", Constants.NBT.TAG_BYTE) || data.getBoolean("simpleMode");
        BlockPos structureShift = readRcBlockPos("structureShift", data);

        if (simpleMode) {
            NBTTagList structures = data.getTagList("structures", Constants.NBT.TAG_STRING);
            int previewOffsetX = 0;

            for (int i = 0; i < structures.tagCount(); i++) {
                String structureName = structures.getStringTagAt(i);
                if (structureName.isEmpty()) continue;

                Object childStructure = getStructureById(registry, structureName);
                if (childStructure == null) continue;

                PreviewTransform childTransform = buildSimplePreviewTransform(childStructure, data, transform);
                int[] childSize = normalizeSize(getStructureSize(childStructure));
                BlockPos combinedShift = addPos(structureShift, new BlockPos(previewOffsetX, 0, 0));
                BlockPos childOrigin = resolveChildOrigin(origin, combinedShift, transform, childTransform, childSize);

                mergeStructurePreview(preview, registry, childStructure, childOrigin, childTransform, recursionStack);
                previewOffsetX += getPreviewSpanX(childSize, childTransform) + PREVIEW_SPACING;
            }

            return;
        }

        String structureListId = data.getString("structureListID");
        if (structureListId.isEmpty()) return;

        EnumFacing front = data.hasKey("front", Constants.NBT.TAG_STRING) ? EnumFacing.byName(data.getString("front")) : null;
        int previewOffsetX = 0;
        for (ListStructureCandidate candidate : getListStructureCandidates(registry, structureListId, front)) {
            PreviewTransform childTransform = buildListPreviewTransform(candidate.structure, front, candidate.front, transform);
            int[] childSize = normalizeSize(getStructureSize(candidate.structure));
            BlockPos combinedShift = addPos(addPos(structureShift, candidate.shift), new BlockPos(previewOffsetX, 0, 0));
            BlockPos childOrigin = resolveChildOrigin(origin, combinedShift, transform, childTransform, childSize);

            mergeStructurePreview(preview, registry, candidate.structure, childOrigin, childTransform, recursionStack);
            previewOffsetX += getPreviewSpanX(childSize, childTransform) + PREVIEW_SPACING;
        }
    }

    private void mergeMazeScript(StructureNBTParser.StructureContentSink contents, Object registry,
            NBTTagCompound data, Set<String> recursionStack) throws ReflectionException {
        String mazeId = data.getString("mazeID");
        if (mazeId.isEmpty()) return;

        for (Object childStructure : getMazeStructures(registry, mazeId)) {
            mergeStructureContents(contents, registry, childStructure, recursionStack);
        }
    }

    private void mergeMazePreview(StructurePreviewStitcher preview, Object registry,
            NBTTagCompound data, BlockPos origin, PreviewTransform transform,
            Set<String> recursionStack) throws ReflectionException {
        String mazeId = data.getString("mazeID");
        if (mazeId.isEmpty()) return;

        List<Object> structures = getMazeStructures(registry, mazeId);
        if (structures.isEmpty()) return;

        BlockPos structureShift = readRcBlockPos("structureShift", data);
        int[] roomSize = getMazeRoomSize(data);
        int column = 0;
        int previewOffsetX = 0;
        int previewOffsetZ = 0;
        int rowDepth = 0;

        // Maze composition depends on runtime constraint solving, so the preview shows all candidate rooms
        // in a deterministic gallery instead of guessing a single generated maze that may never happen.
        for (Object childStructure : structures) {
            PreviewTransform childTransform = transform;
            int[] childSize = normalizeSize(getStructureSize(childStructure));
            BlockPos combinedShift = addPos(structureShift, new BlockPos(previewOffsetX, 0, previewOffsetZ));
            BlockPos childOrigin = resolveChildOrigin(origin, combinedShift, transform, childTransform, childSize);

            mergeStructurePreview(preview, registry, childStructure, childOrigin, childTransform, recursionStack);

            int[] previewSize = childTransform.applySize(childSize);
            rowDepth = Math.max(rowDepth, Math.max(previewSize[2], roomSize[2]));
            column++;

            if (column >= MAZE_PREVIEW_COLUMNS) {
                column = 0;
                previewOffsetX = 0;
                previewOffsetZ += rowDepth + PREVIEW_SPACING;
                rowDepth = 0;
                continue;
            }

            previewOffsetX += Math.max(previewSize[0], roomSize[0]) + PREVIEW_SPACING;
        }
    }

    private void mergeHolderScript(StructureNBTParser.StructureContentSink contents, Object registry,
            NBTTagCompound data, Set<String> recursionStack) throws ReflectionException {
        if (!data.hasKey("worldData", Constants.NBT.TAG_COMPOUND)) return;

        String holderKey = "holder:" + Integer.toHexString(System.identityHashCode(data));
        if (!recursionStack.add(holderKey)) return;

        try {
            Object worldData = constructWorldData(data.getCompoundTag("worldData"));
            mergeWorldData(contents, registry, worldData, recursionStack);
        } finally {
            recursionStack.remove(holderKey);
        }
    }

    private void mergeHolderPreview(StructurePreviewStitcher preview, Object registry,
            NBTTagCompound data, BlockPos origin, PreviewTransform transform,
            Set<String> recursionStack) throws ReflectionException {
        if (!data.hasKey("worldData", Constants.NBT.TAG_COMPOUND)) return;

        String holderKey = "holder:" + Integer.toHexString(System.identityHashCode(data));
        if (!recursionStack.add(holderKey)) return;

        try {
            Object worldData = constructWorldData(data.getCompoundTag("worldData"));
            int[] size = getWorldDataSize(worldData);
            BlockPos holderOrigin = resolveChildOrigin(origin, readRcBlockPos("origin", data), transform, transform, size);
            mergeWorldDataPreview(preview, registry, worldData, size, holderOrigin, transform, recursionStack);
        } finally {
            recursionStack.remove(holderKey);
        }
    }

    @Nullable
    private Object getStructureById(Object registry, String structureId) throws ReflectionException {
        return invokeRequired(registry, "get", new Class<?>[]{String.class}, structureId);
    }

    private List<Object> getListStructures(Object registry, String structureListId,
            @Nullable EnumFacing front) throws ReflectionException {
        Class<?> registryClass = ReflectionHelper.loadClassRequired(STRUCTURE_REGISTRY_CLASS);
        Class<?> listGenerationClass = ReflectionHelper.loadClassRequired(LIST_GENERATION_CLASS);
        Object stream = invokeStaticRequired(listGenerationClass, "structures",
            new Class<?>[]{registryClass, String.class, EnumFacing.class}, registry, structureListId, front);
        return getStructuresFromPairStream(stream);
    }

    private List<ListStructureCandidate> getListStructureCandidates(Object registry, String structureListId,
            @Nullable EnumFacing front) throws ReflectionException {
        Class<?> registryClass = ReflectionHelper.loadClassRequired(STRUCTURE_REGISTRY_CLASS);
        Class<?> listGenerationClass = ReflectionHelper.loadClassRequired(LIST_GENERATION_CLASS);
        Object stream = invokeStaticRequired(listGenerationClass, "structures",
            new Class<?>[]{registryClass, String.class, EnumFacing.class}, registry, structureListId, front);
        return getListStructureCandidatesFromPairStream(stream);
    }

    private List<Object> getMazeStructures(Object registry, String mazeId) throws ReflectionException {
        Class<?> registryClass = ReflectionHelper.loadClassRequired(STRUCTURE_REGISTRY_CLASS);
        Class<?> mazeGenerationClass = ReflectionHelper.loadClassRequired(MAZE_GENERATION_CLASS);
        Object stream = invokeStaticRequired(mazeGenerationClass, "structures",
            new Class<?>[]{registryClass, String.class}, registry, mazeId);
        return getStructuresFromPairStream(stream);
    }

    private List<Object> getStructuresFromPairStream(Object streamObject) throws ReflectionException {
        if (!(streamObject instanceof Stream)) {
            throw new ReflectionException("Unexpected Recurrent Complex structure pair stream payload: " + streamObject);
        }

        Stream<?> stream = (Stream<?>) streamObject;
        List<Object> structures = new ArrayList<>();
        try {
            stream.iterator().forEachRemaining(pair -> {
                try {
                    Object structure = invokeRequired(pair, "getLeft");
                    if (structure != null) structures.add(structure);
                } catch (ReflectionException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof ReflectionException) throw (ReflectionException) e.getCause();
            throw e;
        } finally {
            stream.close();
        }

        return structures;
    }

    private List<ListStructureCandidate> getListStructureCandidatesFromPairStream(Object streamObject) throws ReflectionException {
        if (!(streamObject instanceof Stream)) {
            throw new ReflectionException("Unexpected Recurrent Complex structure pair stream payload: " + streamObject);
        }

        Stream<?> stream = (Stream<?>) streamObject;
        List<ListStructureCandidate> candidates = new ArrayList<>();
        try {
            stream.iterator().forEachRemaining(pair -> {
                try {
                    Object structure = invokeRequired(pair, "getLeft");
                    Object generationInfo = invokeRequired(pair, "getRight");
                    if (structure == null || generationInfo == null) return;

                    BlockPos shift = (BlockPos) ReflectionHelper.getField(generationInfo, generationInfo.getClass(), "shift");
                    EnumFacing candidateFront = (EnumFacing) ReflectionHelper.getField(generationInfo, generationInfo.getClass(), "front");
                    candidates.add(new ListStructureCandidate(structure, shift != null ? shift : BlockPos.ORIGIN, candidateFront));
                } catch (ReflectionException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof ReflectionException) throw (ReflectionException) e.getCause();
            throw e;
        } finally {
            stream.close();
        }

        return candidates;
    }

    private String getStructureKey(Object registry, Object structure) throws ReflectionException {
        Object rawLocation = invokeRequired(registry, "resourceLocation", new Class<?>[]{Object.class}, structure);
        if (rawLocation != null) return rawLocation.toString();

        return structure.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(structure));
    }

    private void setFieldRequired(Object target, Class<?> ownerClass, String fieldName, Object value) throws ReflectionException {
        try {
            Field field = ownerClass.getField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new ReflectionException("Failed to set field '" + fieldName + "' on " + ownerClass.getName(), e);
        }
    }

    private int[] getStructureSize(Object structure) throws ReflectionException {
        Object size = invokeRequired(structure, "size");
        if (size instanceof int[]) return normalizeSize((int[]) size);

        throw new ReflectionException("Unexpected Recurrent Complex size payload: " + size);
    }

    private int[] getWorldDataSize(Object worldData) throws ReflectionException {
        Object blockCollection = ReflectionHelper.getField(worldData, worldData.getClass(), "blockCollection");
        return getBlockCollectionSize(blockCollection);
    }

    private int[] getBlockCollectionSize(@Nullable Object blockCollection) throws ReflectionException {
        if (blockCollection == null) return new int[]{0, 0, 0};

        int width = ReflectionHelper.getIntField(blockCollection, blockCollection.getClass(), "width");
        int height = ReflectionHelper.getIntField(blockCollection, blockCollection.getClass(), "height");
        int depth;

        try {
            depth = ReflectionHelper.getIntField(blockCollection, blockCollection.getClass(), "length");
        } catch (ReflectionException e) {
            depth = ReflectionHelper.getIntField(blockCollection, blockCollection.getClass(), "depth");
        }

        return new int[]{width, height, depth};
    }

    private BlockPos readRcBlockPos(String key, NBTTagCompound compound) throws ReflectionException {
        Class<?> blockPositionsClass = ReflectionHelper.loadClassRequired(BLOCK_POSITIONS_CLASS);
        Object value = invokeStaticRequired(blockPositionsClass, "readFromNBT",
            new Class<?>[]{String.class, NBTTagCompound.class}, key, compound);
        if (value instanceof BlockPos) return (BlockPos) value;

        throw new ReflectionException("Unexpected Recurrent Complex BlockPos payload: " + value);
    }

    private PreviewTransform buildSimplePreviewTransform(Object structure, NBTTagCompound data,
            PreviewTransform parentTransform) throws ReflectionException {
        boolean rotatable = canStructureRotate(structure);
        boolean mirrorable = canStructureMirror(structure);
        boolean hasExplicitRotation = data.hasKey("structureRotation");
        boolean hasExplicitMirror = data.hasKey("structureMirror");

        int rotation = 0;
        boolean mirror = false;

        if (rotatable && hasExplicitRotation) rotation = normalizeRotation(parentTransform.rotation + data.getInteger("structureRotation"));
        if (mirrorable && hasExplicitMirror) mirror = parentTransform.mirrorX != data.getBoolean("structureMirror");

        return new PreviewTransform(rotation, mirror);
    }

    private PreviewTransform buildListPreviewTransform(Object structure, @Nullable EnumFacing front,
            @Nullable EnumFacing targetFront, PreviewTransform parentTransform) throws ReflectionException {
        if (!canStructureRotate(structure)) return PreviewTransform.identity();
        if (front == null || targetFront == null) return PreviewTransform.identity();
        if (front.getAxis().isVertical() || targetFront.getAxis().isVertical()) return PreviewTransform.identity();

        EnumFacing currentFront = parentTransform.applyFacing(front);
        int rotations = getClockwiseRotations(currentFront, targetFront);
        return new PreviewTransform(rotations, false);
    }

    private boolean canStructureRotate(Object structure) throws ReflectionException {
        return invokeBooleanRequired(structure, "isRotatable", new Class<?>[0]);
    }

    private boolean canStructureMirror(Object structure) throws ReflectionException {
        return invokeBooleanRequired(structure, "isMirrorable", new Class<?>[0]);
    }

    private int getClockwiseRotations(EnumFacing from, EnumFacing to) {
        int rotations = 0;
        EnumFacing current = from;
        while (current != to && rotations < 4) {
            current = current.rotateY();
            rotations++;
        }

        return rotations % 4;
    }

    private int[] getMazeRoomSize(NBTTagCompound data) {
        int[] roomSize = data.getIntArray("roomSize");
        if (roomSize.length < 3) return new int[]{3, 5, 3};

        return new int[]{roomSize[0], roomSize[1], roomSize[2]};
    }

    private BlockPos resolveChildOrigin(BlockPos origin, BlockPos localShift,
            PreviewTransform parentTransform, PreviewTransform childTransform, int[] childSize) {
        BlockPos shiftedOrigin = offsetPos(origin, parentTransform.applyVector(localShift));
        return subtractPos(shiftedOrigin, childTransform.originOffset(childSize));
    }

    private BlockPos addPos(BlockPos first, BlockPos second) {
        return first.add(second.getX(), second.getY(), second.getZ());
    }

    private BlockPos offsetPos(BlockPos origin, BlockPos offset) {
        return origin.add(offset.getX(), offset.getY(), offset.getZ());
    }

    private BlockPos subtractPos(BlockPos origin, BlockPos offset) {
        return origin.add(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    private int getPreviewSpanX(int[] size, PreviewTransform transform) {
        return Math.max(transform.applySize(size)[0], 1);
    }

    private boolean hasUsableSize(int[] size) {
        return size.length >= 3 && (size[0] > 0 || size[1] > 0 || size[2] > 0);
    }

    private int[] normalizeSize(@Nullable int[] size) {
        if (size == null) return new int[]{0, 0, 0};

        return new int[]{
            size.length > 0 ? size[0] : 0,
            size.length > 1 ? size[1] : 0,
            size.length > 2 ? size[2] : 0
        };
    }

    private GenerationMetadata collectGenerationMetadata(List<?> generationTypes) throws ReflectionException {
        Set<Biome> biomes = new HashSet<>();
        Set<DimensionInfo> dimensions = new HashSet<>();
        boolean hasNaturalGeneration = false;
        boolean hasStaticGeneration = false;

        for (Object generationType : generationTypes) {
            String className = generationType.getClass().getName();

            if (NATURAL_GENERATION_CLASS.equals(className)) {
                hasNaturalGeneration = true;
                biomes.addAll(resolveNaturalBiomes(generationType));
                dimensions.addAll(resolveNaturalDimensions(generationType));
                continue;
            }

            if (STATIC_GENERATION_CLASS.equals(className)) {
                hasStaticGeneration = true;
                dimensions.addAll(resolveStaticDimensions(generationType));
            }
        }

        if (!hasNaturalGeneration && !hasStaticGeneration) {
            return GenerationMetadata.skip();
        }

        return new GenerationMetadata(
            true,
            !biomes.isEmpty() ? biomes : null,
            !dimensions.isEmpty() ? dimensions : null,
            hasStaticGeneration && !hasNaturalGeneration ? RarityTextHelper.fixedPosition() : null
        );
    }

    private Set<Biome> resolveNaturalBiomes(Object generationType) throws ReflectionException {
        Set<Biome> matchingBiomes = new HashSet<>();
        List<WorldProvider> providers = getRegisteredDimensionProviders();
        if (providers.isEmpty()) return matchingBiomes;

        for (Biome biome : Biome.REGISTRY) {
            if (biome == null) continue;

            for (WorldProvider provider : providers) {
                if (getNaturalGenerationWeight(generationType, provider, biome) <= 0.0D) continue;

                matchingBiomes.add(biome);
                break;
            }
        }

        return matchingBiomes;
    }

    private Set<DimensionInfo> resolveNaturalDimensions(Object generationType) throws ReflectionException {
        Set<DimensionInfo> matchingDimensions = new HashSet<>();

        for (WorldProvider provider : getRegisteredDimensionProviders()) {
            for (Biome biome : Biome.REGISTRY) {
                if (biome == null) continue;
                if (getNaturalGenerationWeight(generationType, provider, biome) <= 0.0D) continue;

                matchingDimensions.add(new DimensionInfo(provider.getDimension()));
                break;
            }
        }

        return matchingDimensions;
    }

    private Set<DimensionInfo> resolveStaticDimensions(Object generationType) throws ReflectionException {
        Set<DimensionInfo> matchingDimensions = new HashSet<>();
        Object dimensionExpression = ReflectionHelper.getField(generationType, generationType.getClass(), "dimensionExpression");
        if (dimensionExpression == null) return matchingDimensions;

        for (WorldProvider provider : getRegisteredDimensionProviders()) {
            boolean matches = invokeBooleanRequired(dimensionExpression, "test", new Class<?>[]{WorldProvider.class}, provider);
            if (matches) matchingDimensions.add(new DimensionInfo(provider.getDimension()));
        }

        return matchingDimensions;
    }

    private double getNaturalGenerationWeight(Object generationType, WorldProvider provider, Biome biome) throws ReflectionException {
        Object value = invokeRequired(generationType, "getGenerationWeight", new Class<?>[]{WorldProvider.class, Biome.class}, provider, biome);
        if (value instanceof Number) return ((Number) value).doubleValue();

        throw new ReflectionException("Unexpected Recurrent Complex generation weight payload: " + value);
    }

    private List<WorldProvider> getRegisteredDimensionProviders() {
        Set<Integer> dimensionIds = new LinkedHashSet<>();
        Integer[] registered = DimensionManager.getIDs();
        if (registered == null) return Collections.emptyList();

        for (Integer dimensionId : registered) {
            if (dimensionId != null) dimensionIds.add(dimensionId);
        }

        List<WorldProvider> providers = new ArrayList<>();
        for (Integer dimensionId : dimensionIds) {
            try {
                providers.add(DimensionManager.createProviderFor(dimensionId));
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.debug("Failed to create Recurrent Complex dimension provider for {}", dimensionId, e);
            }
        }

        return providers;
    }

    private Object invokeRequired(Object target, String methodName) throws ReflectionException {
        return invokeRequired(target, methodName, new Class<?>[0]);
    }

    private Object invokeStaticRequired(Class<?> ownerClass, String methodName, Class<?>[] parameterTypes,
            Object... args) throws ReflectionException {
        try {
            Method method = ownerClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke static method '" + methodName + "' on " + ownerClass.getName(), e);
        }
    }

    private Object invokeRequired(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectionException {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
        }
    }

    private boolean invokeBooleanRequired(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectionException {
        Object value = invokeRequired(target, methodName, parameterTypes, args);
        if (value instanceof Boolean) return (Boolean) value;

        throw new ReflectionException("Unexpected boolean reflection payload from '" + methodName + "': " + value);
    }

    @Override
    public boolean canBeSearched(ResourceLocation structureId) {
        return false;
    }

    @Override
    @Nullable
    public StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        return null;
    }

    @Override
    @Nullable
    public List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        return null;
    }

    private static final class PreviewTransform {
        private final int rotation;
        private final boolean mirrorX;

        private PreviewTransform(int rotation, boolean mirrorX) {
            this.rotation = normalizeRotation(rotation);
            this.mirrorX = mirrorX;
        }

        private static PreviewTransform identity() {
            return new PreviewTransform(0, false);
        }

        private BlockPos applyLocal(BlockPos pos, int[] size) {
            int positionX = mirrorX ? size[0] - 1 - pos.getX() : pos.getX();

            switch (rotation) {
                case 0:
                    return new BlockPos(positionX, pos.getY(), pos.getZ());
                case 1:
                    return new BlockPos(size[2] - 1 - pos.getZ(), pos.getY(), positionX);
                case 2:
                    return new BlockPos(size[0] - 1 - positionX, pos.getY(), size[2] - 1 - pos.getZ());
                case 3:
                    return new BlockPos(pos.getZ(), pos.getY(), size[0] - 1 - positionX);
                default:
                    throw new IllegalStateException("Unexpected rotation " + rotation);
            }
        }

        private BlockPos applyVector(BlockPos pos) {
            int positionX = mirrorX ? -pos.getX() : pos.getX();

            switch (rotation) {
                case 0:
                    return new BlockPos(positionX, pos.getY(), pos.getZ());
                case 1:
                    return new BlockPos(-pos.getZ(), pos.getY(), positionX);
                case 2:
                    return new BlockPos(-positionX, pos.getY(), -pos.getZ());
                case 3:
                    return new BlockPos(pos.getZ(), pos.getY(), -positionX);
                default:
                    throw new IllegalStateException("Unexpected rotation " + rotation);
            }
        }

        private BlockPos originOffset(int[] size) {
            return applyLocal(BlockPos.ORIGIN, size);
        }

        private int[] applySize(int[] size) {
            if ((rotation & 1) == 0) return new int[]{size[0], size[1], size[2]};

            return new int[]{size[2], size[1], size[0]};
        }

        private EnumFacing applyFacing(EnumFacing facing) {
            BlockPos rotated = applyVector(new BlockPos(facing.getXOffset(), 0, facing.getZOffset()));
            return EnumFacing.getFacingFromVector(rotated.getX(), 0, rotated.getZ());
        }
    }

    private static final class ListStructureCandidate {
        private final Object structure;
        private final BlockPos shift;
        @Nullable
        private final EnumFacing front;

        private ListStructureCandidate(Object structure, BlockPos shift, @Nullable EnumFacing front) {
            this.structure = structure;
            this.shift = shift;
            this.front = front;
        }
    }

    private static int normalizeRotation(int rotation) {
        int normalized = rotation % 4;
        return normalized < 0 ? normalized + 4 : normalized;
    }

    private static final class GenerationMetadata {
        private final boolean topLevel;
        @Nullable
        private final Set<Biome> validBiomes;
        @Nullable
        private final Set<DimensionInfo> validDimensions;
        @Nullable
        private final LocalizedText rarity;

        private GenerationMetadata(boolean topLevel,
                @Nullable Set<Biome> validBiomes,
                @Nullable Set<DimensionInfo> validDimensions,
                @Nullable LocalizedText rarity) {
            this.topLevel = topLevel;
            this.validBiomes = validBiomes;
            this.validDimensions = validDimensions;
            this.rarity = rarity;
        }

        private static GenerationMetadata skip() {
            return new GenerationMetadata(false, null, null, null);
        }

        private boolean isTopLevel() {
            return topLevel;
        }
    }
}