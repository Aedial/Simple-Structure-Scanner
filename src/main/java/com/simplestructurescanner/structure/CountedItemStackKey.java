package com.simplestructurescanner.structure;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.simplestructurescanner.util.ItemStackKey;


final class CountedItemStackKey {

    @Nullable
    private final ItemStackKey itemStackKey;
    private final int count;

    CountedItemStackKey(@Nullable ItemStack stack) {
        this.itemStackKey = ItemStackKey.of(stack);
        this.count = stack != null ? stack.getCount() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        CountedItemStackKey that = (CountedItemStackKey) obj;
        return count == that.count && Objects.equals(itemStackKey, that.itemStackKey);
    }

    @Override
    public int hashCode() {
        int result = itemStackKey != null ? itemStackKey.hashCode() : 0;
        return 31 * result + count;
    }
}