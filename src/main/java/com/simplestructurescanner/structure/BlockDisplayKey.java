package com.simplestructurescanner.structure;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

import com.simplestructurescanner.util.ItemStackKey;


public final class BlockDisplayKey {

    private static final String FLUID_PREFIX = "fluid:";
    private static final String ITEM_PREFIX = "item:";
    private static final String BLOCK_PREFIX = "block:";

    private final Kind kind;
    @Nullable
    private final Block block;
    @Nullable
    private final Fluid fluid;
    @Nullable
    private final ItemStackKey itemStackKey;

    private BlockDisplayKey(Kind kind, @Nullable Block block, @Nullable Fluid fluid,
            @Nullable ItemStackKey itemStackKey) {
        this.kind = kind;
        this.block = block;
        this.fluid = fluid;
        this.itemStackKey = itemStackKey;
    }

    public static BlockDisplayKey forBlock(@Nullable Block block) {
        return new BlockDisplayKey(Kind.BLOCK, block != null ? block : Blocks.AIR, null, null);
    }

    public static BlockDisplayKey forFluid(@Nonnull Fluid fluid) {
        return new BlockDisplayKey(Kind.FLUID, null, fluid, null);
    }

    @Nullable
    public static BlockDisplayKey forItem(@Nullable ItemStack displayStack) {
        ItemStackKey itemStackKey = ItemStackKey.of(displayStack);
        if (itemStackKey == null) return null;

        return new BlockDisplayKey(Kind.ITEM, null, null, itemStackKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        BlockDisplayKey that = (BlockDisplayKey) obj;
        if (kind != that.kind) return false;

        switch (kind) {
            case FLUID:
                return fluid == that.fluid;

            case ITEM:
                return Objects.equals(itemStackKey, that.itemStackKey);

            case BLOCK:
            default:
                return block == that.block;
        }
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();

        switch (kind) {
            case FLUID:
                return 31 * result + fluid.hashCode();

            case ITEM:
                return 31 * result + itemStackKey.hashCode();

            case BLOCK:
            default:
                return 31 * result + block.hashCode();
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            case FLUID:
                return FLUID_PREFIX + fluid.getName();

            case ITEM:
                return ITEM_PREFIX + itemStackKey;

            case BLOCK:
            default:
                ResourceLocation blockId = block != null && block.getRegistryName() != null
                    ? block.getRegistryName()
                    : Blocks.AIR.getRegistryName();
                return BLOCK_PREFIX + blockId;
        }
    }

    private enum Kind {
        BLOCK,
        FLUID,
        ITEM,
    }
}