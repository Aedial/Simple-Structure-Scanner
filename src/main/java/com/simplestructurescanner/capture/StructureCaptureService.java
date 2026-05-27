package com.simplestructurescanner.capture;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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


/**
 * Server-side capture builder for preview summaries and final structure NBT files.
 */
public final class StructureCaptureService {

    private static final String CAPTURE_DIRECTORY = "structurescanner-structures";
    private static final String CAPTURE_AUTHOR = "Simple Structure Scanner";
    private static final int DATA_VERSION = 1343;

    private StructureCaptureService() {
    }

    @Nullable
    public static StructureCaptureSummary buildSummary(World world, BlockPos firstCorner, BlockPos secondCorner) {
        NormalizedBounds selection = NormalizedBounds.fromCorners(firstCorner, secondCorner);
        ContentBounds contentBounds = new ContentBounds();
        Map<String, BlockAccumulator> blocks = new LinkedHashMap<String, BlockAccumulator>();
        Map<String, ContainerAccumulator> containers = new LinkedHashMap<String, ContainerAccumulator>();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(selection.minPos, selection.maxPos)) {
            BlockPos blockPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            IBlockState state = world.getBlockState(blockPos);

            if (CaptureBlockHelper.contributesToBounds(state)) contentBounds.include(blockPos);

            if (CaptureBlockHelper.shouldShowInSummary(state)) {
                String blockKey = CaptureBlockHelper.createKey(state);
                BlockAccumulator accumulator = blocks.get(blockKey);
                if (accumulator == null) {
                    accumulator = new BlockAccumulator(blockKey, state);
                    blocks.put(blockKey, accumulator);
                }

                accumulator.count++;
            }

            TileEntity tileEntity = world.getTileEntity(blockPos);
            if (tileEntity == null) continue;

            NBTTagCompound tileData = tileEntity.writeToNBT(new NBTTagCompound());
            ResourceLocation lootTableId = getLootTableId(tileData);

            // Accessing IInventory on TileEntityLockableLoot generates its loot, so only count
            // fixed inventories after confirming no loot table is still attached.
            int itemCount = lootTableId == null ? countInventoryItems(tileEntity) : 0;
            if (lootTableId == null && itemCount <= 0) continue;

            String containerKey = createContainerKey(state, lootTableId);
            ContainerAccumulator accumulator = containers.get(containerKey);
            if (accumulator == null) {
                accumulator = new ContainerAccumulator(containerKey, state, lootTableId);
                containers.put(containerKey, accumulator);
            }

            accumulator.containerCount++;
            accumulator.totalItemCount += itemCount;
        }

        List<StructureCaptureSummary.EntityInstance> entities = collectEntitySummaries(world, selection, contentBounds);

        if (!contentBounds.hasContent()) return null;

        List<StructureCaptureSummary.BlockSummary> blockSummaries = new ArrayList<StructureCaptureSummary.BlockSummary>();
        for (BlockAccumulator accumulator : blocks.values()) {
            blockSummaries.add(new StructureCaptureSummary.BlockSummary(accumulator.key, accumulator.representativeState, accumulator.count));
        }

        Collections.sort(blockSummaries, new Comparator<StructureCaptureSummary.BlockSummary>() {
            @Override
            public int compare(StructureCaptureSummary.BlockSummary first, StructureCaptureSummary.BlockSummary second) {
                int countCompare = Integer.compare(second.getCount(), first.getCount());
                if (countCompare != 0) return countCompare;

                return first.getKey().compareTo(second.getKey());
            }
        });

        List<StructureCaptureSummary.ContainerSummary> containerSummaries = new ArrayList<StructureCaptureSummary.ContainerSummary>();
        for (ContainerAccumulator accumulator : containers.values()) {
            containerSummaries.add(new StructureCaptureSummary.ContainerSummary(
                accumulator.key,
                accumulator.representativeState,
                accumulator.lootTableId,
                accumulator.containerCount,
                accumulator.totalItemCount
            ));
        }

        Collections.sort(containerSummaries, new Comparator<StructureCaptureSummary.ContainerSummary>() {
            @Override
            public int compare(StructureCaptureSummary.ContainerSummary first,
                    StructureCaptureSummary.ContainerSummary second) {
                int countCompare = Integer.compare(second.getContainerCount(), first.getContainerCount());
                if (countCompare != 0) return countCompare;

                return first.getKey().compareTo(second.getKey());
            }
        });

