package com.simplestructurescanner.client;

import com.simplestructurescanner.config.ModConfig;


/**
 * Client-side settings that are synced with config.
 */
public class ClientSettings {
    public static boolean i18nNames = true;

    public static void syncFromConfig() {
        i18nNames = ModConfig.clientI18nNames;
    }

    public static void setI18nNames(boolean value) {
        i18nNames = value;
        ModConfig.setClientI18nNames(value);
    }
}
