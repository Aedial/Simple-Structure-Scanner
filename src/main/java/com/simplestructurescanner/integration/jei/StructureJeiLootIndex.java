package com.simplestructurescanner.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.simplestructurescanner.structure.LootItemAccumulator;
import com.simplestructurescanner.structure.LootTableResolver;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;


/**
 * Lookup buckets for exact loot variants and groups that reach the configured threshold.
 */
public final class StructureJeiLootIndex {
    private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> exactOutputs =
        new HashMap<>();
    private final Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> groupedOutputs =
        new HashMap<>();
    private final Map<StructureJeiRecipe, LootItemAccumulator> recipeItems = new HashMap<>();

    public void add(LootItem item, StructureJeiRecipe recipe) {
        if (item == null || item.stack.isEmpty() || item.dropCount <= 0) return;

        LootItemAccumulator accumulator = recipeItems.computeIfAbsent(recipe,
            ignored -> new LootItemAccumulator());
        accumulator.add(item);

        if (accumulator.isGrouped(item.stack)) {
            ItemStack stack = accumulator.getDisplayStack(item.stack);
            addRecipe(groupedOutputs, StructureJeiIngredientKeys.itemStorageKey(stack), recipe);
        } else {
            addRecipe(exactOutputs, StructureJeiIngredientKeys.itemStorageKey(item.stack), recipe);
        }
    }

    public List<StructureJeiRecipe> getMatches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();

        ItemStack normalized = LootTableResolver.normalizeForDisplay(stack);
        StructureJeiIngredientKeys.IngredientKey exactKey = StructureJeiIngredientKeys.itemLookupKey(normalized);
        List<StructureJeiRecipe> exactMatches = exactOutputs.getOrDefault(exactKey, Collections.emptyList());
        normalized.setTagCompound(null);
        StructureJeiIngredientKeys.IngredientKey groupKey = StructureJeiIngredientKeys.itemLookupKey(normalized);
        List<StructureJeiRecipe> groupedMatches = groupedOutputs.getOrDefault(groupKey, Collections.emptyList());
        if (exactMatches.isEmpty()) return groupedMatches;
        if (groupedMatches.isEmpty()) return exactMatches;

        Set<StructureJeiRecipe> matches = new LinkedHashSet<>(exactMatches);
        matches.addAll(groupedMatches);
        return new ArrayList<>(matches);
    }

    public int size() {
        return exactOutputs.size() + groupedOutputs.size();
    }

    private static void addRecipe(
            Map<StructureJeiIngredientKeys.IngredientKey, List<StructureJeiRecipe>> outputs,
            StructureJeiIngredientKeys.IngredientKey key, StructureJeiRecipe recipe) {
        List<StructureJeiRecipe> recipes = outputs.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!recipes.contains(recipe)) recipes.add(recipe);
    }
}
