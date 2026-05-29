package com.simplestructurescanner.capture;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;


/**
 * Mutable exclusion set for block keys, entity IDs, and container positions.
 */
public class StructureCaptureExclusions {

    private final Set<String> excludedBlockKeys = new HashSet<>();
    private final Set<String> excludedEntityIds = new HashSet<>();
    private final Set<String> excludedContainerKeys = new HashSet<>();

    public boolean isBlockExcluded(@Nullable String key) {
        return key != null && excludedBlockKeys.contains(key);
    }

    public void setBlockExcluded(@Nullable String key, boolean excluded) {
        if (key == null) return;

        if (excluded) {
            excludedBlockKeys.add(key);
            return;
        }

        excludedBlockKeys.remove(key);
    }

    public boolean isEntityExcluded(@Nullable String entityId) {
        return entityId != null && excludedEntityIds.contains(entityId);
    }

    public boolean isEntityExcluded(@Nullable ResourceLocation entityId) {
        return isEntityExcluded(createEntityKey(entityId));
    }

    public void setEntityExcluded(@Nullable String entityId, boolean excluded) {
        if (entityId == null) return;

        if (excluded) {
            excludedEntityIds.add(entityId);
            return;
        }

        excludedEntityIds.remove(entityId);
    }

    public void setEntityExcluded(@Nullable ResourceLocation entityId, boolean excluded) {
        setEntityExcluded(createEntityKey(entityId), excluded);
    }

    public boolean isContainerExcluded(@Nullable String key) {
        return key != null && excludedContainerKeys.contains(key);
    }

    public void setContainerExcluded(@Nullable String key, boolean excluded) {
        if (key == null) return;

        if (excluded) {
            excludedContainerKeys.add(key);
            return;
        }

        excludedContainerKeys.remove(key);
    }

    public void clear() {
        excludedBlockKeys.clear();
        excludedEntityIds.clear();
        excludedContainerKeys.clear();
    }

    public boolean isEmpty() {
        return excludedBlockKeys.isEmpty() && excludedEntityIds.isEmpty() && excludedContainerKeys.isEmpty();
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList blockKeyList = new NBTTagList();
        for (String blockKey : excludedBlockKeys) {
            blockKeyList.appendTag(new NBTTagString(blockKey));
        }

        NBTTagList entityIdList = new NBTTagList();
        for (String entityId : excludedEntityIds) {
            entityIdList.appendTag(new NBTTagString(entityId));
        }

        NBTTagList containerList = new NBTTagList();
        for (String containerKey : excludedContainerKeys) {
            containerList.appendTag(new NBTTagString(containerKey));
        }

        tag.setTag("blocks", blockKeyList);
        tag.setTag("entities", entityIdList);
        tag.setTag("containers", containerList);
        return tag;
    }

    public static StructureCaptureExclusions fromNBT(@Nullable NBTTagCompound tag) {
        StructureCaptureExclusions exclusions = new StructureCaptureExclusions();
        if (tag == null) return exclusions;

        NBTTagList blockKeyList = tag.getTagList("blocks", Constants.NBT.TAG_STRING);
        for (int index = 0; index < blockKeyList.tagCount(); index++) {
            exclusions.excludedBlockKeys.add(blockKeyList.getStringTagAt(index));
        }

        NBTTagList entityIdList = tag.getTagList("entities", Constants.NBT.TAG_STRING);
        for (int index = 0; index < entityIdList.tagCount(); index++) {
            exclusions.excludedEntityIds.add(entityIdList.getStringTagAt(index));
        }

        NBTTagList containerList = tag.getTagList("containers", Constants.NBT.TAG_STRING);
        for (int index = 0; index < containerList.tagCount(); index++) {
            exclusions.excludedContainerKeys.add(containerList.getStringTagAt(index));
        }

        return exclusions;
    }

    public StructureCaptureExclusions copy() {
        StructureCaptureExclusions copy = new StructureCaptureExclusions();
        copy.excludedBlockKeys.addAll(excludedBlockKeys);
        copy.excludedEntityIds.addAll(excludedEntityIds);
        copy.excludedContainerKeys.addAll(excludedContainerKeys);
        return copy;
    }

    @Nullable
    public static String createEntityKey(@Nullable ResourceLocation entityId) {
        return entityId == null ? null : entityId.toString();
    }
}