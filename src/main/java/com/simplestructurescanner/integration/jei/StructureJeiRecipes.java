package com.simplestructurescanner.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureProviderRegistry;


/**
 * Lazy structure recipe cache for JEI lookups.
 *
 * This cache keeps a stable wrapper layer for known structures and a separate visible layer that
 * is patched incrementally as visibility changes. As the whole plugin is dynamic (due to hidden structures),
 * JEI would need to walk through hundreds of structures containing dozens of blocks.
 * Caching allows us to just eat the cost once and then only update the relevant few structures
 * when visibility changes (gamestages, config, blacklist command).
 */
final class StructureJeiRecipes {
    /** Stable wrappers for every known structure and logical tab. */
    private static final Map<StructureJeiView, Map<ResourceLocation, StructureJeiRecipe>> RECIPES =
        new EnumMap<>(StructureJeiView.class);

    /** Currently-visible wrappers, filtered from the stable wrapper layer. */
    private static final Map<StructureJeiView, Map<ResourceLocation, StructureJeiRecipe>> VISIBLE_RECIPES =
        new EnumMap<>(StructureJeiView.class);

    /** Cached sorted snapshots of the visible wrappers. */
    private static final Map<StructureJeiView, List<StructureJeiRecipe>> SORTED_RECIPES =
        new EnumMap<>(StructureJeiView.class);

    /** Last observed StructureInfo reference for each structure id. */
    private static final Map<ResourceLocation, StructureInfo> INFO_REFERENCES = new LinkedHashMap<>();

    /** Last visible state observed for each structure id. */
    private static final Map<ResourceLocation, Boolean> LAST_VISIBILITY = new LinkedHashMap<>();

    /** Reverse bookkeeping for the visible output keys contributed by one structure. */
    private static final Map<ResourceLocation, IndexedOutputs> INDEXED_OUTPUTS = new LinkedHashMap<>();

    /** Visible block item lookup index. */
    private static final Map<String, List<StructureJeiRecipe>> BLOCK_ITEM_OUTPUTS = new LinkedHashMap<>();

    /** Visible block fluid lookup index. */
    private static final Map<String, List<StructureJeiRecipe>> BLOCK_FLUID_OUTPUTS = new LinkedHashMap<>();

    /** Visible loot item lookup index. */
    private static final Map<String, List<StructureJeiRecipe>> LOOT_ITEM_OUTPUTS = new LinkedHashMap<>();

    /** Structures whose base recipe state changed since the last visible sync. */
    private static final Set<ResourceLocation> DIRTY_STRUCTURES = new LinkedHashSet<>();

    /** Categories whose sorted visible snapshots need to be rebuilt before the next read. */
    private static final Set<StructureJeiView> DIRTY_SORTED_RECIPES = EnumSet.noneOf(StructureJeiView.class);

    /** Distinguishes the first visible sync from later delta updates. */
    private static boolean visibleStateInitialized = false;

    static {
        for (StructureJeiView view : StructureJeiView.values()) {
            RECIPES.put(view, new LinkedHashMap<>());
            VISIBLE_RECIPES.put(view, new LinkedHashMap<>());
            SORTED_RECIPES.put(view, Collections.emptyList());
        }
    }

    private StructureJeiRecipes() {
    }

    /**
     * Returns the stable wrapper for one structure and tab.
     */
    public static StructureJeiRecipe get(ResourceLocation structureId, StructureJeiView view) {
        syncBaseRecipes();
        return RECIPES.get(view).get(structureId);
    }

    /**
     * Returns the visible recipes for one category after applying the lazy visibility sync.
     */
    public static List<StructureJeiRecipe> getAllVisible(StructureJeiView view) {
        syncVisibleRecipes();

        return getSortedRecipes(view);
    }

    public static List<StructureJeiRecipe> getMatchingBlockItems(ItemStack stack) {
        syncVisibleRecipes();
        return BLOCK_ITEM_OUTPUTS.getOrDefault(StructureJeiIngredientKeys.itemKey(stack), Collections.emptyList());
    }

