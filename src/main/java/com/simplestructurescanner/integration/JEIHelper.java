package com.simplestructurescanner.integration;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;


/**
 * Helper class for JEI integration.
 * Provides methods to show item recipes/uses in JEI when available.
 */
public class JEIHelper {
    private static Boolean jeiAvailable = null;

    /**
     * Check if JEI is loaded and available.
     */
    public static boolean isJEIAvailable() {
        if (jeiAvailable == null) jeiAvailable = Loader.isModLoaded("jei");

        return jeiAvailable;
    }

    /**
     * Show recipes that produce this item (crafting recipes).
     *
     * @param stack The item to show recipes for
     * @return true if JEI was opened
     */
    public static boolean showItemRecipes(ItemStack stack) {
        if (!isJEIAvailable() || stack.isEmpty()) return false;

        return JEIIntegrationImpl.showRecipes(stack);
    }

    /**
     * Show recipes that use this item as an ingredient.
     *
     * @param stack The item to show uses for
     * @return true if JEI was opened
     */
    public static boolean showItemUses(ItemStack stack) {
        if (!isJEIAvailable() || stack.isEmpty()) return false;

        return JEIIntegrationImpl.showUses(stack);
    }

    /**
     * Show recipes that produce this fluid.
     *
     * @param stack The fluid to show recipes for
     * @return true if JEI was opened
     */
    public static boolean showFluidRecipes(FluidStack stack) {
        if (!isJEIAvailable() || stack == null || stack.amount <= 0 || stack.getFluid() == null) return false;

        return JEIIntegrationImpl.showFluidRecipes(stack);
    }

    /**
     * Show recipes that use this fluid as an ingredient.
     *
     * @param stack The fluid to show uses for
     * @return true if JEI was opened
     */
    public static boolean showFluidUses(FluidStack stack) {
        if (!isJEIAvailable() || stack == null || stack.amount <= 0 || stack.getFluid() == null) return false;

        return JEIIntegrationImpl.showFluidUses(stack);
    }

    /**
     * Internal class to isolate JEI API calls.
     * This prevents ClassNotFoundErrors when JEI is not loaded.
     */
    private static class JEIIntegrationImpl {
        @SuppressWarnings("rawtypes")
        static boolean showRecipes(ItemStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<ItemStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT, stack);

            // Check if any recipes exist for this item before showing
            List<IRecipeCategory> categories = runtime.getRecipeRegistry().getRecipeCategories(focus);
            if (categories.isEmpty()) return false;

            runtime.getRecipesGui().show(focus);

            return true;
        }

        @SuppressWarnings("rawtypes")
        static boolean showUses(ItemStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<ItemStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.INPUT, stack);

            // Check if any recipes exist that use this item before showing
            List<IRecipeCategory> categories = runtime.getRecipeRegistry().getRecipeCategories(focus);
            if (categories.isEmpty()) return false;

            runtime.getRecipesGui().show(focus);

            return true;
        }

        @SuppressWarnings("rawtypes")
        static boolean showFluidRecipes(FluidStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<FluidStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT, stack);

            List<IRecipeCategory> categories = runtime.getRecipeRegistry().getRecipeCategories(focus);
            if (categories.isEmpty()) return false;

            runtime.getRecipesGui().show(focus);

            return true;
        }

        @SuppressWarnings("rawtypes")
        static boolean showFluidUses(FluidStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<FluidStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.INPUT, stack);

            List<IRecipeCategory> categories = runtime.getRecipeRegistry().getRecipeCategories(focus);
            if (categories.isEmpty()) return false;

            runtime.getRecipesGui().show(focus);

            return true;
        }
    }
}
