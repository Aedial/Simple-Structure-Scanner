package com.simplestructurescanner.structure;

import javax.annotation.Nullable;


/**
 * Holds information about a dimension for display purposes.
 * Allows mods to provide both numeric ID and localization key.
 */
public class DimensionInfo {

    private static final String DIMENSION_ID_KEY_PREFIX = "gui.structurescanner.dimension.id.";

    private final int dimensionId;
    private final LocalizedText displayName;

    /**
     * Create dimension info with a localization key.
     * @param dimensionId The numeric dimension ID
     * @param displayKey The localization key for display (e.g., "gui.structurescanner.dimension.overworld")
     */
    public DimensionInfo(int dimensionId, String displayKey) {
        this(dimensionId, displayKey != null ? LocalizedText.translatable(displayKey) : getDefaultDisplayName(dimensionId));
    }

    public DimensionInfo(int dimensionId, LocalizedText displayName) {
        this.dimensionId = dimensionId;
        this.displayName = displayName;
    }

    /**
     * Create dimension info using the default localized fallback for this dimension.
     * @param dimensionId The numeric dimension ID
     */
    public DimensionInfo(int dimensionId) {
        this(dimensionId, getDefaultDisplayName(dimensionId));
    }

    public int getDimensionId() {
        return dimensionId;
    }

    @Nullable
    public String getDisplayKey() {
        if (!displayName.isTranslatable()) return null;

        return displayName.getValue();
    }

    /**
     * Get the localized text descriptor for this dimension.
     */
    public LocalizedText getDisplayName() {
        return displayName;
    }

    private static LocalizedText getDefaultDisplayName(int dimensionId) {
        switch (dimensionId) {
            case -1: return LocalizedText.translatable("gui.structurescanner.dimension.nether");
            case 0: return LocalizedText.translatable("gui.structurescanner.dimension.overworld");
            case 1: return LocalizedText.translatable("gui.structurescanner.dimension.end");
            default:
                return LocalizedText.translatableWithFallback(getGeneratedDisplayKey(dimensionId),
                    LocalizedText.translatable("gui.structurescanner.dimension.unknown", dimensionId));
        }
    }

    public static String getGeneratedDisplayKey(int dimensionId) {
        return DIMENSION_ID_KEY_PREFIX + dimensionId;
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
        return displayName.toString();
    }

    // Common vanilla dimensions as constants
    public static final DimensionInfo OVERWORLD = new DimensionInfo(0, "gui.structurescanner.dimension.overworld");
    public static final DimensionInfo NETHER = new DimensionInfo(-1, "gui.structurescanner.dimension.nether");
    public static final DimensionInfo END = new DimensionInfo(1, "gui.structurescanner.dimension.end");
}
