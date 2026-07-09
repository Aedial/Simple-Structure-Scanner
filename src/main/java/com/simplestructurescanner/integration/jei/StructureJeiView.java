package com.simplestructurescanner.integration.jei;

import javax.annotation.Nullable;


/**
 * Logical JEI tabs exposed for one structure entry.
 */
public enum StructureJeiView {
    // TODO: Replace the letters with proper icons once we have the textures
    PREVIEW("simplestructurescanner.structure_preview", "jei.category.simplestructurescanner.structure_preview", "P"),
    BLOCKS("simplestructurescanner.structure_blocks", "jei.category.simplestructurescanner.structure_blocks", "B"),
    LOOT("simplestructurescanner.structure_loot", "jei.category.simplestructurescanner.structure_loot", "L");

    private final String categoryUid;
    private final String titleKey;
    private final String buttonLabel;

    StructureJeiView(String categoryUid, String titleKey, String buttonLabel) {
        this.categoryUid = categoryUid;
        this.titleKey = titleKey;
        this.buttonLabel = buttonLabel;
    }

    public String getCategoryUid() {
        return categoryUid;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    /**
     * Resolves a JEI category uid back to the matching structure tab.
     */
    @Nullable
    public static StructureJeiView fromCategoryUid(String categoryUid) {
        for (StructureJeiView view : values()) {
            if (view.categoryUid.equals(categoryUid)) return view;
        }

        return null;
    }
}