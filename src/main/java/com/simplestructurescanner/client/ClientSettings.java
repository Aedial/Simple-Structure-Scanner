package com.simplestructurescanner.client;

import com.simplestructurescanner.config.SimpleStructureScannerConfig;


/**
 * Client-side settings that are synced with config.
 */
public class ClientSettings {
    public static boolean i18nNames = true;
    public static boolean showNonSearchable = true;
    public static boolean showCurrentDimensionOnly = false;

    public static void syncFromConfig() {
        i18nNames = SimpleStructureScannerConfig.hidden.i18nNames;
        showNonSearchable = SimpleStructureScannerConfig.hidden.showNonSearchable;
        showCurrentDimensionOnly = SimpleStructureScannerConfig.hidden.showCurrentDimensionOnly;
    }

    public static void setI18nNames(boolean value) {
        i18nNames = value;
        SimpleStructureScannerConfig.setClientI18nNames(value);
    }

    public static void setShowNonSearchable(boolean value) {
        showNonSearchable = value;
        SimpleStructureScannerConfig.setClientShowNonSearchable(value);
    }

    public static void setShowCurrentDimensionOnly(boolean value) {
        showCurrentDimensionOnly = value;
        SimpleStructureScannerConfig.setClientShowCurrentDimensionOnly(value);
    }
}
