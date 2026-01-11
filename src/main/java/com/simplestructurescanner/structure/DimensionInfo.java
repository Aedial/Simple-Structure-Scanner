package com.simplestructurescanner.structure;

import javax.annotation.Nullable;

import net.minecraft.util.text.translation.I18n;


/**
 * Holds information about a dimension for display purposes.
 * Allows mods to provide both numeric ID and localization key.
 */
public class DimensionInfo {

    private final int dimensionId;
    private final String displayKey;

    /**
     * Create dimension info with a localization key.
     * @param dimensionId The numeric dimension ID
     * @param displayKey The localization key for display (e.g., "gui.structurescanner.dimension.overworld")
     */
    public DimensionInfo(int dimensionId, String displayKey) {
        this.dimensionId = dimensionId;
        this.displayKey = displayKey;
    }

    /**
     * Create dimension info using the dimension ID as the display name.
     * Used when no localization is available.
     * @param dimensionId The numeric dimension ID
     */
    public DimensionInfo(int dimensionId) {
        this(dimensionId, null);
    }

    public int getDimensionId() {
        return dimensionId;
    }

    @Nullable
    public String getDisplayKey() {
        return displayKey;
    }

    /**
     * Get the display name for this dimension.
     * Uses the localization key if available, otherwise falls back to the numeric ID.
     */
    public String getDisplayName() {
        if (displayKey != null && I18n.canTranslate(displayKey)) return I18n.translateToLocal(displayKey);

        // Fallback: try standard vanilla dimension keys
        switch (dimensionId) {
            case -1: return I18n.translateToLocal("gui.structurescanner.dimension.nether");
            case 0: return I18n.translateToLocal("gui.structurescanner.dimension.overworld");
            case 1: return I18n.translateToLocal("gui.structurescanner.dimension.end");
            default: return I18n.translateToLocalFormatted("gui.structurescanner.dimension.unknown", dimensionId);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        DimensionInfo that = (DimensionInfo) obj;
        return dimensionId == that.dimensionId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(dimensionId);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    // Common vanilla dimensions as constants
    public static final DimensionInfo OVERWORLD = new DimensionInfo(0, "gui.structurescanner.dimension.overworld");
    public static final DimensionInfo NETHER = new DimensionInfo(-1, "gui.structurescanner.dimension.nether");
    public static final DimensionInfo END = new DimensionInfo(1, "gui.structurescanner.dimension.end");
}
