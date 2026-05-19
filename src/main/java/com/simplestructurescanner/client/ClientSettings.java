package com.simplestructurescanner.client;

import com.simplestructurescanner.config.ModConfig;


/**
 * Client-side settings that are synced with config.
 */
public class ClientSettings {
    public static boolean i18nNames = true;
    public static boolean showNonSearchable = true;
    public static boolean showCurrentDimensionOnly = false;

    public static void syncFromConfig() {
        i18nNames = ModConfig.clientI18nNames;
        showNonSearchable = ModConfig.clientShowNonSearchable;
        showCurrentDimensionOnly = ModConfig.clientShowCurrentDimensionOnly;
    }

    public static void setI18nNames(boolean value) {
        i18nNames = value;
        ModConfig.setClientI18nNames(value);
    }

    public static void setShowNonSearchable(boolean value) {
        showNonSearchable = value;
        ModConfig.setClientShowNonSearchable(value);
    }

    public static void setShowCurrentDimensionOnly(boolean value) {
        showCurrentDimensionOnly = value;
        ModConfig.setClientShowCurrentDimensionOnly(value);
    }
}
