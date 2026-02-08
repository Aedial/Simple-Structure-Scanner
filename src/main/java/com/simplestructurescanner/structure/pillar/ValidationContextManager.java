package com.simplestructurescanner.structure.pillar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Manages the lifecycle of structure validation worlds.
 *
 * This class:
 * - Creates and caches validation worlds by dimension
 * - Reuses existing validation worlds to avoid regenerating chunks
 * - Cleans up worlds when done to prevent memory leaks
 * - Provides thread-safe access to validation worlds
 *
 * USAGE:
 * 1. Get a validation world for a dimension: getValidationWorld(dimension)
 * 2. Use the world for structure validation
 * 3. Clear cache when done: clearCache()
 *
 * IMPORTANT: Always call clearCache() when done validating to free memory.
 */
public class ValidationContextManager {

    private static final Map<Integer, StructureValidationWorld> validationWorlds = new HashMap<>();

    // Cache for client world -> server world mapping
    // Uses WeakHashMap to prevent memory leaks when World objects are no longer referenced
    private static final Map<World, World> serverWorldCache = new WeakHashMap<>();

    private static Field chunkGeneratorField;

    static {
        // Use reflection to access the chunkGenerator field from ChunkProviderServer
        try {
            chunkGeneratorField = ChunkProviderServer.class.getDeclaredField("chunkGenerator");
            chunkGeneratorField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Try obfuscated name
            try {
                chunkGeneratorField = ChunkProviderServer.class.getDeclaredField("field_186029_c");
                chunkGeneratorField.setAccessible(true);
            } catch (NoSuchFieldException e2) {
                throw new RuntimeException("Failed to find chunkGenerator field in ChunkProviderServer", e2);
            }
        }
    }

    /**
     * Creates a safe copy of WorldInfo to prevent modifications to the original.
     * <p>
     * This is critical because some mod providers (like BiomesOPlenty with Lost Cities)
     * may modify the WorldInfo's terrain type during initialization. By using a cloned copy,
     * we prevent these modifications from affecting the actual game world.
     * <p>
     * Based on fix from SuperMobTracker: https://github.com/Aedial/SuperMobTracker/commit/6e928f4e
     *
     * @param original The WorldInfo to clone
     * @return A new WorldInfo with the same settings
     */
    private static WorldInfo cloneWorldInfo(WorldInfo original) {
        WorldSettings settings = new WorldSettings(
            original.getSeed(),
            original.getGameType(),
            original.isMapFeaturesEnabled(),
            original.isHardcoreModeEnabled(),
            original.getTerrainType()
        );
        settings.setGeneratorOptions(original.getGeneratorOptions());
        return new WorldInfo(settings, original.getWorldName());
    }

    /**
     * Gets the server world for a given world with caching.
     * This method caches the client world -> server world mapping to avoid
     * repeatedly iterating through server.worlds for every chunk during scans.
     * <p>
     * Uses WeakHashMap to prevent memory leaks when World objects are GC'd.
     *
     * @param world The world to get the server world for
     * @return The server world, or null if not available
     */
    @Nullable
    private static World getServerWorldCached(World world) {
        // Check cache first - this is the fast path for 111,000 chunks
        if (serverWorldCache.containsKey(world)) return serverWorldCache.get(world);

        // Cache miss - do the expensive lookup once and cache the result
        World serverWorld = getServerWorld(world);
        serverWorldCache.put(world, serverWorld);
        return serverWorld;
    }

    /**
     * Gets the chunk generator from a world using reflection.
     *
     * @param world The world to get the chunk generator from
     * @return The chunk generator
     */
    private static IChunkGenerator getChunkGenerator(World world) {
        if (world.getChunkProvider() instanceof ChunkProviderServer) {
            try {
                return (IChunkGenerator) chunkGeneratorField.get(world.getChunkProvider());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access chunkGenerator field", e);
            }
        }
        throw new IllegalArgumentException("World chunk provider is not a ChunkProviderServer");
    }

    /**
     * Gets or creates a validation world for the given real world.
     *
     * This method:
     * - Returns cached validation world if available
     * - Creates new validation world if not cached
     * - Uses the real world's seed, biome provider, world provider, and chunk generator
     * - Handles client-side worlds by getting the server world instead
     *
     * @param realWorld The real world to create a validation context for
     * @return A validation world with the same seed and terrain generation as the real world
     */
    public static StructureValidationWorld getValidationWorld(World realWorld) {
        // If this is a client world, get the server world instead
        // Client worlds don't have ChunkProviderServer
        // Uses cached lookup to avoid repeated iteration through server.worlds
        World world = getServerWorldCached(realWorld);
        if (world == null) world = realWorld;

        int dimension = world.provider.getDimension();

        // Return cached world if available
        if (validationWorlds.containsKey(dimension)) return validationWorlds.get(dimension);

        // Create new validation world
        StructureValidationWorld validationWorld = createValidationWorld(world);
        validationWorlds.put(dimension, validationWorld);

        return validationWorld;
    }

