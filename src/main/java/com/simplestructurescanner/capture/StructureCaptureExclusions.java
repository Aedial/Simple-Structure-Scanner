package com.simplestructurescanner.capture;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;


/**
 * Mutable exclusion set for block keys, entity UUIDs, and container positions.
 */
public class StructureCaptureExclusions {

    private final Set<String> excludedBlockKeys = new HashSet<>();
    private final Set<String> excludedEntityUuids = new HashSet<>();
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

    public boolean isEntityExcluded(@Nullable String uuid) {
        return uuid != null && excludedEntityUuids.contains(uuid);
    }

    public void setEntityExcluded(@Nullable String uuid, boolean excluded) {
        if (uuid == null) return;

        if (excluded) {
            excludedEntityUuids.add(uuid);
            return;
        }

        excludedEntityUuids.remove(uuid);
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

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagList blockKeyList = new NBTTagList();
        for (String blockKey : excludedBlockKeys) {
            blockKeyList.appendTag(new NBTTagString(blockKey));
        }

        NBTTagList entityUuidList = new NBTTagList();
        for (String entityUuid : excludedEntityUuids) {
            entityUuidList.appendTag(new NBTTagString(entityUuid));
        }

        NBTTagList containerList = new NBTTagList();
        for (String containerKey : excludedContainerKeys) {
            containerList.appendTag(new NBTTagString(containerKey));
        }

        tag.setTag("blocks", blockKeyList);
        tag.setTag("entities", entityUuidList);
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

        NBTTagList entityUuidList = tag.getTagList("entities", Constants.NBT.TAG_STRING);
        for (int index = 0; index < entityUuidList.tagCount(); index++) {
            exclusions.excludedEntityUuids.add(entityUuidList.getStringTagAt(index));
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
        copy.excludedEntityUuids.addAll(excludedEntityUuids);
        copy.excludedContainerKeys.addAll(excludedContainerKeys);
        return copy;
    }
}