    public static List<StructureJeiRecipe> getMatchingBlockFluids(FluidStack stack) {
        syncVisibleRecipes();
        return BLOCK_FLUID_OUTPUTS.getOrDefault(StructureJeiIngredientKeys.fluidKey(stack), Collections.emptyList());
    }

    public static List<StructureJeiRecipe> getMatchingLootItems(ItemStack stack) {
        syncVisibleRecipes();
        return LOOT_ITEM_OUTPUTS.getOrDefault(StructureJeiIngredientKeys.itemKey(stack), Collections.emptyList());
    }

    /**
     * Synchronizes the stable wrapper layer against the provider registry.
     */
    private static void syncBaseRecipes() {
        Set<ResourceLocation> liveStructureIds = new LinkedHashSet<>(StructureProviderRegistry.getAllStructureIds());

        for (ResourceLocation knownId : new ArrayList<>(INFO_REFERENCES.keySet())) {
            if (liveStructureIds.contains(knownId)) continue;

            INFO_REFERENCES.remove(knownId);
            for (StructureJeiView view : StructureJeiView.values()) {
                RECIPES.get(view).remove(knownId);
            }
            DIRTY_STRUCTURES.add(knownId);
        }

        for (ResourceLocation structureId : liveStructureIds) {
            StructureInfo structureInfo = StructureProviderRegistry.getStructureInfo(structureId);
            if (structureInfo == null) {
                if (INFO_REFERENCES.remove(structureId) != null) DIRTY_STRUCTURES.add(structureId);
                for (StructureJeiView view : StructureJeiView.values()) {
                    if (RECIPES.get(view).remove(structureId) != null) DIRTY_STRUCTURES.add(structureId);
                }
                continue;
            }

            StructureInfo previousInfo = INFO_REFERENCES.put(structureId, structureInfo);
            if (previousInfo != structureInfo) DIRTY_STRUCTURES.add(structureId);

            for (StructureJeiView view : StructureJeiView.values()) {
                StructureJeiRecipe recipe = RECIPES.get(view).get(structureId);
                if (recipe != null) continue;

                RECIPES.get(view).put(structureId, new StructureJeiRecipe(structureId, view));
                DIRTY_STRUCTURES.add(structureId);
            }
        }
    }

    /**
     * Applies visibility deltas on top of the stable wrapper layer for the current JEI context.
     */
    private static void syncVisibleRecipes() {
        syncBaseRecipes();

        Set<ResourceLocation> knownStructureIds = new LinkedHashSet<>(INFO_REFERENCES.keySet());
        knownStructureIds.addAll(LAST_VISIBILITY.keySet());
        knownStructureIds.addAll(DIRTY_STRUCTURES);

        for (ResourceLocation structureId : knownStructureIds) {
            boolean currentVisible = INFO_REFERENCES.containsKey(structureId)
                && StructureJeiVisibility.isStructureVisible(structureId);
            boolean previousVisible = LAST_VISIBILITY.getOrDefault(structureId, false);

            if (!visibleStateInitialized || DIRTY_STRUCTURES.contains(structureId) || previousVisible != currentVisible) {
                updateVisibleStructure(structureId, currentVisible);
            }
        }

        visibleStateInitialized = true;
        DIRTY_STRUCTURES.clear();
    }

