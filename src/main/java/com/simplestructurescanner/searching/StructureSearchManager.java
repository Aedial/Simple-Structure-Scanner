package com.simplestructurescanner.searching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.network.NetworkHandler;
import com.simplestructurescanner.network.PacketRequestStructureSearch;
import com.simplestructurescanner.structure.StructureLocation;
import com.simplestructurescanner.structure.StructureProviderRegistry;
import com.simplestructurescanner.util.WorldUtils;

// FIXME: all dimensions consider all structures together - nether fortress in overworld, etc.

/**
 * Manages searched structures for live search feature.
 * Structures can be added to searching list via double-click in the GUI.
 *
 * Caching strategy:
 * - locationCache: Raw structure positions indexed by (worldId, structureId).
 *   These positions are deterministic and don't change for a given world.
 *   Populated via batch read if provider supports it, otherwise via individual reads.
 * - sortedCache: Positions sorted by distance from player's position at time of refresh.
 *   Only updated on manual refresh or world join, NOT automatically based on player movement.
 * - When cycling with arrows, we just change skipOffset and use sortedCache.
 * - For providers that don't support batch reads, we cache individual results as they come in.
 */
public class StructureSearchManager {
    private static final Set<ResourceLocation> searchedStructures = new LinkedHashSet<>();
    private static final Map<ResourceLocation, StructureLocation> lastKnownLocations = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> structureColors = new LinkedHashMap<>();
    private static final Set<Integer> usedColorIndices = new LinkedHashSet<>();
    private static final Map<ResourceLocation, Integer> skipOffsets = new LinkedHashMap<>();

    // Cache for raw structure positions by world id
    // Map<WorldId, Map<StructureId, List<BlockPos>>>
    private static final Map<Long, Map<ResourceLocation, List<BlockPos>>> locationCache = new LinkedHashMap<>();

    // Cache for sorted positions (sorted by player position at time of refresh)
    // Map<StructureId, List<BlockPos>>
    private static final Map<ResourceLocation, List<BlockPos>> sortedCache = new LinkedHashMap<>();

    // Track structures that don't support batch reads (use individual caching instead)
    private static final Set<ResourceLocation> nonBatchStructures = new LinkedHashSet<>();

    // Pending search requests
    private static final Set<ResourceLocation> pendingSearches = new LinkedHashSet<>();

    private static final int MAX_CACHE_RESULTS = 100;

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
        if (structureColors.containsKey(id)) return;

