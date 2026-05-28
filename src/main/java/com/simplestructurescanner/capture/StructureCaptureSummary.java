package com.simplestructurescanner.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import com.simplestructurescanner.structure.StructureInfo.BlockEntry;


/**
 * Serializable preview summary sent from the server to the client before saving a capture.
 */
public class StructureCaptureSummary {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<BlockSummary> blocks;
    private final List<EntityInstance> entities;
    private final List<ContainerSummary> containers;

    public StructureCaptureSummary(int sizeX, int sizeY, int sizeZ, List<BlockSummary> blocks,
            List<EntityInstance> entities, List<ContainerSummary> containers) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        this.containers = Collections.unmodifiableList(new ArrayList<>(containers));
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

    public List<BlockSummary> getBlocks() {
        return blocks;
    }

    public List<EntityInstance> getEntities() {
        return entities;
    }

    public List<ContainerSummary> getContainers() {
        return containers;
    }

    public int getTotalBlockCount() {
        int total = 0;
        for (BlockSummary block : blocks) total += block.getCount();

        return total;
    }

    public int getEntityTypeCount() {
        Set<ResourceLocation> entityTypes = new HashSet<>();
        for (EntityInstance entity : entities) entityTypes.add(entity.getEntityId());

        return entityTypes.size();
    }

    public int getTotalContainerCount() {
        int total = 0;
        for (ContainerSummary container : containers) total += container.getContainerCount();

        return total;
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("sizeX", sizeX);
        tag.setInteger("sizeY", sizeY);
        tag.setInteger("sizeZ", sizeZ);

        NBTTagList blockList = new NBTTagList();
        for (BlockSummary block : blocks) {
            blockList.appendTag(block.toNBT());
        }

        NBTTagList entityList = new NBTTagList();
        for (EntityInstance entity : entities) {
            entityList.appendTag(entity.toNBT());
        }

        NBTTagList containerList = new NBTTagList();
        for (ContainerSummary container : containers) {
            containerList.appendTag(container.toNBT());
        }

        tag.setTag("blocks", blockList);
        tag.setTag("entities", entityList);
        tag.setTag("containers", containerList);

        return tag;
    }

    public static StructureCaptureSummary fromNBT(@Nullable NBTTagCompound tag) {
        if (tag == null) {
            return new StructureCaptureSummary(0, 0, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<BlockSummary> blocks = new ArrayList<>();
        NBTTagList blockList = tag.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < blockList.tagCount(); index++) {
            blocks.add(BlockSummary.fromNBT(blockList.getCompoundTagAt(index)));
        }

        List<EntityInstance> entities = new ArrayList<>();
        NBTTagList entityList = tag.getTagList("entities", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < entityList.tagCount(); index++) {
            entities.add(EntityInstance.fromNBT(entityList.getCompoundTagAt(index)));
        }

        List<ContainerSummary> containers = new ArrayList<>();
        NBTTagList containerList = tag.getTagList("containers", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < containerList.tagCount(); index++) {
            containers.add(ContainerSummary.fromNBT(containerList.getCompoundTagAt(index)));
        }

        return new StructureCaptureSummary(
            tag.getInteger("sizeX"),
            tag.getInteger("sizeY"),
            tag.getInteger("sizeZ"),
            blocks,
            entities,
            containers
        );
    }

    public static class BlockSummary {
        private final String key;
        private final IBlockState blockState;
        private final int count;

        public BlockSummary(String key, IBlockState blockState, int count) {
            this.key = key;
            this.blockState = blockState;
            this.count = count;
        }

        public String getKey() {
            return key;
        }

        public IBlockState getBlockState() {
            return blockState;
        }

        public int getCount() {
            return count;
        }

        @Nullable
        public BlockEntry toBlockEntry() {
            return CaptureBlockHelper.createBlockEntry(blockState, count);
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("key", key);
            tag.setTag("state", NBTUtil.writeBlockState(new NBTTagCompound(), blockState));
            tag.setInteger("count", count);
            return tag;
        }

        public static BlockSummary fromNBT(NBTTagCompound tag) {
            return new BlockSummary(
                tag.getString("key"),
                NBTUtil.readBlockState(tag.getCompoundTag("state")),
                tag.getInteger("count")
            );
        }
    }

    public static class EntityInstance {
        private final String uuid;
        private final ResourceLocation entityId;
        private final BlockPos blockPos;

        public EntityInstance(String uuid, ResourceLocation entityId, BlockPos blockPos) {
            this.uuid = uuid;
            this.entityId = entityId;
            this.blockPos = blockPos;
        }

        public String getUuid() {
            return uuid;
        }

        public ResourceLocation getEntityId() {
            return entityId;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("uuid", uuid);
            tag.setString("entityId", entityId.toString());
            tag.setLong("blockPos", blockPos.toLong());
            return tag;
        }

        public static EntityInstance fromNBT(NBTTagCompound tag) {
            return new EntityInstance(
                tag.getString("uuid"),
                new ResourceLocation(tag.getString("entityId")),
                BlockPos.fromLong(tag.getLong("blockPos"))
            );
        }
    }

    public static class ContainerSummary {
        private final String key;
        private final IBlockState blockState;
        @Nullable
        private final ResourceLocation lootTableId;
        private final int containerCount;
        private final int totalItemCount;

        public ContainerSummary(String key, IBlockState blockState, @Nullable ResourceLocation lootTableId,
                int containerCount, int totalItemCount) {
            this.key = key;
            this.blockState = blockState;
            this.lootTableId = lootTableId;
            this.containerCount = containerCount;
            this.totalItemCount = totalItemCount;
        }

        public String getKey() {
            return key;
        }

        public IBlockState getBlockState() {
            return blockState;
        }

        @Nullable
        public ResourceLocation getLootTableId() {
            return lootTableId;
        }

        public int getContainerCount() {
            return containerCount;
        }

        public int getTotalItemCount() {
            return totalItemCount;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("key", key);
            tag.setTag("state", NBTUtil.writeBlockState(new NBTTagCompound(), blockState));
            if (lootTableId != null) tag.setString("lootTableId", lootTableId.toString());
            tag.setInteger("containerCount", containerCount);
            tag.setInteger("totalItemCount", totalItemCount);
            return tag;
        }

        public static ContainerSummary fromNBT(NBTTagCompound tag) {
            ResourceLocation lootTableId = null;
            if (tag.hasKey("lootTableId")) lootTableId = new ResourceLocation(tag.getString("lootTableId"));

            return new ContainerSummary(
                tag.getString("key"),
                NBTUtil.readBlockState(tag.getCompoundTag("state")),
                lootTableId,
                tag.getInteger("containerCount"),
                tag.getInteger("totalItemCount")
            );
        }
    }
}