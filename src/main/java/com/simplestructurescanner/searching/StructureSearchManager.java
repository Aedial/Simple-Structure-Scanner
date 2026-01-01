package com.simplestructurescanner.searching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.util.WorldUtils;


/**
 * Manages searched structures for live search feature.
 * Structures can be added to searching list via double-click in the GUI.
 */
public class StructureSearchManager {
    private static final Set<ResourceLocation> searchedStructures = new LinkedHashSet<>();
    private static final Map<ResourceLocation, StructureLocation> lastKnownLocations = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> structureColors = new LinkedHashMap<>();
    private static final Set<Integer> usedColorIndices = new LinkedHashSet<>();
    private static final Set<ResourceLocation> pendingSearches = new LinkedHashSet<>();
    private static final Map<ResourceLocation, Integer> skipOffsets = new LinkedHashMap<>();

    // Predefined colors for searched structures (cycling)
    private static final int[] COLORS = {
        0xFF5555,  // Red
        0x55FF55,  // Green
        0x5555FF,  // Blue
        0xFFFF55,  // Yellow
        0xFF55FF,  // Magenta
        0x55FFFF,  // Cyan
        0xFFAA55,  // Orange
        0xAA55FF,  // Purple
        0x55FFAA,  // Mint
        0xFFAAAA   // Pink
    };
    private static final Map<ResourceLocation, Integer> structureColorIndices = new LinkedHashMap<>();

    static {
        loadFromConfig();
    }

    private static void loadFromConfig() {
        searchedStructures.clear();
        for (String id : ModConfig.getClientTrackedIds()) {
            ResourceLocation loc = new ResourceLocation(id);
            searchedStructures.add(loc);
            assignColor(loc);
            skipOffsets.put(loc, 0);
            pendingSearches.add(loc);  // Queue search on load
        }
    }

    private static void saveToConfig() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation loc : searchedStructures) ids.add(loc.toString());
        ModConfig.setClientTrackedIds(ids);
    }

    private static void assignColor(ResourceLocation id) {
        if (!structureColors.containsKey(id)) {
            // Find the first available color index
            int colorIndex = 0;
            while (usedColorIndices.contains(colorIndex)) colorIndex++;
            usedColorIndices.add(colorIndex);
            structureColorIndices.put(id, colorIndex);
            structureColors.put(id, COLORS[colorIndex % COLORS.length]);
        }
    }

    private static void freeColor(ResourceLocation id) {
        Integer colorIndex = structureColorIndices.remove(id);
        if (colorIndex != null) usedColorIndices.remove(colorIndex);
        structureColors.remove(id);
    }

    public static boolean isTracked(ResourceLocation id) {
        return searchedStructures.contains(id);
    }

    public static void toggleTracking(ResourceLocation id) {
        if (searchedStructures.contains(id)) {
            searchedStructures.remove(id);
            lastKnownLocations.remove(id);
            skipOffsets.remove(id);
            pendingSearches.remove(id);
            freeColor(id);
        } else {
            searchedStructures.add(id);
            assignColor(id);
            skipOffsets.put(id, 0);
            requestSearch(id);
        }
        saveToConfig();
    }

    public static void startTracking(ResourceLocation id) {
        if (!searchedStructures.contains(id)) {
            searchedStructures.add(id);
            assignColor(id);
            skipOffsets.put(id, 0);
            requestSearch(id);
            saveToConfig();
        }
    }

    public static void stopTracking(ResourceLocation id) {
        if (searchedStructures.contains(id)) {
            searchedStructures.remove(id);
            lastKnownLocations.remove(id);
            skipOffsets.remove(id);
            pendingSearches.remove(id);
            freeColor(id);
            saveToConfig();
        }
    }

    /**
     * Requests a search for the given structure. The search will be processed
     * on the next client tick.
     */
    public static void requestSearch(ResourceLocation id) {
        if (searchedStructures.contains(id)) pendingSearches.add(id);
    }

    /**
     * Refreshes the search for a structure (resets skip offset and searches again).
     */
    public static void refreshSearch(ResourceLocation id) {
        skipOffsets.put(id, 0);
        lastKnownLocations.remove(id);
        requestSearch(id);
    }

    /**
     * Skips the current result and finds the next structure.
     */
    public static void skipCurrent(ResourceLocation id) {
        int currentOffset = skipOffsets.getOrDefault(id, 0);
        skipOffsets.put(id, currentOffset + 1);
        requestSearch(id);
    }

    /**
     * Goes back to the previous result.
     */
    public static void previousResult(ResourceLocation id) {
        int currentOffset = skipOffsets.getOrDefault(id, 0);
        if (currentOffset > 0) {
            skipOffsets.put(id, currentOffset - 1);
            requestSearch(id);
        }
    }

    /**
     * Blacklists the current location for the given structure, then searches for next.
     * Returns true if a location was blacklisted.
     */
    public static boolean blacklistCurrentLocation(ResourceLocation id, long worldSeed) {
        StructureLocation location = lastKnownLocations.get(id);
        if (location == null) return false;

        BlockPos pos = location.getPosition();
        ModConfig.addBlacklistedLocation(worldSeed, id.toString(),
            pos.getX(), pos.getY(), pos.getZ(), location.isYAgnostic());

        // Remove from cache and search again
        lastKnownLocations.remove(id);
        requestSearch(id);

        return true;
    }

    /**
     * Processes any pending search requests. Called from client tick.
     */
    public static void processPendingSearches(World world, BlockPos playerPos) {
        if (pendingSearches.isEmpty()) return;

        // Process only one search per tick to avoid lag
        ResourceLocation id = pendingSearches.iterator().next();
        pendingSearches.remove(id);

        if (!ModConfig.isStructureAllowed(id.toString())) return;
        if (!StructureProviderRegistry.canBeSearched(id)) return;

        int skipOffset = skipOffsets.getOrDefault(id, 0);
        long worldId = WorldUtils.getWorldIdentifier();
        StructureLocation location = StructureProviderRegistry.findNearest(world, id, playerPos, skipOffset,
            (pos) -> !ModConfig.isLocationBlacklisted(worldId, id.toString(), pos.getX(), pos.getY(), pos.getZ()));
        updateLocation(id, location);
    }

    public static int getSkipOffset(ResourceLocation id) {
        return skipOffsets.getOrDefault(id, 0);
    }

    public static Set<ResourceLocation> getTrackedIds() {
        return new LinkedHashSet<>(searchedStructures);
    }

    public static int getColor(ResourceLocation id) {
        return structureColors.getOrDefault(id, 0xFFFFFF);
    }

    public static void updateLocation(ResourceLocation id, StructureLocation location) {
        if (location != null) {
            lastKnownLocations.put(id, location);
        } else {
            lastKnownLocations.remove(id);
        }
    }

    public static StructureLocation getLastKnownLocation(ResourceLocation id) {
        return lastKnownLocations.get(id);
    }

    public static Map<ResourceLocation, StructureLocation> getAllLocations() {
        return new LinkedHashMap<>(lastKnownLocations);
    }

    public static void clearAll() {
        searchedStructures.clear();
        lastKnownLocations.clear();
        structureColors.clear();
        structureColorIndices.clear();
        usedColorIndices.clear();
        saveToConfig();
    }

    /**
     * Get formatted distance string.
     */
    public static String formatDistance(double distance) {
        if (distance >= 1000) {
            return String.format("%.1f%s", distance / 1000, I18n.format("gui.structurescanner.km"));
        }

        return String.format("%.0f%s", distance, I18n.format("gui.structurescanner.m"));
    }

    /**
     * Get direction from player to structure.
     */
    public static float getYawToLocation(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
    }
}
