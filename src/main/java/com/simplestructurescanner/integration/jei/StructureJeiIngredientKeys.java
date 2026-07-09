package com.simplestructurescanner.integration.jei;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;


/**
 * Builds stable lookup keys for indexed JEI structure outputs.
 */
final class StructureJeiIngredientKeys {
    private StructureJeiIngredientKeys() {
    }

    /**
     * Encodes an item output with its metadata and normalized tag state.
     */
    public static String itemKey(ItemStack stack) {
        ResourceLocation itemId = stack.getItem().getRegistryName();
        String tagKey = tagKey(stack.getTagCompound());

        return String.valueOf(itemId) + "@" + stack.getMetadata() + "#" + tagKey;
    }

    /**
     * Encodes a fluid output with its optional tag state.
     */
    public static String fluidKey(FluidStack stack) {
        String fluidId = String.valueOf(stack.getFluid().getName());
        String tagKey = tagKey(stack.tag);

        return fluidId + "#" + tagKey;
    }

    /**
     * Serializes the part of the output identity that comes from NBT data.
     */
    private static String tagKey(@Nullable NBTTagCompound tagCompound) {
        return tagCompound == null || tagCompound.isEmpty() ? "" : tagCompound.toString();
    }
}