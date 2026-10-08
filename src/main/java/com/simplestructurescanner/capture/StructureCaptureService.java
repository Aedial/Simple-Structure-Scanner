package com.simplestructurescanner.capture;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.BlockDisplayKey;
import com.simplestructurescanner.structure.StructureInfo.StructureLayer;


/**
 * Server-side capture builder for preview summaries and final structure NBT files.
 */
public final class StructureCaptureService {

    private static final String CAPTURE_DIRECTORY = "structurescanner-structures";
    private static final String CAPTURE_AUTHOR = "Simple Structure Scanner";
    private static final int DATA_VERSION = 1343;

    // The capture UI is configured against this frozen source snapshot. Reusing it for later
    // preview/save requests keeps the final output aligned with what the player reviewed.
    private static final Map<UUID, FrozenCapture> FROZEN_CAPTURES = new HashMap<>();

    private StructureCaptureService() {
    }

    @Nullable
    public static StructureCaptureSummary buildSummary(UUID playerId, World world, BlockPos firstCorner,
            BlockPos secondCorner) {
        FrozenCapture frozenCapture = captureSnapshot(world, firstCorner, secondCorner);
        StructureCaptureSummary summary = buildSummary(frozenCapture);
        if (summary == null) {
            clearFrozenCapture(playerId);
            return null;
        }

        FROZEN_CAPTURES.put(playerId, frozenCapture);
        return summary;
    }

    @Nullable
    public static StructureCaptureSummary buildSummary(World world, BlockPos firstCorner, BlockPos secondCorner) {
        return buildSummary(captureSnapshot(world, firstCorner, secondCorner));
    }

    @Nullable
    public static SaveResult saveCapture(UUID playerId, World world, BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) throws IOException {
        FrozenCapture frozenCapture = getOrCreateCapture(playerId, world, firstCorner, secondCorner);
        CapturedStructure capturedStructure = buildCapturedStructure(frozenCapture, exclusions);
        if (capturedStructure == null) return null;

        File captureFile = createCaptureFile(getCaptureDirectory(world));
        try (OutputStream stream = Files.newOutputStream(captureFile.toPath())) {
            CompressedStreamTools.writeCompressed(capturedStructure.getStructureNbt(), stream);
        }

        return new SaveResult(
            captureFile,
            capturedStructure.getSizeX(),
            capturedStructure.getSizeY(),
            capturedStructure.getSizeZ()
        );
    }

    @Nullable
    public static SaveResult saveCapture(World world, BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) throws IOException {
        CapturedStructure capturedStructure = buildCapturedStructure(world, firstCorner, secondCorner, exclusions);
        if (capturedStructure == null) return null;

        File captureFile = createCaptureFile(getCaptureDirectory(world));
        try (OutputStream stream = Files.newOutputStream(captureFile.toPath())) {
            CompressedStreamTools.writeCompressed(capturedStructure.getStructureNbt(), stream);
        }

        return new SaveResult(
            captureFile,
            capturedStructure.getSizeX(),
            capturedStructure.getSizeY(),
            capturedStructure.getSizeZ()
        );
    }

    @Nullable
    public static SaveResult saveMapCapture(String mapName, BlockPos captureOrigin, List<StructureLayer> layers,
            List<NBTTagCompound> entityData) throws IOException {
        CapturedStructure capturedStructure = buildMapCapturedStructure(captureOrigin, layers, entityData);
        if (capturedStructure == null) return null;

        File captureFile = createMapCaptureFile(getCaptureDirectory(null), mapName);
        try (OutputStream stream = Files.newOutputStream(captureFile.toPath())) {
            CompressedStreamTools.writeCompressed(capturedStructure.getStructureNbt(), stream);
        }

        return new SaveResult(
            captureFile,
            capturedStructure.getSizeX(),
            capturedStructure.getSizeY(),
            capturedStructure.getSizeZ()
        );
    }

    @Nullable
    public static CapturedStructure buildCapturedStructure(World world, BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) {
        return buildCapturedStructure(captureSnapshot(world, firstCorner, secondCorner), exclusions);
    }

    @Nullable
    public static NBTTagCompound buildRenderedPreviewNbt(UUID playerId, World world, BlockPos firstCorner,
            BlockPos secondCorner, StructureCaptureExclusions exclusions) {
        FrozenCapture frozenCapture = getOrCreateCapture(playerId, world, firstCorner, secondCorner);
        return buildRenderedPreviewNbt(frozenCapture, exclusions);
    }

    @Nullable
    public static NBTTagCompound buildRenderedPreviewNbt(World world, BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) {
        return buildRenderedPreviewNbt(captureSnapshot(world, firstCorner, secondCorner), exclusions);
    }

    public static void clearFrozenCapture(UUID playerId) {
        FROZEN_CAPTURES.remove(playerId);
    }

    @Nullable
    private static StructureCaptureSummary buildSummary(FrozenCapture frozenCapture) {
        ContentBounds contentBounds = new ContentBounds();
        Map<BlockDisplayKey, BlockAccumulator> blocks = new LinkedHashMap<>();
        Map<CaptureContainerKey, ContainerAccumulator> containers = new LinkedHashMap<>();

        for (FrozenBlock frozenBlock : frozenCapture.blocks.values()) {
            BlockPos blockPos = frozenBlock.worldPos;
            IBlockState state = frozenBlock.state;

            if (CaptureBlockHelper.contributesToBounds(state)) contentBounds.include(blockPos);

            if (CaptureBlockHelper.shouldShowInSummary(state)) {
                BlockDisplayKey blockKey = CaptureBlockHelper.createKey(state);
                BlockAccumulator accumulator = blocks.computeIfAbsent(blockKey, k -> new BlockAccumulator(k, state));

                accumulator.count++;
            }

            if (frozenBlock.tileData == null) continue;
            if (frozenBlock.lootTableId == null && frozenBlock.itemCount <= 0) continue;

            CaptureContainerKey containerKey = createContainerKey(state, frozenBlock.lootTableId);
            ContainerAccumulator accumulator = containers.computeIfAbsent(containerKey, k -> new ContainerAccumulator(k, state, frozenBlock.lootTableId));

            accumulator.containerCount++;
            accumulator.totalItemCount += frozenBlock.itemCount;
        }

        if (!contentBounds.hasContent()) return null;

        List<StructureCaptureSummary.BlockSummary> blockSummaries = new ArrayList<>();
        for (BlockAccumulator accumulator : blocks.values()) {
            blockSummaries.add(new StructureCaptureSummary.BlockSummary(accumulator.key, accumulator.representativeState, accumulator.count));
        }

        blockSummaries.sort((first, second) -> {
            int countCompare = Integer.compare(second.getCount(), first.getCount());
            if (countCompare != 0) return countCompare;

            return first.getSerializedKey().compareTo(second.getSerializedKey());
        });

        List<StructureCaptureSummary.ContainerSummary> containerSummaries = new ArrayList<>();
        for (ContainerAccumulator accumulator : containers.values()) {
            containerSummaries.add(new StructureCaptureSummary.ContainerSummary(
                accumulator.key,
                accumulator.representativeState,
                accumulator.lootTableId,
                accumulator.containerCount,
                accumulator.totalItemCount
            ));
        }

        containerSummaries.sort((first, second) -> {
            int countCompare = Integer.compare(second.getContainerCount(), first.getContainerCount());
            if (countCompare != 0) return countCompare;

            return first.getSerializedKey().compareTo(second.getSerializedKey());
        });

        return new StructureCaptureSummary(
            contentBounds.getSizeX(),
            contentBounds.getSizeY(),
            contentBounds.getSizeZ(),
            blockSummaries,
            collectEntitySummaries(frozenCapture, contentBounds),
            containerSummaries
        );
    }

