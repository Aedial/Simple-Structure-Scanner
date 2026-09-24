package com.simplestructurescanner.client.integration;

import java.awt.Color;
import java.lang.reflect.Method;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;


/**
 * Client-side JourneyMap helpers used by the structure scanner GUI.
 */
public final class JourneyMapIntegration {
    public static final String MOD_ID = "journeymap";
    private static final int DEFAULT_Y = 64;

    private static final String UI_MANAGER_CLASS = "journeymap.client.ui.UIManager";

    // Older Waypoint implementation
    private static final String LEGACY_WAYPOINT_CLASS = "journeymap.client.model.Waypoint";
    private static final String LEGACY_WAYPOINT_TYPE_CLASS = "journeymap.client.model.Waypoint$Type";

    // Newer v2 Waypoint implementation
    private static final String V2_WAYPOINT_FACTORY_CLASS =
        "journeymap.api.v2.common.waypoint.WaypointFactory";
    private static final String V2_WAYPOINT_API_CLASS = "journeymap.api.v2.common.waypoint.Waypoint";
    private static final String CLIENT_WAYPOINT_IMPL_CLASS =
        "journeymap.client.waypoint.ClientWaypointImpl";

    private static Boolean journeyMapAvailable = null;

    private JourneyMapIntegration() {
    }

    public static boolean isJourneyMapAvailable() {
        if (journeyMapAvailable == null) journeyMapAvailable = Loader.isModLoaded(MOD_ID);

        return journeyMapAvailable;
    }

    public static boolean openWaypointEditor(String name, BlockPos pos, int dimensionId, int color, boolean yAgnostic) {
        if (!isJourneyMapAvailable() || name == null || name.isEmpty() || pos == null) return false;

        try {
            return openWaypointEditorOptional(name, pos, dimensionId, color, yAgnostic);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to open JourneyMap waypoint editor", e);
            return false;
        }
    }

    @Optional.Method(modid = MOD_ID)
    private static boolean openWaypointEditorOptional(String name, BlockPos pos, int dimensionId, int color,
            boolean yAgnostic) throws ReflectionException {
        BlockPos waypointPos = yAgnostic ? new BlockPos(pos.getX(), DEFAULT_Y, pos.getZ()) : pos;

        // JourneyMap exposes the editor only through its internal UI manager.
        // The "wpedit" command can also be used, but I'd prefer not go through an indirection layer.
        Class<?> legacyWaypointClass = getLegacyWaypointClass();
        if (legacyWaypointClass != null) {
            openLegacyWaypointEditor(legacyWaypointClass, name, waypointPos, dimensionId, color);
        } else {
            openV2WaypointEditor(name, waypointPos, dimensionId, color);
        }

        return true;
    }

    private static Class<?> getLegacyWaypointClass() {
        try {
            return ReflectionHelper.loadClassRequired(LEGACY_WAYPOINT_CLASS);
        } catch (ReflectionException e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void openLegacyWaypointEditor(Class<?> waypointClass, String name, BlockPos waypointPos,
            int dimensionId, int color) throws ReflectionException {
        Class<?> waypointTypeClass = ReflectionHelper.loadClassRequired(LEGACY_WAYPOINT_TYPE_CLASS);
        Class<? extends Enum> waypointType = waypointTypeClass.asSubclass(Enum.class);
        Object normalType = Enum.valueOf(waypointType, "Normal");
        Object waypoint = construct(waypointClass,
            new Class<?>[] { String.class, BlockPos.class, Color.class, waypointTypeClass, Integer.class },
            name, waypointPos, new Color(color & 0xFFFFFF), normalType, dimensionId);

        ReflectionHelper.invokeRequired(waypoint, "setPersistent", new Class<?>[] { boolean.class }, true);
        ReflectionHelper.invokeRequired(waypoint, "setEnable", new Class<?>[] { boolean.class }, true);
        openWaypointEditor(waypoint, waypointClass, 3);
    }

    private static void openV2WaypointEditor(String name, BlockPos waypointPos, int dimensionId, int color)
            throws ReflectionException {
        Class<?> waypointFactoryClass = ReflectionHelper.loadClassRequired(V2_WAYPOINT_FACTORY_CLASS);
        Object waypoint = ReflectionHelper.invokeStaticRequired(waypointFactoryClass, "createWaypoint",
            new Class<?>[] { String.class, BlockPos.class, String.class, String.class, boolean.class },
            MOD_ID, waypointPos, name, String.valueOf(dimensionId), true);

        ReflectionHelper.invokeRequired(waypoint, "setColor", new Class<?>[] { int.class }, color & 0xFFFFFF);
        ReflectionHelper.invokeRequired(waypoint, "setPersistent", new Class<?>[] { boolean.class }, true);
        ReflectionHelper.invokeRequired(waypoint, "setEnabled", new Class<?>[] { boolean.class }, true);

        Class<?> waypointApiClass = ReflectionHelper.loadClassRequired(V2_WAYPOINT_API_CLASS);
        Class<?> clientWaypointClass = ReflectionHelper.loadClassRequired(CLIENT_WAYPOINT_IMPL_CLASS);
        Object clientWaypoint = construct(clientWaypointClass, new Class<?>[] { waypointApiClass }, waypoint);

        openWaypointEditor(clientWaypoint, clientWaypointClass, 2);
    }

    private static Object construct(Class<?> targetClass, Class<?>[] parameterTypes, Object... args)
            throws ReflectionException {
        try {
            return targetClass.getConstructor(parameterTypes).newInstance(args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to construct " + targetClass.getName(), e);
        }
    }

    private static void openWaypointEditor(Object waypoint, Class<?> waypointClass, int parameterCount)
            throws ReflectionException {
        Class<?> uiManagerClass = ReflectionHelper.loadClassRequired(UI_MANAGER_CLASS);
        Object uiManager = ReflectionHelper.getStaticField(uiManagerClass, "INSTANCE");
        Method editor = findWaypointEditor(uiManagerClass, waypointClass, parameterCount);

        try {
            if (parameterCount == 2) {
                editor.invoke(uiManager, waypoint, true);
            } else {
                editor.invoke(uiManager, waypoint, true, null);
            }
        } catch (Exception e) {
            throw new ReflectionException("Failed to open JourneyMap waypoint editor", e);
        }
    }

    private static Method findWaypointEditor(Class<?> uiManagerClass, Class<?> waypointClass, int parameterCount)
            throws ReflectionException {
        for (Method method : uiManagerClass.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!method.getName().equals("openWaypointEditor") || parameterTypes.length != parameterCount) continue;
            if (parameterTypes[0] != waypointClass || parameterTypes[1] != boolean.class) continue;

            return method;
        }

        throw new ReflectionException("JourneyMap waypoint editor signature is unavailable");
    }
}