        return new StructureCaptureSummary(
            contentBounds.getSizeX(),
            contentBounds.getSizeY(),
            contentBounds.getSizeZ(),
            blockSummaries,
            entities,
            containerSummaries
        );
    }

    @Nullable
    public static SaveResult saveCapture(World world, BlockPos firstCorner, BlockPos secondCorner,
            StructureCaptureExclusions exclusions) throws IOException {
        NormalizedBounds selection = NormalizedBounds.fromCorners(firstCorner, secondCorner);
        ContentBounds finalBounds = collectFinalBounds(world, selection, exclusions);
        if (!finalBounds.hasContent()) return null;

        NBTTagCompound structureNbt = writeStructureNbt(world, finalBounds, exclusions);
        File captureDirectory = getCaptureDirectory(world);

        File captureFile = createCaptureFile(captureDirectory);
        try (OutputStream stream = Files.newOutputStream(captureFile.toPath())) {
            CompressedStreamTools.writeCompressed(structureNbt, stream);
        }

        return new SaveResult(captureFile, finalBounds.getSizeX(), finalBounds.getSizeY(), finalBounds.getSizeZ());
    }

    private static List<StructureCaptureSummary.EntityInstance> collectEntitySummaries(World world,
            NormalizedBounds selection, ContentBounds contentBounds) {
        List<StructureCaptureSummary.EntityInstance> entities = new ArrayList<StructureCaptureSummary.EntityInstance>();
        for (Entity entity : getEntitiesInBounds(world, selection.minPos, selection.maxPos)) {
            if (!shouldCaptureEntity(entity)) continue;

            UUID uuid = entity.getUniqueID();
            ResourceLocation entityId = EntityList.getKey(entity);
            if (uuid == null || entityId == null) continue;

            BlockPos blockPos = entity instanceof EntityPainting
                ? ((EntityPainting) entity).getHangingPosition()
                : new BlockPos(entity.posX, entity.posY, entity.posZ);

            contentBounds.include(blockPos);
            entities.add(new StructureCaptureSummary.EntityInstance(uuid.toString(), entityId, blockPos));
        }

        Collections.sort(entities, new Comparator<StructureCaptureSummary.EntityInstance>() {
            @Override
            public int compare(StructureCaptureSummary.EntityInstance first, StructureCaptureSummary.EntityInstance second) {
                int idCompare = first.getEntityId().toString().compareTo(second.getEntityId().toString());
                if (idCompare != 0) return idCompare;

                int compareX = Integer.compare(first.getBlockPos().getX(), second.getBlockPos().getX());
                if (compareX != 0) return compareX;

                int compareY = Integer.compare(first.getBlockPos().getY(), second.getBlockPos().getY());
                if (compareY != 0) return compareY;

                return Integer.compare(first.getBlockPos().getZ(), second.getBlockPos().getZ());
            }
        });

        return entities;
    }

    private static ContentBounds collectFinalBounds(World world, NormalizedBounds selection,
            StructureCaptureExclusions exclusions) {
        ContentBounds contentBounds = new ContentBounds();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(selection.minPos, selection.maxPos)) {
            BlockPos blockPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            IBlockState state = world.getBlockState(blockPos);

            if (!CaptureBlockHelper.contributesToBounds(state)) continue;
            if (isBlockExcluded(state, exclusions)) continue;

            contentBounds.include(blockPos);
        }

        for (Entity entity : getEntitiesInBounds(world, selection.minPos, selection.maxPos)) {
            if (!shouldCaptureEntity(entity)) continue;

            UUID uuid = entity.getUniqueID();
            if (uuid != null && exclusions.isEntityExcluded(uuid.toString())) continue;

            BlockPos blockPos = entity instanceof EntityPainting
                ? ((EntityPainting) entity).getHangingPosition()
                : new BlockPos(entity.posX, entity.posY, entity.posZ);
            contentBounds.include(blockPos);
        }

        return contentBounds;
    }

    private static NBTTagCompound writeStructureNbt(World world, ContentBounds bounds,
            StructureCaptureExclusions exclusions) {
        List<CapturedBlock> solidBlocks = new ArrayList<CapturedBlock>();
        List<CapturedBlock> tileBlocks = new ArrayList<CapturedBlock>();
        List<CapturedBlock> otherBlocks = new ArrayList<CapturedBlock>();
        LinkedHashMap<IBlockState, Integer> palette = new LinkedHashMap<IBlockState, Integer>();

        for (BlockPos.MutableBlockPos mutablePos : BlockPos.getAllInBoxMutable(bounds.minPos, bounds.maxPos)) {
            BlockPos worldPos = new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ());
            BlockPos relativePos = worldPos.subtract(bounds.minPos);
            IBlockState state = world.getBlockState(worldPos);
            boolean excludedBlock = isBlockExcluded(state, exclusions);
            IBlockState storedState = excludedBlock ? Blocks.AIR.getDefaultState() : state;

            NBTTagCompound tileData = null;
            if (!excludedBlock) {
                TileEntity tileEntity = world.getTileEntity(worldPos);
                if (tileEntity != null) {
                    NBTTagCompound serializedTileData = tileEntity.writeToNBT(new NBTTagCompound());
                    ResourceLocation lootTableId = getLootTableId(serializedTileData);
                    int itemCount = lootTableId == null ? countInventoryItems(tileEntity) : 0;
                    boolean listedContainer = lootTableId != null || itemCount > 0;

                    if (!listedContainer || !isContainerExcluded(state, lootTableId, exclusions)) {
                        tileData = serializedTileData;
                        tileData.removeTag("x");
                        tileData.removeTag("y");
                        tileData.removeTag("z");
                    }
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

        List<CapturedEntity> entities = captureEntitiesForSave(world, bounds, exclusions);

        NBTTagCompound nbt = new NBTTagCompound();
        FMLCommonHandler.instance().getDataFixer().writeVersionData(nbt);
        nbt.setTag("palette", writePalette(palette));
        nbt.setTag("blocks", writeBlocks(solidBlocks, tileBlocks, otherBlocks, palette));
        nbt.setTag("entities", writeEntities(entities));
        nbt.setTag("size", writeInts(bounds.getSizeX(), bounds.getSizeY(), bounds.getSizeZ()));
        nbt.setString("author", CAPTURE_AUTHOR);
        nbt.setInteger("DataVersion", DATA_VERSION);
        return nbt;
    }

    private static List<CapturedEntity> captureEntitiesForSave(World world, ContentBounds bounds,
            StructureCaptureExclusions exclusions) {
        List<CapturedEntity> entities = new ArrayList<CapturedEntity>();

        for (Entity entity : getEntitiesInBounds(world, bounds.minPos, bounds.maxPos)) {
            if (!shouldCaptureEntity(entity)) continue;

            UUID uuid = entity.getUniqueID();
            if (uuid != null && exclusions.isEntityExcluded(uuid.toString())) continue;

            NBTTagCompound entityData = new NBTTagCompound();
            if (!entity.writeToNBTOptional(entityData)) continue;

            Vec3d relativePos = new Vec3d(
                entity.posX - bounds.minPos.getX(),
                entity.posY - bounds.minPos.getY(),
                entity.posZ - bounds.minPos.getZ()
            );

            BlockPos relativeBlockPos = entity instanceof EntityPainting
                ? ((EntityPainting) entity).getHangingPosition().subtract(bounds.minPos)
                : new BlockPos(relativePos);

            entities.add(new CapturedEntity(relativePos, relativeBlockPos, entityData));
        }

        return entities;
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
            blockTag.setInteger("state", palette.get(block.state).intValue());
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

    @Nullable
    private static ResourceLocation getLootTableId(NBTTagCompound tileData) {
        if (!tileData.hasKey("LootTable")) return null;

        String lootTable = tileData.getString("LootTable");
        if (lootTable.isEmpty()) return null;

        return new ResourceLocation(lootTable);
    }

    private static boolean shouldCaptureEntity(@Nullable Entity entity) {
        if (entity == null || !entity.isEntityAlive()) return false;
        if (entity instanceof EntityItem) return false;
        if (entity instanceof EntityXPOrb) return false;

        return true;
    }

    private static String createContainerKey(IBlockState state, @Nullable ResourceLocation lootTableId) {
        String blockKey = CaptureBlockHelper.createKey(state);
        if (lootTableId != null) return blockKey + "|loot:" + lootTableId;

        return blockKey + "|fixed";
    }

    private static boolean isContainerExcluded(IBlockState state, @Nullable ResourceLocation lootTableId,
            StructureCaptureExclusions exclusions) {
        return exclusions.isContainerExcluded(createContainerKey(state, lootTableId));
    }

    private static boolean isBlockExcluded(IBlockState state, StructureCaptureExclusions exclusions) {
        if (CaptureBlockHelper.isAir(state)) return false;

        return exclusions.isBlockExcluded(CaptureBlockHelper.createKey(state));
    }

    private static File getCaptureDirectory(World world) {
        MinecraftServer server = world.getMinecraftServer();
        File captureDirectory = server != null ? server.getFile(CAPTURE_DIRECTORY) : new File(CAPTURE_DIRECTORY);
        if (!captureDirectory.exists()) captureDirectory.mkdirs();

        return captureDirectory;
    }

    private static int idForState(LinkedHashMap<IBlockState, Integer> palette, IBlockState state) {
        Integer currentId = palette.get(state);
        if (currentId != null) return currentId.intValue();

        int nextId = palette.size();
        palette.put(state, Integer.valueOf(nextId));
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

    private static final class BlockAccumulator {
        private final String key;
        private final IBlockState representativeState;
        private int count;

        private BlockAccumulator(String key, IBlockState representativeState) {
            this.key = key;
            this.representativeState = representativeState;
            this.count = 0;
        }
    }

    private static final class ContainerAccumulator {
        private final String key;
        private final IBlockState representativeState;
        @Nullable
        private final ResourceLocation lootTableId;
        private int containerCount;
        private int totalItemCount;

        private ContainerAccumulator(String key, IBlockState representativeState,
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
}