    @Nullable
    private static CapturedStructure buildCapturedStructure(FrozenCapture frozenCapture,
            StructureCaptureExclusions exclusions) {
        ContentBounds finalBounds = collectFinalBounds(frozenCapture, exclusions);
        if (!finalBounds.hasContent()) return null;

        long startTime = System.currentTimeMillis();
        NBTTagCompound structureNbt = writeStructureNbt(frozenCapture, finalBounds, exclusions);
        long endTime = System.currentTimeMillis();
        SimpleStructureScanner.LOGGER.debug("Structure NBT generation took %s ms", endTime - startTime);

        return new CapturedStructure(
            structureNbt,
            finalBounds.getSizeX(),
            finalBounds.getSizeY(),
            finalBounds.getSizeZ()
        );
    }

    @Nullable
    private static CapturedStructure buildMapCapturedStructure(BlockPos captureOrigin, List<StructureLayer> layers,
            List<NBTTagCompound> entityData) {
        if (captureOrigin == null || layers == null) return null;

        Map<Long, FrozenBlock> blocks = new LinkedHashMap<>();
        for (StructureLayer layer : layers) {
            if (layer == null) continue;

            for (int x = 0; x < layer.width; x++) {
                for (int z = 0; z < layer.depth; z++) {
                    IBlockState state = layer.getBlockState(x, z);
                    if (state == null) continue;

                    NBTTagCompound tileData = layer.getBlockEntityData(x, z);
                    ResourceLocation tileEntityId = getResourceLocation(tileData, "id");
                    ResourceLocation lootTableId = tileData != null ? getLootTableId(tileData) : null;
                    BlockPos blockPos = captureOrigin.add(layer.xOffset + x, layer.y, layer.zOffset + z);
                    blocks.put(blockPos.toLong(), new FrozenBlock(
                        blockPos, state, tileData, tileEntityId, lootTableId, 0));
                }
            }
        }

        List<FrozenEntity> entities = new ArrayList<>();
        if (entityData != null) {
            for (NBTTagCompound data : entityData) {
                if (data == null) continue;

                ResourceLocation entityId = getResourceLocation(data, "id");
                NBTTagList position = data.getTagList("Pos", Constants.NBT.TAG_DOUBLE);
                if (entityId == null || position.tagCount() < 3) continue;

                Vec3d worldPos = new Vec3d(position.getDoubleAt(0), position.getDoubleAt(1), position.getDoubleAt(2));
                entities.add(new FrozenEntity("", entityId, new BlockPos(worldPos), worldPos, data.copy()));
            }
        }

        return buildCapturedStructure(new FrozenCapture(0, captureOrigin, captureOrigin, blocks, entities),
            new StructureCaptureExclusions());
    }

    @Nullable
    private static NBTTagCompound buildRenderedPreviewNbt(FrozenCapture frozenCapture,
            StructureCaptureExclusions exclusions) {
        ContentBounds finalBounds = collectFinalBounds(frozenCapture, exclusions);
        if (!finalBounds.hasContent()) return null;

        return writeRenderedPreviewNbt(frozenCapture, finalBounds, exclusions);
    }

    private static List<StructureCaptureSummary.EntityInstance> collectEntitySummaries(FrozenCapture frozenCapture,
            ContentBounds contentBounds) {
        List<StructureCaptureSummary.EntityInstance> entities = new ArrayList<>();
        for (FrozenEntity frozenEntity : frozenCapture.entities) {
            contentBounds.include(frozenEntity.anchorPos);
            entities.add(new StructureCaptureSummary.EntityInstance(
                frozenEntity.uuid,
                frozenEntity.entityId,
                frozenEntity.anchorPos
            ));
        }

        entities.sort((first, second) -> {
            int idCompare = first.getEntityId().toString().compareTo(second.getEntityId().toString());
            if (idCompare != 0) return idCompare;

            int compareX = Integer.compare(first.getBlockPos().getX(), second.getBlockPos().getX());
            if (compareX != 0) return compareX;

            int compareY = Integer.compare(first.getBlockPos().getY(), second.getBlockPos().getY());
            if (compareY != 0) return compareY;

            return Integer.compare(first.getBlockPos().getZ(), second.getBlockPos().getZ());
        });

        return entities;
    }

    private static ContentBounds collectFinalBounds(FrozenCapture frozenCapture, StructureCaptureExclusions exclusions) {
        ContentBounds contentBounds = new ContentBounds();

        for (FrozenBlock frozenBlock : frozenCapture.blocks.values()) {
            BlockPos blockPos = frozenBlock.worldPos;
            IBlockState state = frozenBlock.state;
            if (!CaptureBlockHelper.contributesToBounds(state)) continue;
            if (isBlockExcluded(state, exclusions)) continue;

            contentBounds.include(blockPos);
        }

        for (FrozenEntity frozenEntity : frozenCapture.entities) {
            if (exclusions.isEntityExcluded(frozenEntity.entityId)) continue;

            contentBounds.include(frozenEntity.anchorPos);
        }

        return contentBounds;
    }

