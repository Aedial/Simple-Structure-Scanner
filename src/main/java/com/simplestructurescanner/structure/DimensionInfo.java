package com.simplestructurescanner.structure;

import javax.annotation.Nullable;

import net.minecraft.world.DimensionType;
import net.minecraftforge.common.DimensionManager;


/**
 * Holds information about a dimension for display purposes.
 * Uses the registered dimension type when a provider does not supply a name.
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
     * Create dimension info using the registered dimension type for display.
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
        if (DimensionManager.isDimensionRegistered(dimensionId)) {
            DimensionType dimensionType = DimensionManager.getProviderType(dimensionId);
            if (dimensionType != null) return LocalizedText.translatable("gui.structurescanner.dimension.unknown",
                dimensionType.getName(), dimensionId);
        }

        return LocalizedText.translatable("gui.structurescanner.dimension.unknown", dimensionId);
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
    public static final DimensionInfo OVERWORLD = new DimensionInfo(0);
    public static final DimensionInfo NETHER = new DimensionInfo(-1);
    public static final DimensionInfo END = new DimensionInfo(1);
}
