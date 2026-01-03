package com.simplestructurescanner.structure;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * Interface for structure providers.
 * Each mod integration implements this to provide structure data.
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
    StructureLocation findNearest(World world, ResourceLocation structureId, BlockPos pos, int skipCount);

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