    private static NBTTagCompound writeStructureNbt(FrozenCapture frozenCapture, ContentBounds bounds,
            StructureCaptureExclusions exclusions) {
        List<CapturedBlock> solidBlocks = new ArrayList<>();
        List<CapturedBlock> tileBlocks = new ArrayList<>();
        List<CapturedBlock> otherBlocks = new ArrayList<>();
        LinkedHashMap<IBlockState, Integer> palette = new LinkedHashMap<>();
        Set<String> referencedNamespaces = new LinkedHashSet<>();
        AirRetentionMask airRetentionMask = buildAirRetentionMask(frozenCapture, bounds, exclusions);

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(bounds.minPos, bounds.maxPos)) {
            BlockPos worldPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            BlockPos relativePos = worldPos.subtract(bounds.minPos);
            FrozenBlock frozenBlock = frozenCapture.getBlock(worldPos);
            IBlockState state = frozenBlock == null ? Blocks.AIR.getDefaultState() : frozenBlock.state;
            boolean excludedBlock = isBlockExcluded(state, exclusions);
            IBlockState storedState = excludedBlock ? Blocks.AIR.getDefaultState() : state;

            if (CaptureBlockHelper.isAir(storedState) && !airRetentionMask.shouldKeep(relativePos)) continue;

            if (!CaptureBlockHelper.isAir(storedState)) {
                addNamespace(referencedNamespaces, storedState.getBlock().getRegistryName());
            }

            NBTTagCompound tileData = null;
            if (!excludedBlock && frozenBlock != null && frozenBlock.tileData != null) {
                boolean listedContainer = frozenBlock.lootTableId != null || frozenBlock.itemCount > 0;
                if (!listedContainer || !isContainerExcluded(state, frozenBlock.lootTableId, exclusions)) {
                    tileData = frozenBlock.tileData.copy();
                    trimTileEntityData(tileData);
                    addNamespace(referencedNamespaces, frozenBlock.tileEntityId);
                }
            }

            idForState(palette, storedState);
            CapturedBlock block = new CapturedBlock(relativePos, storedState, tileData);
            if (tileData != null) {
                tileBlocks.add(block);
                continue;
            }

            if (storedState.isFullBlock() && storedState.isFullCube()) {
                solidBlocks.add(block);
                continue;
            }

            otherBlocks.add(block);
        }

        List<CapturedEntity> entities = captureEntitiesForSave(frozenCapture, bounds, exclusions, referencedNamespaces);

