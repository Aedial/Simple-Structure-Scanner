package com.simplestructurescanner.client.integration;

import java.util.TreeSet;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap;
import com.mamiyaotaru.voxelmap.interfaces.IVoxelMap;
import com.mamiyaotaru.voxelmap.interfaces.IWaypointManager;
import com.mamiyaotaru.voxelmap.util.Waypoint;


/**
 * Client-side VoxelMap helpers used by the structure scanner GUI.
 */
public final class VoxelMapIntegration {
    public static final String MOD_ID = "voxelmap";
    private static final int DEFAULT_Y = 64;
    private static final int NETHER_DIMENSION_ID = -1;
    private static final int NETHER_COORDINATE_SCALE = 8;

    private static Boolean voxelMapAvailable = null;

    private VoxelMapIntegration() {
    }

    public static boolean isVoxelMapAvailable() {
        if (voxelMapAvailable == null) voxelMapAvailable = Loader.isModLoaded(MOD_ID);

        return voxelMapAvailable;
    }

    public static boolean addWaypoint(String name, BlockPos pos, int dimensionId, int color, boolean yAgnostic) {
        if (!isVoxelMapAvailable() || name == null || name.isEmpty() || pos == null) return false;

        return addWaypointOptional(name, pos, dimensionId, color, yAgnostic);
    }

    @Optional.Method(modid = MOD_ID)
    private static boolean addWaypointOptional(String name, BlockPos pos, int dimensionId, int color, boolean yAgnostic) {
        IVoxelMap voxelMap = AbstractVoxelMap.getInstance();
        if (voxelMap == null) return false;

        IWaypointManager waypointManager = voxelMap.getWaypointManager();
        if (waypointManager == null) return false;

        TreeSet<Integer> dimensions = new TreeSet<>();
        dimensions.add(dimensionId);

        Waypoint waypoint = new Waypoint(
            name,
            getVoxelMapCoordinate(pos.getX(), dimensionId),
            getVoxelMapCoordinate(pos.getZ(), dimensionId),
            yAgnostic ? DEFAULT_Y : pos.getY(),
            true,
            getColorComponent(color, 16),
            getColorComponent(color, 8),
            getColorComponent(color, 0),
            "",
            waypointManager.getCurrentSubworldDescriptor(false),
            dimensions);

        waypointManager.addWaypoint(waypoint);

        return true;
    }

    // VoxelMap stores Nether waypoint X/Z in overworld scale. These map mods, I swear...
    private static int getVoxelMapCoordinate(int coordinate, int dimensionId) {
        if (dimensionId != NETHER_DIMENSION_ID) return coordinate;

        return coordinate * NETHER_COORDINATE_SCALE;
    }

    private static float getColorComponent(int color, int shift) {
        return ((color >> shift) & 0xFF) / 255.0F;
    }
}