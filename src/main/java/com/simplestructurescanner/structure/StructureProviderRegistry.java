package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.vanilla.VanillaStructureProvider;


/**
 * Registry for structure providers.
 * Manages multiple providers from different mods.
 */
public class StructureProviderRegistry {
    private static final List<StructureProvider> providers = new ArrayList<>();
    private static final Map<ResourceLocation, StructureProvider> structureToProvider = new HashMap<>();
    private static boolean initialized = false;

    /**
     * Discover and register all available structure providers.
     * Called during mod initialization.
     */
    public static void discoverProviders() {
        if (initialized) return;

        initialized = true;

        // Register vanilla provider (always available)
        registerProvider(new VanillaStructureProvider());

        // TODO: Add mod detection and registration here
        // Example:
        // if (Loader.isModLoaded("recurrentcomplex")) {
        //     registerProvider(new RecurrentComplexProvider());
        // }

        SimpleStructureScanner.LOGGER.info("Registered {} structure providers", providers.size());
    }

    /**
     * Register a structure provider.
     */
    public static void registerProvider(StructureProvider provider) {
        if (!provider.isAvailable()) {
            SimpleStructureScanner.LOGGER.debug("Skipping unavailable provider: {}", provider.getProviderId());

            return;
        }

        providers.add(provider);

        // Map all structures to their provider
        for (ResourceLocation structureId : provider.getStructureIds()) {
            structureToProvider.put(structureId, provider);
        }

        SimpleStructureScanner.LOGGER.info("Registered structure provider: {} ({} structures)",
            provider.getModName(), provider.getStructureIds().size());
    }

    /**
     * Get all registered providers.
     */
    public static List<StructureProvider> getProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Get the provider for a specific structure.
     */
    @Nullable
    public static StructureProvider getProviderForStructure(ResourceLocation structureId) {
        return structureToProvider.get(structureId);
    }

    /**
     * Get all known structure IDs from all providers.
     */
    public static List<ResourceLocation> getAllStructureIds() {
        List<ResourceLocation> allIds = new ArrayList<>();
        for (StructureProvider provider : providers) allIds.addAll(provider.getStructureIds());

        return allIds;
    }

    /**
     * Get structure info by ID, looking up the appropriate provider.
     */
    @Nullable
    public static StructureInfo getStructureInfo(ResourceLocation structureId) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return null;

        return provider.getStructureInfo(structureId);
    }

    /**
     * Get the mod name for a structure.
     */
    public static String getModNameForStructure(ResourceLocation structureId) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return "gui.structurescanner.unknown";

        return provider.getModName();
    }

    /**
     * Get the display name for a structure.
     */
    @Nullable
    public static String getNameForStructure(ResourceLocation structureId) {
        StructureInfo info = getStructureInfo(structureId);
        if (info == null) return null;

        return info.getDisplayName();
    }

    /**
     * Check if a structure can be searched for.
     */
    public static boolean canBeSearched(ResourceLocation structureId) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return false;

        return provider.canBeSearched(structureId);
    }

    /**
     * Find the nearest structure of a given type.
     */
    @Nullable
    public static StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return null;

        return provider.findNearest(world, structureId, pos, skipCount);
    }

    /**
     * Clear all providers and structure mappings.
     * Primarily for testing.
     */
    public static void clear() {
        providers.clear();
        structureToProvider.clear();
        initialized = false;
    }
}