        NBTTagCompound nbt = new NBTTagCompound();
        writeSelectedForgeDataVersions(nbt, referencedNamespaces);
        nbt.setTag("palette", writePalette(palette));
        nbt.setTag("blocks", writeBlocks(solidBlocks, tileBlocks, otherBlocks, palette));
        nbt.setTag("entities", writeEntities(entities));
        nbt.setTag("size", writeInts(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ()));
        nbt.setString("author", CAPTURE_AUTHOR);
        nbt.setInteger("DataVersion", DATA_VERSION);
        return nbt;
    }

    private static NBTTagCompound writeRenderedPreviewNbt(FrozenCapture frozenCapture, ContentBounds bounds,
            StructureCaptureExclusions exclusions) {
        List<CapturedBlock> solidBlocks = new ArrayList<>();
        List<CapturedBlock> otherBlocks = new ArrayList<>();
        LinkedHashMap<IBlockState, Integer> palette = new LinkedHashMap<>();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(bounds.minPos, bounds.maxPos)) {
            BlockPos worldPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            FrozenBlock frozenBlock = frozenCapture.getBlock(worldPos);
            IBlockState state = frozenBlock == null ? Blocks.AIR.getDefaultState() : frozenBlock.state;
            if (isBlockExcluded(state, exclusions)) continue;
            if (CaptureBlockHelper.isAir(state)) continue;

            BlockPos relativePos = worldPos.subtract(bounds.minPos);
            idForState(palette, state);

            CapturedBlock block = new CapturedBlock(relativePos, state, null);
            if (state.isFullBlock() && state.isFullCube()) {
                solidBlocks.add(block);
                continue;
            }

            otherBlocks.add(block);
        }

        NBTTagCompound nbt = new NBTTagCompound();

        // The preview renderer only needs visible block states. Save-only data such as air
        // retention, tile NBT, and entities is omitted here to keep large previews small.
        nbt.setTag("palette", writePalette(palette));
        nbt.setTag("blocks", writeBlocks(solidBlocks, Collections.emptyList(), otherBlocks, palette));
        nbt.setTag("size", writeInts(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ()));
        return nbt;
    }

    private static List<CapturedEntity> captureEntitiesForSave(FrozenCapture frozenCapture, ContentBounds bounds,
            StructureCaptureExclusions exclusions, Set<String> referencedNamespaces) {
        List<CapturedEntity> entities = new ArrayList<>();

        for (FrozenEntity frozenEntity : frozenCapture.entities) {
            if (exclusions.isEntityExcluded(frozenEntity.entityId)) continue;
            if (frozenEntity.entityData == null) continue;

            NBTTagCompound entityData = frozenEntity.entityData.copy();

            addNamespacesFromEntityData(entityData, referencedNamespaces);
            trimEntityData(entityData);

            Vec3d relativePos = new Vec3d(
                frozenEntity.worldPos.x - bounds.minPos.getX(),
                frozenEntity.worldPos.y - bounds.minPos.getY(),
                frozenEntity.worldPos.z - bounds.minPos.getZ()
            );

            BlockPos relativeBlockPos = frozenEntity.anchorPos.subtract(bounds.minPos);

            Vec3d savedRelativePos = new Vec3d(
                relativeBlockPos.getX() + 0.5D,
                relativePos.y,
                relativeBlockPos.getZ() + 0.5D
            );

            entities.add(new CapturedEntity(savedRelativePos, relativeBlockPos, entityData));
        }

        return entities;
    }

    private static AirRetentionMask buildAirRetentionMask(FrozenCapture frozenCapture, ContentBounds bounds,
            StructureCaptureExclusions exclusions) {
        AirRetentionMask airRetentionMask = new AirRetentionMask(bounds);

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(bounds.minPos, bounds.maxPos)) {
            BlockPos worldPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            FrozenBlock frozenBlock = frozenCapture.getBlock(worldPos);
            IBlockState state = frozenBlock == null ? Blocks.AIR.getDefaultState() : frozenBlock.state;
            IBlockState storedState = isBlockExcluded(state, exclusions) ? Blocks.AIR.getDefaultState() : state;
            if (CaptureBlockHelper.isAir(storedState)) continue;

            BlockPos relativePos = worldPos.subtract(bounds.minPos);
            airRetentionMask.include(relativePos);
        }

        airRetentionMask.classifyOutside();
        return airRetentionMask;
    }

    private static NBTTagList writePalette(LinkedHashMap<IBlockState, Integer> palette) {
        NBTTagList paletteList = new NBTTagList();
        for (IBlockState state : palette.keySet()) {
            paletteList.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), state));
        }

        return paletteList;
    }

    private static NBTTagList writeBlocks(List<CapturedBlock> solidBlocks, List<CapturedBlock> tileBlocks,
            List<CapturedBlock> otherBlocks, LinkedHashMap<IBlockState, Integer> palette) {
        NBTTagList blockList = new NBTTagList();

        appendBlocks(blockList, solidBlocks, palette);
        appendBlocks(blockList, tileBlocks, palette);
        appendBlocks(blockList, otherBlocks, palette);
        return blockList;
    }

    private static void appendBlocks(NBTTagList blockList, List<CapturedBlock> blocks,
            LinkedHashMap<IBlockState, Integer> palette) {
        for (CapturedBlock block : blocks) {
            NBTTagCompound blockTag = new NBTTagCompound();
            blockTag.setTag("pos", writeInts(block.relativePos.getX(), block.relativePos.getY(), block.relativePos.getZ()));
            blockTag.setInteger("state", palette.get(block.state));
            if (block.tileData != null) blockTag.setTag("nbt", block.tileData);

            blockList.appendTag(blockTag);
        }
    }

    private static NBTTagList writeEntities(List<CapturedEntity> entities) {
        NBTTagList entityList = new NBTTagList();
        for (CapturedEntity entity : entities) {
            NBTTagCompound entityTag = new NBTTagCompound();
            entityTag.setTag("pos", writeDoubles(entity.relativePos.x, entity.relativePos.y, entity.relativePos.z));
            entityTag.setTag("blockPos", writeInts(entity.relativeBlockPos.getX(), entity.relativeBlockPos.getY(), entity.relativeBlockPos.getZ()));
            entityTag.setTag("nbt", entity.entityData);
            entityList.appendTag(entityTag);
        }

        return entityList;
    }

    private static int countInventoryItems(TileEntity tileEntity) {
        if (!(tileEntity instanceof IInventory)) return 0;

        IInventory inventory = (IInventory) tileEntity;
        int itemCount = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) itemCount += stack.getCount();
        }

        return itemCount;
    }

    private static List<Entity> getEntitiesInBounds(World world, BlockPos minPos, BlockPos maxPos) {
        AxisAlignedBB bounds = new AxisAlignedBB(minPos, maxPos.add(1, 1, 1));
        return world.getEntitiesWithinAABB(Entity.class, bounds, entity -> !(entity instanceof EntityPlayer));
    }

    private static FrozenCapture captureSnapshot(World world, BlockPos firstCorner, BlockPos secondCorner) {
        NormalizedBounds selection = NormalizedBounds.fromCorners(firstCorner, secondCorner);
        Map<Long, FrozenBlock> blocks = new LinkedHashMap<>();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(selection.minPos, selection.maxPos)) {
            BlockPos blockPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            IBlockState state = world.getBlockState(blockPos);
            TileEntity tileEntity = world.getTileEntity(blockPos);
            NBTTagCompound tileData = null;
            ResourceLocation tileEntityId = null;
            ResourceLocation lootTableId = null;
            int itemCount = 0;

            if (tileEntity != null) {
                tileData = tileEntity.writeToNBT(new NBTTagCompound());
                tileEntityId = TileEntity.getKey(tileEntity.getClass());
                lootTableId = getLootTableId(tileData);

                // Accessing IInventory on TileEntityLockableLoot generates its loot, so only count
                // fixed inventories after confirming no loot table is still attached.
                itemCount = lootTableId == null ? countInventoryItems(tileEntity) : 0;
            }

            blocks.put(blockPos.toLong(), new FrozenBlock(blockPos, state, tileData, tileEntityId, lootTableId, itemCount));
        }

        List<FrozenEntity> entities = new ArrayList<>();
        for (Entity entity : getEntitiesInBounds(world, selection.minPos, selection.maxPos)) {
            if (!shouldCaptureEntity(entity)) continue;

            ResourceLocation entityId = EntityList.getKey(entity);
            if (entityId == null) continue;

            BlockPos anchorPos = entity instanceof EntityPainting
                ? ((EntityPainting) entity).getHangingPosition()
                : new BlockPos(entity.posX, entity.posY, entity.posZ);
            NBTTagCompound entityData = new NBTTagCompound();

            entities.add(new FrozenEntity(
                entity.getUniqueID().toString(),
                entityId,
                anchorPos,
                new Vec3d(entity.posX, entity.posY, entity.posZ),
                entity.writeToNBTOptional(entityData) ? entityData : null
            ));
        }

        return new FrozenCapture(
            world.provider.getDimension(),
            selection.minPos,
            selection.maxPos,
            blocks,
            entities
        );
    }

    private static FrozenCapture getOrCreateCapture(UUID playerId, World world, BlockPos firstCorner,
            BlockPos secondCorner) {
        FrozenCapture frozenCapture = FROZEN_CAPTURES.get(playerId);
        if (matchesFrozenCapture(frozenCapture, world, firstCorner, secondCorner)) return frozenCapture;

        // This only happens if the server-side session was lost or the corners changed. Refreshing
        // the snapshot keeps the capture flow functional instead of hard-failing the request.
        frozenCapture = captureSnapshot(world, firstCorner, secondCorner);
        FROZEN_CAPTURES.put(playerId, frozenCapture);
        return frozenCapture;
    }

    private static boolean matchesFrozenCapture(@Nullable FrozenCapture frozenCapture, World world,
            BlockPos firstCorner, BlockPos secondCorner) {
        if (frozenCapture == null) return false;

        NormalizedBounds selection = NormalizedBounds.fromCorners(firstCorner, secondCorner);
        if (frozenCapture.dimension != world.provider.getDimension()) return false;
        if (!frozenCapture.selectionMinPos.equals(selection.minPos)) return false;

        return frozenCapture.selectionMaxPos.equals(selection.maxPos);
    }

    @Nullable
    private static ResourceLocation getLootTableId(NBTTagCompound tileData) {
        if (!tileData.hasKey("LootTable")) return null;

        String lootTable = tileData.getString("LootTable");
        if (lootTable.isEmpty()) return null;

        return new ResourceLocation(lootTable);
    }

    @Nullable
    private static ResourceLocation getResourceLocation(@Nullable NBTTagCompound data, String key) {
        if (data == null || !data.hasKey(key, Constants.NBT.TAG_STRING)) return null;

        String value = data.getString(key);
        return value.isEmpty() ? null : new ResourceLocation(value);
    }

    private static void trimTileEntityData(NBTTagCompound tileData) {
        tileData.removeTag("x");
        tileData.removeTag("y");
        tileData.removeTag("z");
        tileData.removeTag("ForgeCaps");
        tileData.removeTag("Lock");
    }

    private static void trimEntityData(NBTTagCompound entityData) {
        entityData.removeTag("Attributes");
        entityData.removeTag("ForgeCaps");
        entityData.removeTag("Motion");
        entityData.removeTag("Rotation");
        entityData.removeTag("UUIDMost");
        entityData.removeTag("UUIDLeast");
        entityData.removeTag("Pos");

        if (!entityData.hasKey("Passengers", Constants.NBT.TAG_LIST)) return;

        NBTTagList passengers = entityData.getTagList("Passengers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < passengers.tagCount(); i++) trimEntityData(passengers.getCompoundTagAt(i));
    }

    private static void addNamespacesFromEntityData(NBTTagCompound entityData, Set<String> referencedNamespaces) {
        addNamespaceFromIdString(referencedNamespaces, entityData.getString("id"));

        if (!entityData.hasKey("Passengers", Constants.NBT.TAG_LIST)) return;

        NBTTagList passengers = entityData.getTagList("Passengers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < passengers.tagCount(); i++) {
            addNamespacesFromEntityData(passengers.getCompoundTagAt(i), referencedNamespaces);
        }
    }

    private static void addNamespace(Set<String> referencedNamespaces, @Nullable ResourceLocation id) {
        if (id == null) return;

        String namespace = id.getNamespace();
        if (namespace.isEmpty() || "minecraft".equals(namespace)) return;

        referencedNamespaces.add(namespace);
    }

    private static void addNamespaceFromIdString(Set<String> referencedNamespaces, String id) {
        if (id.isEmpty()) return;

        int separator = id.indexOf(':');
        String namespace = separator >= 0 ? id.substring(0, separator) : "minecraft";
        if (namespace.isEmpty() || "minecraft".equals(namespace)) return;

        referencedNamespaces.add(namespace);
    }

    private static void writeSelectedForgeDataVersions(NBTTagCompound structureNbt, Set<String> referencedNamespaces) {
        FMLCommonHandler.instance().getDataFixer().writeVersionData(structureNbt);
        if (!structureNbt.hasKey("ForgeDataVersion", Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound forgeDataVersion = structureNbt.getCompoundTag("ForgeDataVersion");
        List<String> namespaces = new ArrayList<>(forgeDataVersion.getKeySet());
        for (String namespace : namespaces) {
            if ("minecraft".equals(namespace) || !referencedNamespaces.contains(namespace)) {
                forgeDataVersion.removeTag(namespace);
            }
        }

        if (forgeDataVersion.getKeySet().isEmpty()) structureNbt.removeTag("ForgeDataVersion");
    }

    private static boolean shouldCaptureEntity(@Nullable Entity entity) {
        if (entity == null || !entity.isEntityAlive()) return false;
        if (entity instanceof EntityItem) return false;
        if (entity instanceof EntityXPOrb) return false;

        return true;
    }

    private static CaptureContainerKey createContainerKey(IBlockState state, @Nullable ResourceLocation lootTableId) {
        return new CaptureContainerKey(CaptureBlockHelper.createKey(state), lootTableId);
    }

    private static boolean isContainerExcluded(IBlockState state, @Nullable ResourceLocation lootTableId,
            StructureCaptureExclusions exclusions) {
        return exclusions.isContainerExcluded(createContainerKey(state, lootTableId));
    }

    private static boolean isBlockExcluded(IBlockState state, StructureCaptureExclusions exclusions) {
        if (CaptureBlockHelper.isAir(state)) return false;

        return exclusions.isBlockExcluded(CaptureBlockHelper.createKey(state));
    }

    private static File getCaptureDirectory(@Nullable World world) {
        MinecraftServer server = world != null ? world.getMinecraftServer() : null;
        File captureDirectory = server != null ? server.getFile(CAPTURE_DIRECTORY) : new File(CAPTURE_DIRECTORY);
        if (!captureDirectory.exists()) captureDirectory.mkdirs();

        return captureDirectory;
    }

    private static int idForState(LinkedHashMap<IBlockState, Integer> palette, IBlockState state) {
        Integer currentId = palette.get(state);
        if (currentId != null) return currentId;

        int nextId = palette.size();
        palette.put(state, nextId);
        return nextId;
    }

    private static NBTTagList writeInts(int... values) {
        NBTTagList list = new NBTTagList();
        for (int value : values) list.appendTag(new NBTTagInt(value));

        return list;
    }

    private static NBTTagList writeDoubles(double... values) {
        NBTTagList list = new NBTTagList();
        for (double value : values) list.appendTag(new NBTTagDouble(value));

        return list;
    }

    private static File createCaptureFile(File directory) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        File candidate = new File(directory, "capture_" + timestamp + ".nbt");
        if (!candidate.exists()) return candidate;

        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(directory, "capture_" + timestamp + "_" + suffix + ".nbt");
            suffix++;
        }

        return candidate;
    }

    private static File createMapCaptureFile(File directory, String mapName) {
        String baseName = "mapgen_" + sanitizeFileName(mapName);
        File candidate = new File(directory, baseName + ".nbt");
        if (!candidate.exists()) return candidate;

        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(directory, baseName + "_" + suffix + ".nbt");
            suffix++;
        }

        return candidate;
    }

    private static String sanitizeFileName(String name) {
        StringBuilder sanitized = new StringBuilder();
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                sanitized.append(character);
            } else {
                sanitized.append('_');
            }
        }

        return sanitized.toString();
    }

    public static final class SaveResult {
        private final File file;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;

        private SaveResult(File file, int sizeX, int sizeY, int sizeZ) {
            this.file = file;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public File getFile() {
            return file;
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
    }

    public static final class CapturedStructure {
        private final NBTTagCompound structureNbt;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;

        private CapturedStructure(NBTTagCompound structureNbt, int sizeX, int sizeY, int sizeZ) {
            this.structureNbt = structureNbt;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public NBTTagCompound getStructureNbt() {
            return structureNbt;
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
    }

    private static final class BlockAccumulator {
        private final BlockDisplayKey key;
        private final IBlockState representativeState;
        private int count;

        private BlockAccumulator(BlockDisplayKey key, IBlockState representativeState) {
            this.key = key;
            this.representativeState = representativeState;
            this.count = 0;
        }
    }

    private static final class ContainerAccumulator {
        private final CaptureContainerKey key;
        private final IBlockState representativeState;
        @Nullable
        private final ResourceLocation lootTableId;
        private int containerCount;
        private int totalItemCount;

        private ContainerAccumulator(CaptureContainerKey key, IBlockState representativeState,
                @Nullable ResourceLocation lootTableId) {
            this.key = key;
            this.representativeState = representativeState;
            this.lootTableId = lootTableId;
            this.containerCount = 0;
            this.totalItemCount = 0;
        }
    }

    private static final class NormalizedBounds {
        private final BlockPos minPos;
        private final BlockPos maxPos;

        private NormalizedBounds(BlockPos minPos, BlockPos maxPos) {
            this.minPos = minPos;
            this.maxPos = maxPos;
        }

        private static NormalizedBounds fromCorners(BlockPos firstCorner, BlockPos secondCorner) {
            BlockPos minPos = new BlockPos(
                Math.min(firstCorner.getX(), secondCorner.getX()),
                Math.min(firstCorner.getY(), secondCorner.getY()),
                Math.min(firstCorner.getZ(), secondCorner.getZ())
            );

            BlockPos maxPos = new BlockPos(
                Math.max(firstCorner.getX(), secondCorner.getX()),
                Math.max(firstCorner.getY(), secondCorner.getY()),
                Math.max(firstCorner.getZ(), secondCorner.getZ())
            );

            return new NormalizedBounds(minPos, maxPos);
        }
    }

    private static final class ContentBounds {
        private boolean hasContent;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private BlockPos minPos = BlockPos.ORIGIN;
        private BlockPos maxPos = BlockPos.ORIGIN;

        private void include(BlockPos blockPos) {
            hasContent = true;
            minX = Math.min(minX, blockPos.getX());
            minY = Math.min(minY, blockPos.getY());
            minZ = Math.min(minZ, blockPos.getZ());
            maxX = Math.max(maxX, blockPos.getX());
            maxY = Math.max(maxY, blockPos.getY());
            maxZ = Math.max(maxZ, blockPos.getZ());
            minPos = new BlockPos(minX, minY, minZ);
            maxPos = new BlockPos(maxX, maxY, maxZ);
        }

        private boolean hasContent() {
            return hasContent;
        }

        private int getSizeX() {
            return maxX - minX + 1;
        }

        private int getSizeY() {
            return maxY - minY + 1;
        }

        private int getSizeZ() {
            return maxZ - minZ + 1;
        }
    }

    /**
     * Only keeps air hidden behind structure walls. The exterior fill reaches every air block
     * connected to capture's sides unless a framed entrance separates the air spaces.
     * <p>
     * Outside air is defined as :
     * - Any air connected to the sides of the capture area
     * - That does not cross an entrance (connected frame)
     * - Unless it is a 1-thick arch
     * <p>
     * This means a tunnel or a room is considered inside air. A 2-thick arch is a 2-thick tunnel.
     */
    private static final class AirRetentionMask {
        private static final int AXIS_X = 0;
        private static final int AXIS_Z = 1;

        // Non-air bitsets and entrance/plane tagging are used to drastically reduce the number of checks.
        // This keeps the computational complexity manageable by doing the job once per relevant group.
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int haloSizeX;
        private final int haloSizeY;
        private final int haloSizeZ;
        private final BitSet nonAirX;
        private final BitSet nonAirY;
        private final BitSet nonAirZ;
        private final BitSet outside;
        private final Map<Long, Boolean> entrances = new HashMap<>();
        private final Map<Long, Integer> planeTags = new HashMap<>();
        private final int[] outsideQueue;
        private final int[] planeQueue;
        private int nextPlaneTag = 1;

        private AirRetentionMask(ContentBounds bounds) {
            sizeX = bounds.getSizeX();
            sizeY = bounds.getSizeY();
            sizeZ = bounds.getSizeZ();
            haloSizeX = sizeX + 2;
            haloSizeY = sizeY + 2;
            haloSizeZ = sizeZ + 2;
            nonAirX = new BitSet(sizeX * sizeY * sizeZ);
            nonAirY = new BitSet(sizeX * sizeY * sizeZ);
            nonAirZ = new BitSet(sizeX * sizeY * sizeZ);
            outside = new BitSet(haloSizeX * haloSizeY * haloSizeZ);
            outsideQueue = new int[haloSizeX * haloSizeY * haloSizeZ];
            planeQueue = new int[sizeX * sizeY * sizeZ];
        }

        private void include(BlockPos relativePos) {
            int x = relativePos.getX();
            int y = relativePos.getY();
            int z = relativePos.getZ();
            nonAirX.set(getIndexX(x, y, z));
            nonAirY.set(getIndexY(x, y, z));
            nonAirZ.set(getIndexZ(x, y, z));
        }

        private boolean shouldKeep(BlockPos relativePos) {
            return !isOutside(relativePos.getX(), relativePos.getY(), relativePos.getZ());
        }

        private void classifyOutside() {
            int head = 0;
            int tail = 0;
            outside.set(0);
            outsideQueue[tail++] = 0;

            while (head < tail) {
                int index = outsideQueue[head++];
                int x = index % haloSizeX - 1;
                int planeIndex = index / haloSizeX;
                int z = planeIndex % haloSizeZ - 1;
                int y = planeIndex / haloSizeZ - 1;

                tail = enqueueOutside(x, y, z, x - 1, y, z, tail);
                tail = enqueueOutside(x, y, z, x + 1, y, z, tail);
                tail = enqueueOutside(x, y, z, x, y - 1, z, tail);
                tail = enqueueOutside(x, y, z, x, y + 1, z, tail);
                tail = enqueueOutside(x, y, z, x, y, z - 1, tail);
                tail = enqueueOutside(x, y, z, x, y, z + 1, tail);
            }
        }

        private int enqueueOutside(int x, int y, int z, int neighborX, int neighborY, int neighborZ, int tail) {
            if (!isInsideHalo(neighborX, neighborY, neighborZ)) return tail;

            int neighborIndex = getHaloIndex(neighborX, neighborY, neighborZ);
            if (outside.get(neighborIndex)) return tail;
            if (!isAir(neighborX, neighborY, neighborZ)) return tail;
            if (!canTraverse(x, y, z, neighborX, neighborY, neighborZ)) return tail;

            outside.set(neighborIndex);
            outsideQueue[tail++] = neighborIndex;
            return tail;
        }

        private boolean canTraverse(int x, int y, int z, int neighborX, int neighborY, int neighborZ) {
            if (x != neighborX) {
                return canTraverseEntrance(x, y, z, neighborX, neighborY, neighborZ, AXIS_X);
            }

            if (z != neighborZ) {
                return canTraverseEntrance(x, y, z, neighborX, neighborY, neighborZ, AXIS_Z);
            }

            return true;
        }

        private boolean canTraverseEntrance(int x, int y, int z, int neighborX, int neighborY, int neighborZ,
                int axis) {
            int direction = axis == AXIS_X ? neighborX - x : neighborZ - z;
            if (isInside(neighborX, neighborY, neighborZ)
                    && isEntrance(neighborX, neighborY, neighborZ, axis)
                    && !isOutside(axis == AXIS_X ? neighborX + direction : neighborX, neighborY,
                        axis == AXIS_Z ? neighborZ + direction : neighborZ)) {
                return false;
            }

            if (isInside(x, y, z)
                    && isEntrance(x, y, z, axis)
                    && !isOutside(axis == AXIS_X ? x - direction : x, y,
                        axis == AXIS_Z ? z - direction : z)) {
                return false;
            }

            return true;
        }

        private boolean isEntrance(int x, int y, int z, int axis) {
            long key = getPlaneKey(x, y, z, axis);
            Boolean entrance = entrances.get(key);
            if (entrance != null) return entrance;

            if (!hasAirOnBothSides(x, y, z, axis)) {
                entrances.put(key, Boolean.FALSE);
                return false;
            }

            if (!hasConnectedFrame(x, y, z, axis)) {
                entrances.put(key, Boolean.FALSE);
                return false;
            }

            return tagEntrance(x, y, z, axis);
        }

        private boolean hasAirOnBothSides(int x, int y, int z, int axis) {
            if (axis == AXIS_X) return isAir(x - 1, y, z) && isAir(x + 1, y, z);

            return isAir(x, y, z - 1) && isAir(x, y, z + 1);
        }

        private boolean hasConnectedFrame(int x, int y, int z, int axis) {
            int lowerY = findPreviousNonAirY(x, y, z);
            int upperY = findNextNonAirY(x, y, z);
            if (lowerY < 0 || upperY < 0) return false;

            int first = axis == AXIS_X
                ? findPreviousNonAirZ(x, y, z)
                : findPreviousNonAirX(x, y, z);
            int second = axis == AXIS_X
                ? findNextNonAirZ(x, y, z)
                : findNextNonAirX(x, y, z);
            if (first < 0 || second < 0) return false;

            int tag = getPlaneTag(x, lowerY, z, axis);
            if (tag != getPlaneTag(x, upperY, z, axis)) return false;
            if (tag != getPlaneTag(axis == AXIS_X ? x : first, y, axis == AXIS_X ? first : z, axis)) return false;

            return tag == getPlaneTag(axis == AXIS_X ? x : second, y, axis == AXIS_X ? second : z, axis);
        }

        private boolean tagEntrance(int x, int y, int z, int axis) {
            int head = 0;
            int tail = 0;
            boolean reachesBoundary = false;
            long key = getPlaneKey(x, y, z, axis);
            entrances.put(key, Boolean.TRUE);
            planeQueue[tail++] = getIndexX(x, y, z);

            while (head < tail) {
                int index = planeQueue[head++];
                int currentX = index % sizeX;
                int planeIndex = index / sizeX;
                int currentZ = planeIndex % sizeZ;
                int currentY = planeIndex / sizeZ;
                reachesBoundary |= isPlaneBoundary(currentX, currentY, currentZ, axis);

                if (axis == AXIS_X) {
                    tail = addEntrancePosition(currentX, currentY - 1, currentZ, axis, tail);
                    tail = addEntrancePosition(currentX, currentY + 1, currentZ, axis, tail);
                    tail = addEntrancePosition(currentX, currentY, currentZ - 1, axis, tail);
                    tail = addEntrancePosition(currentX, currentY, currentZ + 1, axis, tail);
                } else {
                    tail = addEntrancePosition(currentX - 1, currentY, currentZ, axis, tail);
                    tail = addEntrancePosition(currentX + 1, currentY, currentZ, axis, tail);
                    tail = addEntrancePosition(currentX, currentY - 1, currentZ, axis, tail);
                    tail = addEntrancePosition(currentX, currentY + 1, currentZ, axis, tail);
                }
            }

            if (!reachesBoundary) return true;

            for (int index = 0; index < tail; index++) {
                int position = planeQueue[index];
                entrances.put(getPlaneKeyForIndex(position, axis), Boolean.FALSE);
            }

            return false;
        }

        private int addEntrancePosition(int x, int y, int z, int axis, int tail) {
            if (!isInside(x, y, z) || isNonAir(x, y, z)) return tail;

            long key = getPlaneKey(x, y, z, axis);
            if (Boolean.TRUE.equals(entrances.get(key))) return tail;

            entrances.put(key, Boolean.TRUE);
            planeQueue[tail++] = getIndexX(x, y, z);
            return tail;
        }

        private boolean isPlaneBoundary(int x, int y, int z, int axis) {
            if (axis == AXIS_X) return y == 0 || y == sizeY - 1 || z == 0 || z == sizeZ - 1;

            return x == 0 || x == sizeX - 1 || y == 0 || y == sizeY - 1;
        }

        private int getPlaneTag(int x, int y, int z, int axis) {
            long key = getPlaneKey(x, y, z, axis);
            Integer tag = planeTags.get(key);
            if (tag != null) return tag;

            tag = nextPlaneTag++;
            int head = 0;
            int tail = 0;
            planeTags.put(key, tag);
            planeQueue[tail++] = getIndexX(x, y, z);

            while (head < tail) {
                int index = planeQueue[head++];
                int currentX = index % sizeX;
                int planeIndex = index / sizeX;
                int currentZ = planeIndex % sizeZ;
                int currentY = planeIndex / sizeZ;

                if (axis == AXIS_X) {
                    tail = addPlanePosition(currentX, currentY - 1, currentZ, axis, tag, tail);
                    tail = addPlanePosition(currentX, currentY + 1, currentZ, axis, tag, tail);
                    tail = addPlanePosition(currentX, currentY, currentZ - 1, axis, tag, tail);
                    tail = addPlanePosition(currentX, currentY, currentZ + 1, axis, tag, tail);
                } else {
                    tail = addPlanePosition(currentX - 1, currentY, currentZ, axis, tag, tail);
                    tail = addPlanePosition(currentX + 1, currentY, currentZ, axis, tag, tail);
                    tail = addPlanePosition(currentX, currentY - 1, currentZ, axis, tag, tail);
                    tail = addPlanePosition(currentX, currentY + 1, currentZ, axis, tag, tail);
                }
            }

            return tag;
        }

        private int addPlanePosition(int x, int y, int z, int axis, int tag, int tail) {
            if (!isInside(x, y, z) || !isNonAir(x, y, z)) return tail;

            long key = getPlaneKey(x, y, z, axis);
            if (planeTags.containsKey(key)) return tail;

            planeTags.put(key, tag);
            planeQueue[tail++] = getIndexX(x, y, z);
            return tail;
        }

        private int findPreviousNonAirX(int x, int y, int z) {
            if (x <= 0) return -1;

            int lineStart = getIndexX(0, y, z);
            int index = nonAirX.previousSetBit(getIndexX(x - 1, y, z));
            return index >= lineStart ? index - lineStart : -1;
        }

        private int findNextNonAirX(int x, int y, int z) {
            int lineStart = getIndexX(0, y, z);
            int lineEnd = lineStart + sizeX - 1;
            int index = nonAirX.nextSetBit(getIndexX(x + 1, y, z));
            return index >= 0 && index <= lineEnd ? index - lineStart : -1;
        }

        private int findPreviousNonAirY(int x, int y, int z) {
            if (y <= 0) return -1;

            int lineStart = getIndexY(x, 0, z);
            int index = nonAirY.previousSetBit(getIndexY(x, y - 1, z));
            return index >= lineStart ? index - lineStart : -1;
        }

        private int findNextNonAirY(int x, int y, int z) {
            int lineStart = getIndexY(x, 0, z);
            int lineEnd = lineStart + sizeY - 1;
            int index = nonAirY.nextSetBit(getIndexY(x, y + 1, z));
            return index >= 0 && index <= lineEnd ? index - lineStart : -1;
        }

        private int findPreviousNonAirZ(int x, int y, int z) {
            if (z <= 0) return -1;

            int lineStart = getIndexZ(x, y, 0);
            int index = nonAirZ.previousSetBit(getIndexZ(x, y, z - 1));
            return index >= lineStart ? index - lineStart : -1;
        }

        private int findNextNonAirZ(int x, int y, int z) {
            int lineStart = getIndexZ(x, y, 0);
            int lineEnd = lineStart + sizeZ - 1;
            int index = nonAirZ.nextSetBit(getIndexZ(x, y, z + 1));
            return index >= 0 && index <= lineEnd ? index - lineStart : -1;
        }

        private boolean isAir(int x, int y, int z) {
            return isInsideHalo(x, y, z) && (!isInside(x, y, z) || !isNonAir(x, y, z));
        }

        private boolean isNonAir(int x, int y, int z) {
            return nonAirX.get(getIndexX(x, y, z));
        }

        private boolean isOutside(int x, int y, int z) {
            return isInsideHalo(x, y, z) && outside.get(getHaloIndex(x, y, z));
        }

        private boolean isInside(int x, int y, int z) {
            return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
        }

        private boolean isInsideHalo(int x, int y, int z) {
            return x >= -1 && x <= sizeX && y >= -1 && y <= sizeY && z >= -1 && z <= sizeZ;
        }

        private int getIndexX(int x, int y, int z) {
            return x + sizeX * (z + sizeZ * y);
        }

        private int getIndexY(int x, int y, int z) {
            return y + sizeY * (z + sizeZ * x);
        }

        private int getIndexZ(int x, int y, int z) {
            return z + sizeZ * (y + sizeY * x);
        }

        private int getHaloIndex(int x, int y, int z) {
            return x + 1 + haloSizeX * (z + 1 + haloSizeZ * (y + 1));
        }

        private long getPlaneKey(int x, int y, int z, int axis) {
            return getPlaneKeyForIndex(getIndexX(x, y, z), axis);
        }

        private long getPlaneKeyForIndex(int index, int axis) {
            return ((long) index << 1) | axis;
        }
    }

    private static final class CapturedBlock {
        private final BlockPos relativePos;
        private final IBlockState state;
        @Nullable
        private final NBTTagCompound tileData;

        private CapturedBlock(BlockPos relativePos, IBlockState state, @Nullable NBTTagCompound tileData) {
            this.relativePos = relativePos;
            this.state = state;
            this.tileData = tileData;
        }
    }

    private static final class CapturedEntity {
        private final Vec3d relativePos;
        private final BlockPos relativeBlockPos;
        private final NBTTagCompound entityData;

        private CapturedEntity(Vec3d relativePos, BlockPos relativeBlockPos, NBTTagCompound entityData) {
            this.relativePos = relativePos;
            this.relativeBlockPos = relativeBlockPos;
            this.entityData = entityData;
        }
    }

    private static final class FrozenCapture {
        private final int dimension;
        private final BlockPos selectionMinPos;
        private final BlockPos selectionMaxPos;
        private final Map<Long, FrozenBlock> blocks;
        private final List<FrozenEntity> entities;

        private FrozenCapture(int dimension, BlockPos selectionMinPos, BlockPos selectionMaxPos,
                Map<Long, FrozenBlock> blocks, List<FrozenEntity> entities) {
            this.dimension = dimension;
            this.selectionMinPos = selectionMinPos;
            this.selectionMaxPos = selectionMaxPos;
            this.blocks = blocks;
            this.entities = entities;
        }

        @Nullable
        private FrozenBlock getBlock(BlockPos blockPos) {
            return blocks.get(blockPos.toLong());
        }
    }

    private static final class FrozenBlock {
        private final BlockPos worldPos;
        private final IBlockState state;
        @Nullable
        private final NBTTagCompound tileData;
        @Nullable
        private final ResourceLocation tileEntityId;
        @Nullable
        private final ResourceLocation lootTableId;
        private final int itemCount;

        private FrozenBlock(BlockPos worldPos, IBlockState state, @Nullable NBTTagCompound tileData,
                @Nullable ResourceLocation tileEntityId, @Nullable ResourceLocation lootTableId, int itemCount) {
            this.worldPos = worldPos;
            this.state = state;
            this.tileData = tileData;
            this.tileEntityId = tileEntityId;
            this.lootTableId = lootTableId;
            this.itemCount = itemCount;
        }
    }

    private static final class FrozenEntity {
        private final String uuid;
        private final ResourceLocation entityId;
        private final BlockPos anchorPos;
        private final Vec3d worldPos;
        @Nullable
        private final NBTTagCompound entityData;

        private FrozenEntity(String uuid, ResourceLocation entityId, BlockPos anchorPos, Vec3d worldPos,
                @Nullable NBTTagCompound entityData) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.anchorPos = anchorPos;
            this.worldPos = worldPos;
            this.entityData = entityData;
        }
    }
}