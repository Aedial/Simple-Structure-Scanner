package com.simplestructurescanner.integration.jei;

import java.util.Objects;

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

    public static IngredientKey itemLookupKey(ItemStack stack) {
        return IngredientKey.forItem(stack, false);
    }

    public static IngredientKey itemStorageKey(ItemStack stack) {
        return IngredientKey.forItem(stack, true);
    }

    public static IngredientKey fluidLookupKey(FluidStack stack) {
        return IngredientKey.forFluid(stack, false);
    }

    public static IngredientKey fluidStorageKey(FluidStack stack) {
        return IngredientKey.forFluid(stack, true);
    }

    @Nullable
    private static NBTTagCompound normalizeTag(@Nullable NBTTagCompound tagCompound, boolean copyTag) {
        if (tagCompound == null || tagCompound.isEmpty()) return null;

        return copyTag ? tagCompound.copy() : tagCompound;
    }

    static final class IngredientKey {
        private final boolean fluid;
        @Nullable
        private final ResourceLocation itemId;
        @Nullable
        private final String fluidId;
        private final int metadata;
        @Nullable
        private final NBTTagCompound tag;
        private final boolean storageKey;
        private final int hashCode;

        private IngredientKey(boolean fluid, @Nullable ResourceLocation itemId, @Nullable String fluidId,
                int metadata, @Nullable NBTTagCompound tag, boolean storageKey) {
            this.fluid = fluid;
            this.itemId = itemId;
            this.fluidId = fluidId;
            this.metadata = metadata;
            this.tag = tag;
            this.storageKey = storageKey;

            int hash = Boolean.valueOf(fluid).hashCode();
            hash = 31 * hash + Objects.hashCode(itemId);
            hash = 31 * hash + Objects.hashCode(fluidId);
            hash = 31 * hash + metadata;
            hash = 31 * hash + Objects.hashCode(tag);
            this.hashCode = hash;
        }

        private static IngredientKey forItem(ItemStack stack, boolean storageKey) {
            return new IngredientKey(false, stack.getItem().getRegistryName(), null, stack.getMetadata(),
                normalizeTag(stack.getTagCompound(), storageKey), storageKey);
        }

        private static IngredientKey forFluid(FluidStack stack, boolean storageKey) {
            return new IngredientKey(true, null, stack.getFluid().getName(), 0,
                normalizeTag(stack.tag, storageKey), storageKey);
        }

        public IngredientKey copyForStorage() {
            if (storageKey) return this;

            return new IngredientKey(fluid, itemId, fluidId, metadata, normalizeTag(tag, true), true);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IngredientKey)) return false;

            IngredientKey that = (IngredientKey) other;
            return fluid == that.fluid
                && metadata == that.metadata
                && Objects.equals(itemId, that.itemId)
                && Objects.equals(fluidId, that.fluidId)
                && Objects.equals(tag, that.tag);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}