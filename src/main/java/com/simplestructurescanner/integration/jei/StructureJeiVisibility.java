package com.simplestructurescanner.integration.jei;

import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.client.integration.GameStagesIntegration;
import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.structure.StructureSearchOverrides;


/**
 * Centralized visibility and config gates for JEI structure content.
 */
public final class StructureJeiVisibility {
    private StructureJeiVisibility() {
    }

    /**
     * Refreshes the stage-qualified visibility snapshot before a JEI lookup.
     */
    public static void refreshStageSnapshot() {
        StructureSearchOverrides.setActiveStageSnapshot(GameStagesIntegration.captureClientStages());
    }

    /**
     * Checks whether one structure is allowed to surface in JEI.
     */
    public static boolean isStructureVisible(ResourceLocation structureId) {
        refreshStageSnapshot();
        if (StructureProviderRegistry.getStructureInfo(structureId) == null) return false;
        if (StructureProviderRegistry.isStructureHidden(structureId)) return false;

        return !ModConfig.isBlacklisted(structureId.toString());
    }

    /**
     * Applies the master JEI toggle together with the per-tab toggle for one view.
     */
    public static boolean isCategoryEnabled(StructureJeiView view) {
        if (!ModConfig.isClientJeiCategoriesEnabled()) return false;

        switch (view) {
            case PREVIEW:
                return ModConfig.isClientJeiPreviewEnabled();
            case BLOCKS:
                return ModConfig.isClientJeiBlocksEnabled();
            case LOOT:
                return ModConfig.isClientJeiLootEnabled();
            default:
                return false;
        }
    }

    /**
     * Fast gate for callers that want to skip all JEI work when every structure tab is disabled.
     */
    public static boolean isAnyCategoryEnabled() {
        if (!ModConfig.isClientJeiCategoriesEnabled()) return false;

        for (StructureJeiView view : StructureJeiView.values()) {
            if (isCategoryEnabled(view)) return true;
        }

        return false;
    }
}