        // Find the first available color index
        int colorIndex = 0;
        while (usedColorIndices.contains(colorIndex)) colorIndex++;
        usedColorIndices.add(colorIndex);
        structureColorIndices.put(id, colorIndex);
        structureColors.put(id, COLORS[colorIndex % COLORS.length]);
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
            sortedCache.remove(id);
            freeColor(id);
        } else {
            searchedStructures.add(id);
            assignColor(id);
            skipOffsets.put(id, 0);
            pendingSearches.add(id);
        }

        saveToConfig();
    }

    public static void startTracking(ResourceLocation id) {
        if (searchedStructures.contains(id)) return;

        searchedStructures.add(id);
        assignColor(id);
        skipOffsets.put(id, 0);
        pendingSearches.add(id);
        saveToConfig();
    }

    public static void stopTracking(ResourceLocation id) {
        if (!searchedStructures.contains(id)) return;

        searchedStructures.remove(id);
        lastKnownLocations.remove(id);
        skipOffsets.remove(id);
        pendingSearches.remove(id);
        sortedCache.remove(id);
        freeColor(id);
        saveToConfig();
    }

    /**
     * Requests a search for the given structure.
     * Uses cached data if available, otherwise fetches from world.
     */
    public static void requestSearch(ResourceLocation id) {
        if (searchedStructures.contains(id)) pendingSearches.add(id);
    }

    /**
     * Refreshes the search for a structure.
     * Clears sorted cache to force re-sort based on current player position.
     */
    public static void refreshSearch(ResourceLocation id) {
        skipOffsets.put(id, 0);
        lastKnownLocations.remove(id);
        sortedCache.remove(id);  // Force re-sort on next search
        requestSearch(id);
    }

    /**
     * Forces a full cache refresh (invalidates location cache and fetches fresh data).
     */
    public static void forceRefresh(ResourceLocation id) {
        skipOffsets.put(id, 0);
        lastKnownLocations.remove(id);
        sortedCache.remove(id);
        nonBatchStructures.remove(id);  // Re-check if batch is supported

        // Clear from location cache
        long worldId = WorldUtils.getWorldIdentifier();
        Map<ResourceLocation, List<BlockPos>> worldCache = locationCache.get(worldId);
        if (worldCache != null) {
            worldCache.remove(id);
        }

        requestSearch(id);
    }

    /**
     * Skips the current result and shows the next structure.
     * Uses cached data if available.
     */
    public static void skipCurrent(ResourceLocation id) {
        List<BlockPos> sorted = sortedCache.get(id);
        int currentOffset = skipOffsets.getOrDefault(id, 0);

        if (sorted != null) {
            // Have batch cache, just increment offset
            if (currentOffset < sorted.size() - 1) {
                skipOffsets.put(id, currentOffset + 1);
                updateLocationFromSortedCache(id);
            }
        } else if (!nonBatchStructures.contains(id)) {
            // No cache yet, trigger a search
            skipOffsets.put(id, currentOffset + 1);
            pendingSearches.add(id);
        } else {
            // Non-batch structure, need server request for new skip count
            skipOffsets.put(id, currentOffset + 1);
            pendingSearches.add(id);
        }
    }

    /**
     * Goes back to the previous result.
     * Uses cached data if available.
     */
    public static void previousResult(ResourceLocation id) {
        int currentOffset = skipOffsets.getOrDefault(id, 0);
        if (currentOffset <= 0) return;

        skipOffsets.put(id, currentOffset - 1);

        List<BlockPos> sorted = sortedCache.get(id);
        if (sorted != null) {
            // Have batch cache, just update display
            updateLocationFromSortedCache(id);
        } else {
            // Need server request
            pendingSearches.add(id);
        }
    }

    /**
     * Blacklists the current location for the given structure, then searches for next.
     * Returns true if a location was blacklisted.
     */
    public static boolean blacklistCurrentLocation(ResourceLocation id, long worldId) {
        StructureLocation location = lastKnownLocations.get(id);
        if (location == null) return false;

        BlockPos pos = location.getPosition();
        ModConfig.addBlacklistedLocation(worldId, id.toString(),
            pos.getX(), pos.getY(), pos.getZ(), location.isYAgnostic());

        // Remove from sorted cache
        List<BlockPos> sorted = sortedCache.get(id);
        if (sorted != null) {
            sorted.removeIf(p -> p.getX() == pos.getX() && p.getZ() == pos.getZ());
        }

        // Remove from location cache
        Map<ResourceLocation, List<BlockPos>> worldCache = locationCache.get(worldId);
        if (worldCache != null) {
            List<BlockPos> cached = worldCache.get(id);
            if (cached != null) cached.removeIf(p -> p.getX() == pos.getX() && p.getZ() == pos.getZ());
        }

        // Remove from display and update from cache or request new
        lastKnownLocations.remove(id);

        if (sorted != null && !sorted.isEmpty()) {
            updateLocationFromSortedCache(id);
        } else {
            pendingSearches.add(id);
        }

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

        long worldId = WorldUtils.getWorldIdentifier();
        int skipOffset = skipOffsets.getOrDefault(id, 0);

        // Check if we have a sorted cache we can use
        List<BlockPos> sorted = sortedCache.get(id);
        if (sorted != null && skipOffset < sorted.size()) {
            updateLocationFromSortedCache(id);
            return;
        }

        // Check if we have a location cache that needs sorting
        Map<ResourceLocation, List<BlockPos>> worldCache = locationCache.get(worldId);
        if (worldCache != null && worldCache.containsKey(id)) {
            // Sort and use
            updateSortedCache(id, playerPos, worldId);
            updateLocationFromSortedCache(id);
            return;
        }

        // No cache available, need to fetch from world
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) {
            World serverWorld = mc.getIntegratedServer().getWorld(world.provider.getDimension());
            if (serverWorld != null) {
                processSingleplayerSearch(serverWorld, id, playerPos, skipOffset, worldId);
                return;
            }
        }

        // Multiplayer: send request to server
        NetworkHandler.INSTANCE.sendToServer(new PacketRequestStructureSearch(id, playerPos, skipOffset));
    }

    /**
     * Processes a search in singleplayer mode.
     */
    private static void processSingleplayerSearch(World serverWorld, ResourceLocation id,
            BlockPos playerPos, int skipOffset, long worldId) {

        // Try batch search first
        List<BlockPos> positions = StructureProviderRegistry.findAllNearby(
            serverWorld, id, playerPos, MAX_CACHE_RESULTS
        );

        if (positions != null) {
            // Batch supported, cache and sort
            positions.removeIf(pos ->
                ModConfig.isLocationBlacklisted(worldId, id.toString(), pos.getX(), pos.getY(), pos.getZ())
            );

            locationCache.computeIfAbsent(worldId, k -> new LinkedHashMap<>()).put(id, positions);
            updateSortedCache(id, playerPos, worldId);
            updateLocationFromSortedCache(id, serverWorld, worldId);
        } else {
            // Batch not supported, use individual read
            nonBatchStructures.add(id);

            StructureLocation location = StructureProviderRegistry.findNearest(
                serverWorld, id, playerPos, skipOffset,
                pos -> !ModConfig.isLocationBlacklisted(worldId, id.toString(), pos.getX(), pos.getY(), pos.getZ())
            );

            // Cache the individual result
            if (location != null) addToLocationCache(worldId, id, location.getPosition());

            updateLocation(id, location);
        }
    }

    /**
     * Adds a position to the location cache (for non-batch providers).
     */
    private static void addToLocationCache(long worldId, ResourceLocation id, BlockPos pos) {
        Map<ResourceLocation, List<BlockPos>> worldCache =
            locationCache.computeIfAbsent(worldId, k -> new LinkedHashMap<>());
        List<BlockPos> positions = worldCache.computeIfAbsent(id, k -> new ArrayList<>());

        // Avoid duplicates
        for (BlockPos existing : positions) {
            if (existing.getX() == pos.getX() && existing.getZ() == pos.getZ()) return;
        }

        positions.add(pos);
    }

    /**
     * Updates the sorted cache for a structure based on player position.
     */
    private static void updateSortedCache(ResourceLocation id, BlockPos playerPos, long worldId) {
        Map<ResourceLocation, List<BlockPos>> worldCache = locationCache.get(worldId);
        if (worldCache == null) return;

        List<BlockPos> rawPositions = worldCache.get(id);
        if (rawPositions == null) return;

        // Create a sorted copy
        List<BlockPos> sorted = new ArrayList<>(rawPositions);
        final int px = playerPos.getX();
        final int pz = playerPos.getZ();

        sorted.sort((a, b) -> {
            // Cast to long to avoid integer overflow for large distances
            long dxA = a.getX() - px;
            long dzA = a.getZ() - pz;
            long dxB = b.getX() - px;
            long dzB = b.getZ() - pz;
            long distA = dxA * dxA + dzA * dzA;
            long distB = dxB * dxB + dzB * dzB;
            return Long.compare(distA, distB);
        });

        sortedCache.put(id, sorted);
    }

    /**
     * Updates the display location from the sorted cache.
     */
    private static void updateLocationFromSortedCache(ResourceLocation id) {
        updateLocationFromSortedCache(id, null, WorldUtils.getWorldIdentifier());
    }

    /**
     * Updates the display location from the sorted cache, with optional server world for height calculation.
     */
    private static void updateLocationFromSortedCache(ResourceLocation id, World serverWorld, long worldId) {
        List<BlockPos> sorted = sortedCache.get(id);
        if (sorted == null || sorted.isEmpty()) {
            lastKnownLocations.remove(id);
            return;
        }

        int skipOffset = skipOffsets.getOrDefault(id, 0);

        // Clamp skip offset to valid range
        if (skipOffset >= sorted.size()) {
            skipOffset = sorted.size() - 1;
            skipOffsets.put(id, skipOffset);
        }

        BlockPos targetPos = sorted.get(skipOffset);

        // Calculate terrain height for surface structures if we have server world access
        if (serverWorld != null && targetPos.getY() == 0) {
            com.simplestructurescanner.structure.TerrainHeightCalculator heightCalc =
                new com.simplestructurescanner.structure.TerrainHeightCalculator(
                    worldId, serverWorld.getBiomeProvider()
                );
            int terrainY = heightCalc.getTerrainHeight(targetPos.getX(), targetPos.getZ());
            targetPos = new BlockPos(targetPos.getX(), terrainY, targetPos.getZ());
        }

        boolean yAgnostic = targetPos.getY() == 0;
        StructureLocation location = new StructureLocation(targetPos, skipOffset, sorted.size(), yAgnostic);
        lastKnownLocations.put(id, location);
    }

    /**
     * Called when server sends batch results (provider supports batch reads).
     */
    public static void handleBatchResponse(ResourceLocation id, List<BlockPos> positions, BlockPos playerPos) {
        long worldId = WorldUtils.getWorldIdentifier();

        // Filter out blacklisted positions
        positions.removeIf(pos ->
            ModConfig.isLocationBlacklisted(worldId, id.toString(), pos.getX(), pos.getY(), pos.getZ())
        );

        // Store in location cache
        locationCache.computeIfAbsent(worldId, k -> new LinkedHashMap<>()).put(id, positions);

        // Sort and update display
        updateSortedCache(id, playerPos, worldId);
        updateLocationFromSortedCache(id);
    }

    /**
     * Called when server sends single result (provider doesn't support batch reads).
     */
    public static void handleSingleResponse(ResourceLocation id, StructureLocation location, int skipCount) {
        nonBatchStructures.add(id);

        if (location != null) {
            long worldId = WorldUtils.getWorldIdentifier();
            addToLocationCache(worldId, id, location.getPosition());
        }

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
        pendingSearches.clear();
        sortedCache.clear();
        nonBatchStructures.clear();
        saveToConfig();
    }

    /**
     * Clears all caches. Called when changing worlds.
     */
    public static void clearCaches() {
        sortedCache.clear();
        lastKnownLocations.clear();
        nonBatchStructures.clear();

        // Re-queue searches for all tracked structures
        for (ResourceLocation id : searchedStructures) {
            skipOffsets.put(id, 0);
            pendingSearches.add(id);
        }
    }

    /**
     * Clears the location cache for a specific world.
     */
    public static void clearWorldCache(long worldId) {
        locationCache.remove(worldId);
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
