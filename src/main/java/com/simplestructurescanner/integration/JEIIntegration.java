package com.simplestructurescanner.integration;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;

import com.simplestructurescanner.integration.jei.StructureJeiCategory;
import com.simplestructurescanner.integration.jei.StructureJeiRegistryPlugin;
import com.simplestructurescanner.integration.jei.StructureJeiView;
import com.simplestructurescanner.structure.StructureProviderRegistry;


/**
 * JEI plugin for structure categories and recipe lookups.
 */
@JEIPlugin
public class JEIIntegration implements IModPlugin {
    public static final String CATEGORY_STRUCTURE_PREVIEW = StructureJeiView.PREVIEW.getCategoryUid();
    public static final String CATEGORY_STRUCTURE_BLOCKS = StructureJeiView.BLOCKS.getCategoryUid();
    public static final String CATEGORY_STRUCTURE_LOOT = StructureJeiView.LOOT.getCategoryUid();

    private static IJeiRuntime runtime = null;

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(
            new StructureJeiCategory(registry.getJeiHelpers().getGuiHelper(), StructureJeiView.PREVIEW),
            new StructureJeiCategory(registry.getJeiHelpers().getGuiHelper(), StructureJeiView.BLOCKS),
            new StructureJeiCategory(registry.getJeiHelpers().getGuiHelper(), StructureJeiView.LOOT)
        );
    }

    @Override
    public void register(IModRegistry registry) {
        StructureProviderRegistry.discoverProviders();
        registry.addRecipeRegistryPlugin(new StructureJeiRegistryPlugin());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static IJeiRuntime getRuntime() {
        return runtime;
    }

    public static boolean isRuntimeAvailable() {
        return runtime != null;
    }
}
