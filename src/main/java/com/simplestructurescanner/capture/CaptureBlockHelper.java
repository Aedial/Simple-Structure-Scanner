package com.simplestructurescanner.capture;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import com.simplestructurescanner.structure.StructureInfo.BlockEntry;
import com.simplestructurescanner.structure.StructureNBTParser;


/**
 * Shared block helpers for capture previews and final NBT saves.
 *
 * The capture flow groups blocks by their displayed item or fluid identity so the UI matches the
 * rest of the scanner while the saved structure still preserves exact block states.
 */
public final class CaptureBlockHelper {

    private static final String FLUID_PREFIX = "fluid:";
     private static final String ITEM_PREFIX = "item:";
    private static final String BLOCK_PREFIX = "block:";

    private CaptureBlockHelper() {
    }

    public static boolean isAir(@Nullable IBlockState state) {
        Block block = state != null ? state.getBlock() : null;
        return block == null || block == Blocks.AIR;
    }

    public static boolean contributesToBounds(@Nullable IBlockState state) {
        return !isAir(state);
    }

    public static boolean shouldShowInSummary(@Nullable IBlockState state) {
        Block block = state != null ? state.getBlock() : null;
        return block != null && block != Blocks.AIR && block != Blocks.STRUCTURE_VOID;
    }

    public static String createKey(@Nullable IBlockState state) {
        Block block = state != null ? state.getBlock() : null;
        if (state == null || block == null) return BLOCK_PREFIX + "minecraft:air";

        FluidStack displayFluid = createDisplayFluid(state);
        if (displayFluid != null && displayFluid.getFluid() != null) {
            return FLUID_PREFIX + displayFluid.getFluid().getName();
        }

        ItemStack displayStack = StructureNBTParser.createDisplayStack(state);
        if (!displayStack.isEmpty() && displayStack.getItem() != null
                && displayStack.getItem().getRegistryName() != null) {
            return ITEM_PREFIX + displayStack.getItem().getRegistryName() + ":" + displayStack.getMetadata();
        }

        String blockId = block.getRegistryName() != null ? block.getRegistryName().toString() : "minecraft:air";
        return BLOCK_PREFIX + blockId;
    }

    @Nullable
    public static FluidStack createDisplayFluid(@Nullable IBlockState state) {
        Block block = state != null ? state.getBlock() : null;
        if (state == null || block == null) return null;

        Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
        if (fluid == null && block instanceof IFluidBlock) fluid = ((IFluidBlock) block).getFluid();
        if (fluid == null && (block == Blocks.WATER || block == Blocks.FLOWING_WATER)) fluid = FluidRegistry.getFluid("water");
        if (fluid == null && (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA)) fluid = FluidRegistry.getFluid("lava");
        if (fluid == null) return null;

        return new FluidStack(fluid, Fluid.BUCKET_VOLUME);
    }

    @Nullable
    public static BlockEntry createBlockEntry(@Nullable IBlockState state, int count) {
        if (state == null) return null;

        FluidStack displayFluid = createDisplayFluid(state);
        if (displayFluid != null) return new BlockEntry(state, null, displayFluid, count);

        return new BlockEntry(state, StructureNBTParser.createDisplayStack(state), count);
    }
}