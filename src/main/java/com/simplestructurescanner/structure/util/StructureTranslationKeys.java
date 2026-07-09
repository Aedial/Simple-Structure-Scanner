package com.simplestructurescanner.structure.util;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.util.ResourceLocation;


/**
 * Shared helpers for generated structure translation keys.
 * <p>
 * These methods generate translation keys only. They must not be used to create
 * human-readable fallback names for user-authored structures. If a provider exposes
 * user-made content from configs or external folders, the content author is expected
 * to provide matching localization entries for the generated keys.
 */
public final class StructureTranslationKeys {

    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile(
        "(?<=[A-Za-z])(?=[A-Z][a-z])|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])");

    private StructureTranslationKeys() {
    }

    public static String providerNameKey(String providerId) {
        return "gui.structurescanner.provider." + providerId;
    }

    public static String structureNameKey(ResourceLocation id) {
        return structureNameKey(id.getNamespace(), id.getPath());
    }

    public static String structureNameKey(String namespace, String path) {
        return "gui.structurescanner.structures." + namespace + "." + path.replace('/', '.');
    }

    public static String normalizedStructureNameKey(String namespace, String rawId) {
        String normalized = CAMEL_CASE_BOUNDARY.matcher(rawId).replaceAll("_")
            .replace('/', '.')
            .replace('-', '_')
            .replace(' ', '_')
            .replaceAll("_+", "_")
            .toLowerCase(Locale.ROOT);

        return structureNameKey(namespace, normalized);
    }
}