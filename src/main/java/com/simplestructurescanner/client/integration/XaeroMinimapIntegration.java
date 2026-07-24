package com.simplestructurescanner.client.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.util.ReflectionHelper;
import com.simplestructurescanner.structure.util.ReflectionHelper.ReflectionException;


/**
 * Client-side Xaero's Minimap helpers used by the structure scanner GUI.
 * Xaero is completely locked-down and does not make things any easy to integrate with,
 * so this integration is a complete mess of reflection and guesswork.
 * It may break at any update, so please report any issues you encounter.
 */
public final class XaeroMinimapIntegration {
    public static final String MOD_ID = "xaerominimap";
    private static final int DEFAULT_Y = 64;

    @Nullable
    private static Boolean xaeroMinimapAvailable = null;

    private XaeroMinimapIntegration() {
    }

    public static boolean isXaeroMinimapAvailable() {
        if (xaeroMinimapAvailable == null) xaeroMinimapAvailable = Loader.isModLoaded(MOD_ID);

        return xaeroMinimapAvailable;
    }

    public static boolean addWaypoint(String name, BlockPos pos, int color, boolean yAgnostic) {
        if (!isXaeroMinimapAvailable() || name == null || name.isEmpty() || pos == null) return false;

        try {
            return addWaypointOptional(name, pos, color, yAgnostic);
        } catch (ReflectionException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to create Xaero waypoint", e);
            return false;
        }
    }

    private static boolean addWaypointOptional(String name, BlockPos pos, int color, boolean yAgnostic)
            throws ReflectionException {
        Class<?> sessionClass = ReflectionHelper.loadClassRequired("xaero.common.XaeroMinimapSession");
        Object session = invokeStaticRequired(sessionClass, "getCurrentSession", new Class<?>[0]);
        if (session == null) return false;

        Object waypointsManager = invokeRequired(session, "getWaypointsManager");
        if (waypointsManager == null) return false;

        Object currentWorld = invokeRequired(waypointsManager, "getCurrentWorld");
        if (currentWorld == null) return false;

        Object waypointSet = invokeRequired(waypointsManager, "getWaypoints");
        if (waypointSet == null) return false;

        List<Object> waypointList = getWaypointList(waypointSet);
        waypointList.add(createWaypoint(name, pos, color, yAgnostic));

        Object waypointSession = invokeRequired(waypointsManager, "getWaypointSession");
        invokeRequired(waypointSession, "setSetChangedTime", new Class<?>[] { long.class }, System.currentTimeMillis());

        Object worldManagerIO = invokeRequired(waypointsManager, "getWorldManagerIO");
        Class<?> minimapWorldClass = ReflectionHelper.loadClassRequired("xaero.hud.minimap.world.MinimapWorld");
        invokeRequired(worldManagerIO, "saveWorld", new Class<?>[] { minimapWorldClass }, currentWorld);

        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getWaypointList(Object waypointSet) throws ReflectionException {
        Object value = invokeRequired(waypointSet, "getList");
        if (value instanceof List) return (List<Object>) value;

        throw new ReflectionException("Unexpected Xaero waypoint list payload: " + value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object createWaypoint(String name, BlockPos pos, int color, boolean yAgnostic)
            throws ReflectionException {
        int waypointY = yAgnostic ? DEFAULT_Y : pos.getY();

        Class<?> waypointClass = ReflectionHelper.loadClassRequired("xaero.common.minimap.waypoints.Waypoint");
        Class<?> waypointColorClass = ReflectionHelper.loadClassRequired("xaero.hud.minimap.waypoint.WaypointColor");
        Class<?> waypointPurposeClass = ReflectionHelper.loadClassRequired("xaero.hud.minimap.waypoint.WaypointPurpose");

        Object waypointColor = resolveClosestColor(waypointColorClass, color);
        Object normalPurpose = Enum.valueOf((Class<Enum>) waypointPurposeClass.asSubclass(Enum.class), "NORMAL");

        try {
            Constructor<?> constructor = waypointClass.getConstructor(
                int.class,
                int.class,
                int.class,
                String.class,
                String.class,
                waypointColorClass,
                waypointPurposeClass);

            Object waypoint = constructor.newInstance(
                pos.getX(),
                waypointY,
                pos.getZ(),
                name,
                createInitials(name),
                waypointColor,
                normalPurpose);

            invokeRequired(waypoint, "setYIncluded", new Class<?>[] { boolean.class }, !yAgnostic);

            return waypoint;
        } catch (Exception e) {
            throw new ReflectionException("Failed to construct Xaero waypoint", e);
        }
    }

    private static Object resolveClosestColor(Class<?> waypointColorClass, int color) throws ReflectionException {
        Object[] colors = waypointColorClass.getEnumConstants();
        if (colors == null || colors.length == 0) {
            throw new ReflectionException("Xaero waypoint colors are unavailable");
        }

        int targetColor = color & 0xFFFFFF;
        Object closestColor = colors[0];
        int bestDistance = Integer.MAX_VALUE;

        for (Object waypointColor : colors) {
            Object value = invokeRequired(waypointColor, "getHex");
            if (!(value instanceof Number)) {
                throw new ReflectionException("Unexpected Xaero waypoint color payload: " + value);
            }

            int candidateColor = ((Number) value).intValue() & 0xFFFFFF;
            int distance = colorDistanceSquared(targetColor, candidateColor);
            if (distance < bestDistance) {
                closestColor = waypointColor;
                bestDistance = distance;
            }
        }

        return closestColor;
    }

    private static int colorDistanceSquared(int firstColor, int secondColor) {
        int firstRed = (firstColor >> 16) & 0xFF;
        int firstGreen = (firstColor >> 8) & 0xFF;
        int firstBlue = firstColor & 0xFF;
        int secondRed = (secondColor >> 16) & 0xFF;
        int secondGreen = (secondColor >> 8) & 0xFF;
        int secondBlue = secondColor & 0xFF;
        int redDifference = firstRed - secondRed;
        int greenDifference = firstGreen - secondGreen;
        int blueDifference = firstBlue - secondBlue;

        return redDifference * redDifference + greenDifference * greenDifference + blueDifference * blueDifference;
    }

    private static String createInitials(String name) {
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) return "S";

        StringBuilder initials = new StringBuilder(2);
        boolean takeNextCharacter = true;

        for (int i = 0; i < trimmedName.length() && initials.length() < 2; i++) {
            char character = trimmedName.charAt(i);
            if (Character.isWhitespace(character) || character == '_' || character == '-') {
                takeNextCharacter = true;
                continue;
            }

            if (!Character.isLetterOrDigit(character) || !takeNextCharacter) continue;

            initials.append(Character.toUpperCase(character));
            takeNextCharacter = false;
        }

        if (initials.length() == 0) initials.append(Character.toUpperCase(trimmedName.charAt(0)));

        return initials.toString();
    }

    private static Object invokeStaticRequired(Class<?> ownerClass, String methodName, Class<?>[] parameterTypes,
            Object... args) throws ReflectionException {
        try {
            Method method = ownerClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke static method '" + methodName + "' on " + ownerClass.getName(), e);
        }
    }

    private static Object invokeRequired(Object target, String methodName) throws ReflectionException {
        return invokeRequired(target, methodName, new Class<?>[0]);
    }

    private static Object invokeRequired(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectionException {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new ReflectionException("Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
        }
    }
}