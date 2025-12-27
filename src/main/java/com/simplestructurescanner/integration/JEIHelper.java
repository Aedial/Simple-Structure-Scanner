package com.simplestructurescanner.integration;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IFocus;

import com.simplestructurescanner.SimpleStructureScanner;


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
     * Internal class to isolate JEI API calls.
     * This prevents ClassNotFoundErrors when JEI is not loaded.
     */
    private static class JEIIntegrationImpl {
        static boolean showRecipes(ItemStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<ItemStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT, stack);
            runtime.getRecipesGui().show(focus);
            // FIXME: should return false if no recipes found, otherwise the GUI breaks

            return true;
        }

        static boolean showUses(ItemStack stack) {
            IJeiRuntime runtime = JEIIntegration.getRuntime();
            if (runtime == null) return false;

            IFocus<ItemStack> focus = runtime.getRecipeRegistry().createFocus(IFocus.Mode.INPUT, stack);
            runtime.getRecipesGui().show(focus);
            // FIXME: same here

            return true;
        }
    }
}
