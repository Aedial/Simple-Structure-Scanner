package com.simplestructurescanner.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;


/**
 * Dynamic JEI registry plugin for Structure Scanner recipe lookups.
 */
public class StructureJeiRegistryPlugin implements IRecipeRegistryPlugin {
    /**
     * Resolves which structure categories currently have visible matches for one JEI focus.
     */
    @Nonnull
    @Override
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (!StructureJeiVisibility.isAnyCategoryEnabled()) return Collections.emptyList();

        StructureJeiVisibility.refreshStageSnapshot();

        Object focusValue = focus.getValue();
        if (focusValue instanceof ItemStack) return getItemCategoryUids(castFocus(focus));
        if (focusValue instanceof FluidStack) return getFluidCategoryUids(castFocus(focus));

        return Collections.emptyList();
    }

    /**
     * Resolves the matching structure wrappers for one category and one JEI focus.
     */
    @Nonnull
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        StructureJeiView view = StructureJeiView.fromCategoryUid(recipeCategory.getUid());
        if (view == null) return Collections.emptyList();
        if (!StructureJeiVisibility.isCategoryEnabled(view)) return Collections.emptyList();

        StructureJeiVisibility.refreshStageSnapshot();

        List<StructureJeiRecipe> candidates = getMatchingRecipes(view, focus);
        if (candidates.isEmpty()) return Collections.emptyList();

        List<T> matchingRecipes = new ArrayList<>();

        for (StructureJeiRecipe recipe : candidates) matchingRecipes.add(castRecipe(recipe));

        return matchingRecipes;
    }

    /**
     * Returns the full visible recipe set for one category when JEI opens that category directly.
     */
    @Nonnull
    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory) {
        StructureJeiView view = StructureJeiView.fromCategoryUid(recipeCategory.getUid());
        if (view == null) return Collections.emptyList();
        if (!StructureJeiVisibility.isCategoryEnabled(view)) return Collections.emptyList();

        StructureJeiVisibility.refreshStageSnapshot();

        List<T> visibleRecipes = new ArrayList<>();
        for (StructureJeiRecipe recipe : StructureJeiRecipes.getAllVisible(view)) visibleRecipes.add(castRecipe(recipe));

        return visibleRecipes;
    }

    /**
     * Maps an item focus to the structure categories that currently expose it.
     */
    private List<String> getItemCategoryUids(IFocus<ItemStack> focus) {
        ItemStack stack = focus.getValue();
        if (stack.isEmpty()) return Collections.emptyList();

        if (focus.getMode() == IFocus.Mode.INPUT && StructureJeiRecipe.isAnchorStack(stack)) {
            ResourceLocation structureId = StructureJeiRecipe.getAnchorStructureId(stack);
            if (!StructureJeiVisibility.isStructureVisible(structureId)) {
                return Collections.emptyList();
            }

            StructureJeiView anchorView = StructureJeiRecipe.getAnchorView(stack);
            if (anchorView != null) {
                if (!StructureJeiVisibility.isCategoryEnabled(anchorView)) return Collections.emptyList();

                return Collections.singletonList(anchorView.getCategoryUid());
            }

            List<String> categoryUids = new ArrayList<>(StructureJeiView.values().length);
            for (StructureJeiView view : StructureJeiView.values()) {
                if (!StructureJeiVisibility.isCategoryEnabled(view)) continue;
                categoryUids.add(view.getCategoryUid());
            }

            return categoryUids;
        }

        if (focus.getMode() != IFocus.Mode.OUTPUT) return Collections.emptyList();

        Set<String> categoryUids = new LinkedHashSet<>();

        if (StructureJeiVisibility.isCategoryEnabled(StructureJeiView.BLOCKS)
                && !StructureJeiRecipes.getMatchingBlockItems(stack).isEmpty()) {
            categoryUids.add(StructureJeiView.BLOCKS.getCategoryUid());
        }

        if (StructureJeiVisibility.isCategoryEnabled(StructureJeiView.LOOT)
                && !StructureJeiRecipes.getMatchingLootItems(stack).isEmpty()) {
            categoryUids.add(StructureJeiView.LOOT.getCategoryUid());
        }

        return new ArrayList<>(categoryUids);
    }

    private List<String> getFluidCategoryUids(IFocus<FluidStack> focus) {
        FluidStack stack = focus.getValue();
        if (stack.amount <= 0 || stack.getFluid() == null) return Collections.emptyList();
        if (focus.getMode() != IFocus.Mode.OUTPUT) return Collections.emptyList();
        if (!StructureJeiVisibility.isCategoryEnabled(StructureJeiView.BLOCKS)) return Collections.emptyList();

        if (!StructureJeiRecipes.getMatchingBlockFluids(stack).isEmpty()) {
            return Collections.singletonList(StructureJeiView.BLOCKS.getCategoryUid());
        }

        return Collections.emptyList();
    }

    /**
     * Narrows one JEI focus down to the recipe wrappers that belong to a single structure tab.
     */
    private List<StructureJeiRecipe> getMatchingRecipes(StructureJeiView view, IFocus<?> focus) {
        Object focusValue = focus.getValue();

        if (focusValue instanceof ItemStack) {
            ItemStack stack = (ItemStack) focusValue;

            if (focus.getMode() == IFocus.Mode.INPUT && StructureJeiRecipe.isAnchorStack(stack)) {
                ResourceLocation structureId = StructureJeiRecipe.getAnchorStructureId(stack);
                StructureJeiView anchorView = StructureJeiRecipe.getAnchorView(stack);
                if (anchorView != null && anchorView != view) return Collections.emptyList();
                if (!StructureJeiVisibility.isStructureVisible(structureId)) return Collections.emptyList();

                StructureJeiRecipe recipe = StructureJeiRecipes.get(structureId, view);
                return recipe != null ? Collections.singletonList(recipe) : Collections.emptyList();
            }

            if (focus.getMode() != IFocus.Mode.OUTPUT) return Collections.emptyList();

            if (view == StructureJeiView.BLOCKS) return StructureJeiRecipes.getMatchingBlockItems(stack);
            if (view == StructureJeiView.LOOT) return StructureJeiRecipes.getMatchingLootItems(stack);

            return Collections.emptyList();
        }

        if (focusValue instanceof FluidStack) {
            if (view != StructureJeiView.BLOCKS || focus.getMode() != IFocus.Mode.OUTPUT) return Collections.emptyList();

            return StructureJeiRecipes.getMatchingBlockFluids((FluidStack) focusValue);
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static <V> IFocus<V> castFocus(IFocus<?> focus) {
        return (IFocus<V>) focus;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IRecipeWrapper> T castRecipe(StructureJeiRecipe recipe) {
        return (T) recipe;
    }
}