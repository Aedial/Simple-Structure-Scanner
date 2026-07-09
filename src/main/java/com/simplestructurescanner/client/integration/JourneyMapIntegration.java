package com.simplestructurescanner.client.integration;

import java.awt.Color;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import journeymap.client.model.Waypoint;
import journeymap.client.ui.UIManager;


/**
 * Client-side JourneyMap helpers used by the structure scanner GUI.
 */
public final class JourneyMapIntegration {
    public static final String MOD_ID = "journeymap";
    private static final int DEFAULT_Y = 64;

    private static Boolean journeyMapAvailable = null;

    private JourneyMapIntegration() {
    }

    public static boolean isJourneyMapAvailable() {
        if (journeyMapAvailable == null) journeyMapAvailable = Loader.isModLoaded(MOD_ID);

        return journeyMapAvailable;
    }

    public static boolean openWaypointEditor(String name, BlockPos pos, int dimensionId, int color, boolean yAgnostic) {
        if (!isJourneyMapAvailable() || name == null || name.isEmpty() || pos == null) return false;

        return openWaypointEditorOptional(name, pos, dimensionId, color, yAgnostic);
    }

    @Optional.Method(modid = MOD_ID)
    private static boolean openWaypointEditorOptional(String name, BlockPos pos, int dimensionId, int color, boolean yAgnostic) {
        BlockPos waypointPos = yAgnostic ? new BlockPos(pos.getX(), DEFAULT_Y, pos.getZ()) : pos;

        // JourneyMap's public API can create waypoints, but the native editor is only exposed through the internal UI manager.
        // The "wpedit" command can also be used, but it'd prefer not going through an indirection layer.
        Waypoint waypoint = new Waypoint(name, waypointPos, new Color(color & 0xFFFFFF), Waypoint.Type.Normal, dimensionId)
            .setPersistent(true)
            .setEnable(true);

        UIManager.INSTANCE.openWaypointEditor(waypoint, true, null);

        return true;
    }
}