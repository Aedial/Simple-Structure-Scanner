package com.simplestructurescanner.structure.pillar;

import net.minecraft.server.MinecraftServer;
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
 * - Resolves the correct generation world before validation
 *
 * USAGE:
 * 1. Get a validation world for a world: getValidationWorld(world)
 * 2. Use the world for structure validation
 * 3. Clear cache when done: clearCache()
 *
 * IMPORTANT: Always call clearCache() when done validating to free memory.
 */
public class ValidationContextManager {

    private static final Map<Integer, StructureValidationWorld> VALIDATION_WORLDS = new HashMap<>();

    // Cache for client world -> server world mapping
    // Uses WeakHashMap to prevent memory leaks when World objects are no longer referenced
    private static final Map<World, World> SERVER_WORLD_CACHE = new WeakHashMap<>();

    private static final Field CHUNK_GENERATOR_FIELD = findChunkGeneratorField();

    private ValidationContextManager() {
    }

    private static Field findChunkGeneratorField() {
        String[] fieldNames = {"chunkGenerator", "field_186029_c"};

        // The obfuscated fallback preserves compatibility with production jars.
        for (String fieldName : fieldNames) {
            try {
                Field field = ChunkProviderServer.class.getDeclaredField(fieldName);
                field.setAccessible(true);

                return field;
            } catch (NoSuchFieldException e) {
                // Try the next known field name.
            }
        }

        throw new RuntimeException("Failed to find chunkGenerator field in ChunkProviderServer");
    }

    /**
     * Creates a safe copy of WorldInfo to prevent modifications to the original.
     * <p>
     * This is critical because some mod providers (like BiomesOPlenty with Lost Cities)
     * may modify the WorldInfo's terrain type during initialization. By using a cloned copy,
     * we prevent these modifications from affecting the actual game world.
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
        if (SERVER_WORLD_CACHE.containsKey(world)) return SERVER_WORLD_CACHE.get(world);

        // Cache miss - do the expensive lookup once and cache the result
        World serverWorld = getServerWorld(world);
        SERVER_WORLD_CACHE.put(world, serverWorld);
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
                return (IChunkGenerator) CHUNK_GENERATOR_FIELD.get(world.getChunkProvider());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access chunkGenerator field", e);
            }
        }
        throw new IllegalArgumentException("World chunk provider is not a ChunkProviderServer");
    }

    /**
     * Resolves the actual world used for terrain generation.
     * <p>
     * Pillar receives the server-side dimension world during chunk generation.
     * Searches started from the client world can report the wrong seed in mods
     * that override it per dimension, so we resolve the matching server world
     * when it is available and otherwise fall back to the provided world.
     *
     * @param realWorld The world passed to the search code
     * @return The server generation world, or the original world if unavailable
     */
    public static World getGenerationWorld(World realWorld) {
        World world = getServerWorldCached(realWorld);

        return world != null ? world : realWorld;
    }

    /**
     * Gets the chunk generator used by the resolved generation world.
     * Returns null when the generator is not accessible from the current side.
     *
     * @param realWorld The world passed to the search code
     * @return The resolved generation chunk generator, or null if unavailable
     */
    @Nullable
    public static IChunkGenerator getGenerationChunkGenerator(World realWorld) {
        World world = getGenerationWorld(realWorld);
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return null;

        return getChunkGenerator(world);
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
        World world = getGenerationWorld(realWorld);
        int dimension = world.provider.getDimension();
        StructureValidationWorld validationWorld = VALIDATION_WORLDS.get(dimension);
        if (validationWorld != null) return validationWorld;

        validationWorld = createValidationWorld(world);
        VALIDATION_WORLDS.put(dimension, validationWorld);

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
                if (server != null) return findServerWorld(server, world.provider.getDimension());
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

    @Nullable
    private static World findServerWorld(MinecraftServer server, int dimension) {
        for (WorldServer serverWorld : server.worlds) {
            if (serverWorld.provider.getDimension() == dimension) return serverWorld;
        }

        return null;
    }

    /**
     * Clears the chunk cache for a specific dimension.
     * Use this to free memory after validating structures in a dimension.
     *
     * @param dimension The dimension ID
     */
    public static void clearDimensionCache(int dimension) {
        clearChunkCache(VALIDATION_WORLDS.get(dimension));
    }

    /**
     * Removes and cleans up all validation worlds.
     * IMPORTANT: Call this when completely done with structure validation
     * to prevent memory leaks.
     */
    public static void clearCache() {
        VALIDATION_WORLDS.values().forEach(ValidationContextManager::clearChunkCache);
        VALIDATION_WORLDS.clear();
    }

    private static void clearChunkCache(@Nullable StructureValidationWorld world) {
        if (world != null) world.clearChunkCache();
    }

    private static int getCachedChunkCountForWorld(StructureValidationWorld world) {
        if (world.getChunkProvider() instanceof ValidationChunkProvider) {
            return ((ValidationChunkProvider) world.getChunkProvider()).getCachedChunkCount();
        }

        return 0;
    }

    /**
     * Gets the total number of cached chunks across all validation worlds.
     * Useful for monitoring memory usage.
     *
     * @return The total number of cached chunks
     */
    public static int getTotalCachedChunkCount() {
        return VALIDATION_WORLDS.values().stream()
                .mapToInt(ValidationContextManager::getCachedChunkCountForWorld)
                .sum();
    }
}
