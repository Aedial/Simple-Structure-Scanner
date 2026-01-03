package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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

    private static List<Class<? extends StructureProvider>> providerClasses = Arrays.asList(
        VanillaStructureProvider.class
        // Add other provider classes here
    );

    /**
     * Discover and register all available structure providers.
     * Called during mod initialization.
     */
    public static void discoverProviders() {
        if (initialized) return;

        for (Class<? extends StructureProvider> providerClass : providerClasses) {
            try {
                StructureProvider provider = providerClass.getDeclaredConstructor().newInstance();
                if (!provider.isAvailable()) {
                    SimpleStructureScanner.LOGGER.debug("Skipping unavailable provider: {}", provider.getProviderId());
                    continue;
                }

                registerProvider(provider);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.error("Failed to instantiate structure provider: {}", providerClass.getName(), e);
            }
        }

        initialized = true;
        SimpleStructureScanner.LOGGER.info("Registered {} structure providers", providers.size());
    }

    /**
     * Register a structure provider.
     */
    public static void registerProvider(StructureProvider provider) {
        provider.postInit();  // Allow provider to set up structure data
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
        return findNearest(world, structureId, pos, skipCount, null);
    }

    /**
     * Find the nearest structure of a given type, with optional location filter.
     */
    @Nullable
    public static StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return null;

        // If no filter, use the simple path
        if (locationFilter == null) return provider.findNearest(world, structureId, pos, skipCount);

        // With filter, we need to search with increasing skip counts until we find enough valid locations
        int additionalSkips = 0;
        int validFound = 0;
        int maxAttempts = skipCount + 50;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            StructureLocation location = provider.findNearest(world, structureId, pos, attempt);
            if (location == null) break;

            BlockPos locationPos = location.getPosition();
            if (locationFilter.test(locationPos)) {
                if (validFound == skipCount) return location;

                validFound++;
            }
        }

        return null;
    }

    /**
     * Find all nearby structures of a given type.
     * Results are not sorted - caller should sort by distance if needed.
     * @return List of positions, null if batch search not supported, or empty list if none found
     */
    @Nullable
    public static List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return null;

        return provider.findAllNearby(world, structureId, pos, maxResults);
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
