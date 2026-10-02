package com.simplestructurescanner.config;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Config;

import com.simplestructurescanner.Tags;


@Config(modid = Tags.MODID, name = Tags.MODID, category = "enabledProviders")
public final class ProviderConfig {

    @Config.Name("providers")
    @Config.LangKey("config.structurescanner.enabledProviders")
    @Config.RequiresMcRestart
    public static Map<String, Boolean> providers = new LinkedHashMap<>();

    private ProviderConfig() {
    }

    public static boolean isEnabled(String providerId) {
        if (providerId == null || providerId.isEmpty()) return true;

        Boolean enabled = providers.get(providerId);
        if (enabled == null) {
            providers.put(providerId, true);
            return true;
        }

        return enabled;
    }
}
