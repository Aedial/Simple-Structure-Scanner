package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.abyssalcraft.AbyssalCraftStructureProvider;
import com.simplestructurescanner.structure.aether.AetherStructureProvider;
import com.simplestructurescanner.structure.external.ExternalStructureProviderLoader;
import com.simplestructurescanner.structure.iceandfire.IceAndFireStructureProvider;
import com.simplestructurescanner.structure.pillar.PillarStructureProvider;
import com.simplestructurescanner.structure.recurrentcomplex.RecurrentComplexStructureProvider;
import com.simplestructurescanner.structure.vanilla.VanillaStructureProvider;


/**
 * Registry for structure providers.
 * Manages multiple providers from different mods.
 */
public class StructureProviderRegistry {
    private static final List<StructureProvider> providers = new ArrayList<>();
    private static final Map<ResourceLocation, StructureProvider> structureToProvider = new LinkedHashMap<>();
    private static boolean initialized = false;

    private static final List<Class<? extends StructureProvider>> providerClasses = Arrays.asList(
        VanillaStructureProvider.class,
        AbyssalCraftStructureProvider.class,
        AetherStructureProvider.class,
        IceAndFireStructureProvider.class,
        PillarStructureProvider.class,
        RecurrentComplexStructureProvider.class
        // <b>IMPORTANT, DO NOT REMOVE:</b> Add other provider classes here
                                                                                                 );

    /**
     * Discover and register all available structure providers.
     * Called during mod initialization.
     */
    public static void discoverProviders() {
        if (initialized) return;

        StructureSearchOverrides.load();

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

        for (StructureProvider provider : ExternalStructureProviderLoader.loadProviders()) {
            if (!provider.isAvailable()) {
                SimpleStructureScanner.LOGGER.debug("Skipping unavailable external provider: {}", provider.getProviderId());
                continue;
            }

            registerProvider(provider);
        }

        initialized = true;
        SimpleStructureScanner.LOGGER.info("Registered {} structure providers", providers.size());
    }

    public static void reloadProviders() {
        clear();
        discoverProviders();
    }

    public static void reloadProvider(String providerId) {
        if (!initialized) {
            discoverProviders();
            return;
        }
        if (providerId == null || providerId.isEmpty()) return;

        removeProviderMappings(providerId);

        StructureProvider provider = findProvider(providerId);
        if (provider == null) return;

        indexProviderStructures(provider);
    }

    /**
     * Register a structure provider.
     */
    public static void registerProvider(StructureProvider provider) {
        provider.postInit();  // Allow provider to set up structure data
        providers.add(provider);
        int visibleStructures = indexProviderStructures(provider);

        SimpleStructureScanner.LOGGER.info("Registered structure provider: {} ({} structures)",
            provider.getModName(), visibleStructures);
    }

    private static int indexProviderStructures(StructureProvider provider) {
        int visibleStructures = 0;

        // Map all structures to their provider
        for (ResourceLocation structureId : provider.getStructureIds()) {
            if (StructureSearchOverrides.isStructureHidden(provider.getProviderId(), structureId)) continue;

            structureToProvider.put(structureId, provider);
            visibleStructures++;
        }

        return visibleStructures;
    }

    private static void removeProviderMappings(String providerId) {
        structureToProvider.entrySet().removeIf(entry -> providerId.equals(entry.getValue().getProviderId()));
    }

    @Nullable
    private static StructureProvider findProvider(String providerId) {
        for (StructureProvider provider : providers) {
            if (providerId.equals(provider.getProviderId())) return provider;
        }

        return null;
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
        return new ArrayList<>(structureToProvider.keySet());
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
    public static LocalizedText getNameForStructure(ResourceLocation structureId) {
        StructureInfo info = getStructureInfo(structureId);
        if (info == null) return null;

        return info.getDisplayName();
    }

    /**
     * Check if a structure can be searched for.
     */
    public static boolean canBeSearched(ResourceLocation structureId) {
        return canBeSearched(structureId, null);
    }

    /**
     * Check if a structure can be searched for in the current dimension context.
     */
    public static boolean canBeSearched(ResourceLocation structureId, @Nullable Integer dimensionId) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return false;

        return StructureSearchOverrides.canStructureBeSearched(
            provider.getProviderId(), structureId, dimensionId, provider.canBeSearched(structureId));
    }

    public static boolean isStructureHiddenInDimension(ResourceLocation structureId, int dimensionId) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return false;

        return StructureSearchOverrides.isStructureHiddenInDimension(
            provider.getProviderId(), structureId, dimensionId);
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
     * Delegates filtering to the provider for efficient handling.
     */
    @Nullable
    public static StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter) {
        StructureProvider provider = getProviderForStructure(structureId);
        if (provider == null) return null;
        if (world != null && !canBeSearched(structureId, world.provider.getDimension())) return null;

        return provider.findNearest(world, structureId, pos, skipCount, locationFilter);
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
        if (world != null && !canBeSearched(structureId, world.provider.getDimension())) {
            return Collections.emptyList();
        }

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