    /**
     * Gets the server world for a given world.
     * If the world is already a server world, returns it as-is.
     * If the world is a client world, returns the corresponding server world.
     *
     * @param world The world to get the server world for
     * @return The server world, or null if not available
     */
    @Nullable
    private static World getServerWorld(World world) {
        // Check if this is already a server world (has ChunkProviderServer)
        if (world.getChunkProvider() instanceof ChunkProviderServer) return world;

        // This is a client world, try to get the server world
        try {
            FMLCommonHandler handler = FMLCommonHandler.instance();
            if (handler != null) {
                MinecraftServer server = handler.getMinecraftServerInstance();
                if (server != null) {
                    int dimension = world.provider.getDimension();
                    // Get the server world for this dimension
                    for (WorldServer serverWorld : server.worlds) {
                        if (serverWorld.provider.getDimension() == dimension) {
                            return serverWorld;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to get server world, return null
        }

        return null;
    }

    /**
     * Creates a new validation world based on the real world.
     *
     * IMPORTANT: We clone the real world's WorldInfo to prevent any modifications
     * from affecting the actual world. Some mod providers (like BiomesOPlenty with Lost Cities)
     * may inadvertently modify the WorldInfo's terrain type during initialization.
     *
     * @param realWorld The real world to copy settings from
     * @return A new validation world with the same generation parameters
     */
    private static StructureValidationWorld createValidationWorld(World realWorld) {
        // Clone the WorldInfo to prevent modifications from affecting the actual world
        // This prevents corruption with mods like BiomesOPlenty + Lost Cities
        WorldInfo worldInfo = cloneWorldInfo(realWorld.getWorldInfo());
        BiomeProvider biomeProvider = realWorld.getBiomeProvider();
        WorldProvider worldProvider = realWorld.provider;
        IChunkGenerator chunkGenerator = getChunkGenerator(realWorld);

        return new StructureValidationWorld(worldInfo, biomeProvider, worldProvider, chunkGenerator);
    }

    /**
     * Checks if a validation world exists for the given dimension.
     *
     * @param dimension The dimension ID
     * @return true if a validation world exists, false otherwise
     */
    public static boolean hasValidationWorld(int dimension) {
        return validationWorlds.containsKey(dimension);
    }

    /**
     * Gets a validation world by dimension ID.
     *
     * @param dimension The dimension ID
     * @return The validation world, or null if not found
     */
    @Nullable
    public static StructureValidationWorld getValidationWorldByDimension(int dimension) {
        return validationWorlds.get(dimension);
    }

    /**
     * Clears the chunk cache for a specific dimension.
     * Use this to free memory after validating structures in a dimension.
     *
     * @param dimension The dimension ID
     */
    public static void clearDimensionCache(int dimension) {
        StructureValidationWorld world = validationWorlds.get(dimension);
        if (world != null) world.clearChunkCache();
    }

    /**
     * Clears all chunk caches across all dimensions.
     * Use this to free memory after validation sessions.
     */
    public static void clearAllChunkCaches() {
        validationWorlds.values().forEach(StructureValidationWorld::clearChunkCache);
    }

    /**
     * Removes and cleans up a validation world for a specific dimension.
     * Use this when completely done with a dimension.
     *
     * @param dimension The dimension ID
     */
    public static void removeValidationWorld(int dimension) {
        StructureValidationWorld world = validationWorlds.remove(dimension);
        if (world != null) world.clearChunkCache();
    }

    /**
     * Removes and cleans up all validation worlds.
     * IMPORTANT: Call this when completely done with structure validation
     * to prevent memory leaks.
     */
    public static void clearCache() {
        // Clear all chunk caches first
        validationWorlds.values().forEach(StructureValidationWorld::clearChunkCache);

        // Remove all worlds
        validationWorlds.clear();
    }

    /**
     * Gets the number of cached validation worlds.
     * Useful for monitoring memory usage.
     *
     * @return The number of validation worlds
     */
    public static int getCachedWorldCount() {
        return validationWorlds.size();
    }

    /**
     * Gets the total number of cached chunks across all validation worlds.
     * Useful for monitoring memory usage.
     *
     * @return The total number of cached chunks
     */
    public static int getTotalCachedChunkCount() {
        return validationWorlds.values().stream()
                .mapToInt(world -> {
                    if (world.getChunkProvider() instanceof ValidationChunkProvider) {
                        return ((ValidationChunkProvider) world.getChunkProvider()).getCachedChunkCount();
                    }
                    return 0;
                })
                .sum();
    }
}
