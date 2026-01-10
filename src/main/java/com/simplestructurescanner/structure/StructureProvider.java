package com.simplestructurescanner.structure;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * Interface for structure providers.
 * Each mod integration implements this to provide structure data.
 *
 * <p><b>IMPORTANT:</b> If you modify this interface or related classes
 * ({@link StructureInfo}, {@link StructureLocation}, {@link DimensionInfo}),
 * update the documentation at {@code docs/STRUCTURE_PROVIDER_GUIDE.md}.</p>
 */
public interface StructureProvider {

    /**
     * Get the unique ID of this provider.
     * Used to identify which provider a structure comes from.
     */
    String getProviderId();

    /**
     * Get the display name of the mod this provider is for.
     */
    String getModName();

    /**
     * Check if this provider is available (mod is loaded).
     * You can use Loader.isModLoaded for this.
     */
    boolean isAvailable();

    /**
     * Called after provider registration. Use this to set up any necessary structure data.
     * Class initialization should contain no bindings to other mods, as they may not be loaded.
     */
    void postInit();

    /**
     * Get a list of all structure IDs this provider knows about.
     */
    List<ResourceLocation> getStructureIds();

    /**
     * Check if a structure can be searched for.
     * @param structureId The structure ID
     */
    boolean canBeSearched(ResourceLocation structureId);

    /**
     * Get information about a specific structure.
     * @param structureId The structure ID
     * @return Structure info, or null if not found
     */
    @Nullable
    StructureInfo getStructureInfo(ResourceLocation structureId);

    /**
     * Find the nearest structure of the given type. This code runs on the server side.
     * @param world The world to search in
     * @param structureId The structure ID to find
     * @param pos The position to search from
     * @param skipCount Number of structures to skip (for "next" functionality)
     * @return The location of the structure, or null if not found
     */
    @Nullable
    default StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount) {
        return findNearest(world, structureId, pos, skipCount, null);
    }

    /**
     * Find the nearest structure of the given type, with optional location filter. This code runs on the server side.
     * Providers should implement this method to optimize filtering internally (e.g., caching positions).
     * @param world The world to search in
     * @param structureId The structure ID to find
     * @param pos The position to search from
     * @param skipCount Number of structures to skip (for "next" functionality)
     * @param locationFilter Optional filter to exclude certain positions (e.g., blacklisted locations). May be null.
     * @return The location of the structure, or null if not found
     */
    @Nullable
    StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount,
            @Nullable Predicate<BlockPos> locationFilter);

    /**
     * Find all nearby structures of the given type within search range. This code runs on the server side.
     * Results are not sorted - caller should sort by distance if needed.
     * @param world The world to search in
     * @param structureId The structure ID to find
     * @param pos The position to search from
     * @param maxResults Maximum number of results to return
     * @return List of structure positions, null if batch search not supported, or empty list if none found
     */
    @Nullable
    default List<BlockPos> findAllNearby(World world, ResourceLocation structureId, BlockPos pos, int maxResults) {
        return null;  // null = batch search not supported, use findNearest instead
    }
}
