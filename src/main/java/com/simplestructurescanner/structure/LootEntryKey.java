package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.structure.StructureInfo.LootEntry;
import com.simplestructurescanner.structure.StructureInfo.LootEntryKind;
import com.simplestructurescanner.util.ItemStackKey;


public final class LootEntryKey {

    @Nullable
    private final ResourceLocation lootTableId;
    private final LootEntryKind kind;
    @Nullable
    private final LocalizedTextKey containerType;
    @Nullable
    private final LocalizedTextKey sourceName;
    @Nullable
    private final ItemStackKey sourceStack;
    private final List<CountedItemStackKey> possibleDrops;

    public LootEntryKey(@Nonnull final LootEntry lootEntry) {
        this.lootTableId = lootEntry.lootTableId;
        this.kind = lootEntry.kind;
        this.containerType = LocalizedTextKey.of(lootEntry.containerType);
        this.sourceName = LocalizedTextKey.of(lootEntry.sourceName);
        this.sourceStack = ItemStackKey.of(lootEntry.sourceStack);
        this.possibleDrops = createPossibleDropKeys(lootEntry.possibleDrops);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        LootEntryKey that = (LootEntryKey) obj;
        return kind == that.kind
            && Objects.equals(lootTableId, that.lootTableId)
            && Objects.equals(containerType, that.containerType)
            && Objects.equals(sourceName, that.sourceName)
            && Objects.equals(sourceStack, that.sourceStack)
            && possibleDrops.equals(that.possibleDrops);
    }

    @Override
    public int hashCode() {
        int result = lootTableId != null ? lootTableId.hashCode() : 0;
        result = 31 * result + kind.hashCode();
        result = 31 * result + (containerType != null ? containerType.hashCode() : 0);
        result = 31 * result + (sourceName != null ? sourceName.hashCode() : 0);
        result = 31 * result + (sourceStack != null ? sourceStack.hashCode() : 0);
        return 31 * result + possibleDrops.hashCode();
    }

    private static List<CountedItemStackKey> createPossibleDropKeys(@Nullable List<ItemStack> possibleDrops) {
        List<CountedItemStackKey> itemKeys = new ArrayList<>();
        if (possibleDrops == null) return itemKeys;

        for (ItemStack stack : possibleDrops) itemKeys.add(new CountedItemStackKey(stack));

        return itemKeys;
    }
}