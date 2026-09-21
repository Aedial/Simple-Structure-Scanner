package com.simplestructurescanner.capture;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.structure.BlockDisplayKey;


public final class CaptureContainerKey {

    private final BlockDisplayKey blockKey;
    @Nullable
    private final ResourceLocation lootTableId;

    public CaptureContainerKey(@Nonnull BlockDisplayKey blockKey, @Nullable ResourceLocation lootTableId) {
        this.blockKey = blockKey;
        this.lootTableId = lootTableId;
    }

    public BlockDisplayKey getBlockKey() {
        return blockKey;
    }

    @Nullable
    public ResourceLocation getLootTableId() {
        return lootTableId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CaptureContainerKey that = (CaptureContainerKey) obj;
        return blockKey.equals(that.blockKey) && Objects.equals(lootTableId, that.lootTableId);
    }

    @Override
    public int hashCode() {
        int result = blockKey.hashCode();
        return 31 * result + (lootTableId != null ? lootTableId.hashCode() : 0);
    }

    @Override
    public String toString() {
        if (lootTableId != null) return blockKey + "|loot:" + lootTableId;

        return blockKey + "|fixed";
    }
}