    /**
     * Re-applies the visible contribution of one structure.
     */
    private static void updateVisibleStructure(ResourceLocation structureId, boolean visible) {
        removeVisibleStructure(structureId);

        if (!INFO_REFERENCES.containsKey(structureId)) {
            LAST_VISIBILITY.remove(structureId);
            return;
        }

        LAST_VISIBILITY.put(structureId, visible);
        if (!visible) return;

        IndexedOutputs indexedOutputs = new IndexedOutputs();

        for (StructureJeiView view : StructureJeiView.values()) {
            StructureJeiRecipe recipe = RECIPES.get(view).get(structureId);
            if (recipe == null) continue;

            VISIBLE_RECIPES.get(view).put(structureId, recipe);
            DIRTY_SORTED_RECIPES.add(view);
        }

        StructureJeiRecipe blockRecipe = RECIPES.get(StructureJeiView.BLOCKS).get(structureId);
        if (blockRecipe != null) {
            for (ItemStack output : blockRecipe.getBlockItemOutputs()) {
                String key = StructureJeiIngredientKeys.itemKey(output);
                addIndexedRecipe(BLOCK_ITEM_OUTPUTS, key, blockRecipe);
                indexedOutputs.blockItemKeys.add(key);
            }

            for (FluidStack output : blockRecipe.getBlockFluidOutputs()) {
                String key = StructureJeiIngredientKeys.fluidKey(output);
                addIndexedRecipe(BLOCK_FLUID_OUTPUTS, key, blockRecipe);
                indexedOutputs.blockFluidKeys.add(key);
            }
        }

        StructureJeiRecipe lootRecipe = RECIPES.get(StructureJeiView.LOOT).get(structureId);
        if (lootRecipe != null) {
            for (ItemStack output : lootRecipe.getLootOutputStacks()) {
                String key = StructureJeiIngredientKeys.itemKey(output);
                addIndexedRecipe(LOOT_ITEM_OUTPUTS, key, lootRecipe);
                indexedOutputs.lootItemKeys.add(key);
            }
        }

        INDEXED_OUTPUTS.put(structureId, indexedOutputs);
    }

    /**
     * Adds one visible recipe wrapper to a lookup bucket.
     */
    private static void addIndexedRecipe(Map<String, List<StructureJeiRecipe>> index, String key, StructureJeiRecipe recipe) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe);
    }

    /**
     * Removes every visible contribution currently owned by one structure.
     */
    private static void removeVisibleStructure(ResourceLocation structureId) {
        IndexedOutputs indexedOutputs = INDEXED_OUTPUTS.remove(structureId);

        for (StructureJeiView view : StructureJeiView.values()) {
            if (VISIBLE_RECIPES.get(view).remove(structureId) != null) DIRTY_SORTED_RECIPES.add(view);
        }

        if (indexedOutputs == null) return;

        for (String key : indexedOutputs.blockItemKeys) {
            removeIndexedRecipe(BLOCK_ITEM_OUTPUTS, key, structureId);
        }

        for (String key : indexedOutputs.blockFluidKeys) {
            removeIndexedRecipe(BLOCK_FLUID_OUTPUTS, key, structureId);
        }

        for (String key : indexedOutputs.lootItemKeys) {
            removeIndexedRecipe(LOOT_ITEM_OUTPUTS, key, structureId);
        }
    }

    /**
     * Removes one structure from a single lookup bucket.
     */
    private static void removeIndexedRecipe(Map<String, List<StructureJeiRecipe>> index, String key, ResourceLocation structureId) {
        List<StructureJeiRecipe> recipes = index.get(key);
        if (recipes == null) return;

        recipes.removeIf(recipe -> structureId.equals(recipe.getStructureId()));
        if (recipes.isEmpty()) index.remove(key);
    }

    /**
     * Returns the sorted visible snapshot for one category, rebuilding it only when needed.
     */
    private static List<StructureJeiRecipe> getSortedRecipes(StructureJeiView view) {
        if (DIRTY_SORTED_RECIPES.contains(view)) rebuildSortedRecipes(view);

        return SORTED_RECIPES.get(view);
    }

    /**
     * Rebuilds the sorted visible snapshot for one category.
     */
    private static void rebuildSortedRecipes(StructureJeiView view) {
        List<StructureJeiRecipe> recipes = new ArrayList<>(VISIBLE_RECIPES.get(view).values());
        recipes.sort((first, second) -> first.getSortKey().compareToIgnoreCase(second.getSortKey()));
        SORTED_RECIPES.put(view, Collections.unmodifiableList(recipes));
        DIRTY_SORTED_RECIPES.remove(view);
    }

    /**
     * Reverse key set for one structure's visible indexed outputs.
     */
    private static class IndexedOutputs {
        private final List<String> blockItemKeys = new ArrayList<>();
        private final List<String> blockFluidKeys = new ArrayList<>();
        private final List<String> lootItemKeys = new ArrayList<>();
    }
}