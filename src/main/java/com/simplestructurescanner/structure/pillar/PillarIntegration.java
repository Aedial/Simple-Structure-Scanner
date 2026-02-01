package com.simplestructurescanner.structure.pillar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simplestructurescanner.SimpleStructureScanner;

/**
 * Integration helper for accessing Pillar data via reflection.
 * <p>
 * This class provides safe access to Pillar's internals without requiring
 * Pillar as a compile-time dependency. All access is done through reflection.
 */
public class PillarIntegration {

    private static boolean pillarLoaded = false;
    private static boolean checkedPillar = false;
    private static Map<String, PillarSchemaProxy> schemas = null;
    private static List<PillarSchemaProxy> schemasInOrder = null;  // Captures Pillar's natural iteration order
    private static Float rarityMultiplier = null;
    private static Integer maxStructuresInOneChunk = null;

    /**
     * Check if Pillar is loaded.
     */
    public static boolean isPillarLoaded() {
        if (!checkedPillar) {
            checkPillar();
        }
        return pillarLoaded;
    }

    /**
     * Check if Pillar mod is loaded and cache the result.
     */
    private static void checkPillar() {
        checkedPillar = true;
        try {
            Class.forName("vazkii.pillar.Pillar");
            pillarLoaded = true;
            SimpleStructureScanner.LOGGER.info("Pillar mod detected - integration enabled");
        } catch (ClassNotFoundException e) {
            pillarLoaded = false;
            SimpleStructureScanner.LOGGER.info("Pillar mod not found - Pillar structures will not be available");
        }
    }

    /**
     * Get all Pillar structure schemas.
     * <p>
     * Returns cached data if available, otherwise loads via reflection.
     * <p>
     * <b>IMPORTANT:</b> This method captures Pillar's natural HashMap iteration order
     * when first called. This order is preserved for all subsequent calls.
     * The captured order should match what Pillar sees internally, assuming
     * SSS loads schemas at a similar time to Pillar's internal access.
     *
     * @return Map of structure name to schema proxy, or null if Pillar not loaded
     */
    public static Map<String, PillarSchemaProxy> getSchemas() {
        if (!isPillarLoaded()) {
            return null;
        }

        if (schemas != null) {
            return schemas;
        }

        try {
            // Access StructureLoader.loadedSchemas via reflection
            Class<?> structureLoaderClass = Class.forName("vazkii.pillar.StructureLoader");
            Field loadedSchemasField = structureLoaderClass.getDeclaredField("loadedSchemas");
            loadedSchemasField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Object> pillarSchemas = (Map<String, Object>) loadedSchemasField.get(null);

            if (pillarSchemas == null) {
                SimpleStructureScanner.LOGGER.warn("Pillar loadedSchemas is null");
                return null;
            }

            schemas = new HashMap<>();
            schemasInOrder = new ArrayList<>();

            // OPTION 10: Early Schema Order Capture
            // Capture Pillar's NATURAL HashMap iteration order (not sorted!)
            // This is the key to matching Pillar's behavior without modifying Pillar.
            // We iterate pillarSchemas.values() directly to get the exact order
            // that HashMap's internal iteration produces at this moment.

            SimpleStructureScanner.LOGGER.info("OPTION 10: Capturing Pillar's natural schema iteration order");
            StringBuilder capturedOrder = new StringBuilder("Captured Pillar schema order (").append(pillarSchemas.size()).append("): ");

            int index = 0;
            for (Object schemaObj : pillarSchemas.values()) {
                try {
                    PillarSchemaProxy proxy = createProxy(schemaObj);
                    if (proxy != null) {
                        schemas.put(proxy.structureName, proxy);
                        schemasInOrder.add(proxy);

                        if (index > 0) capturedOrder.append(", ");
                        capturedOrder.append(proxy.structureName);
                        index++;
                    }
                } catch (Exception e) {
                    SimpleStructureScanner.LOGGER.error("Failed to create proxy during iteration", e);
                }
            }

            SimpleStructureScanner.LOGGER.info("{}", capturedOrder.toString());
            SimpleStructureScanner.LOGGER.info("TOTAL: Loaded {} Pillar structure schemas", schemas.size());
            SimpleStructureScanner.LOGGER.info("OPTION 10: Schema order captured and stored - will use this order for all predictions");

            return schemas;

        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to access Pillar schemas", e);
            return null;
        }
    }

    /**
     * Get all Pillar structure schemas in their captured iteration order.
     * <p>
     * This returns the schemas in the exact order that Pillar's HashMap
     * iteration produced when SSS first loaded them. This captured order
     * should match what Pillar sees internally.
     * <p>
     * <b>IMPORTANT:</b> This is the key to Option 10 - Early Schema Order Capture.
     * By capturing the order once and reusing it, we avoid the non-determinism
     * of HashMap iteration across multiple accesses.
     *
     * @return List of schemas in captured order, or null if Pillar not loaded
     */
    @Nullable
    public static List<PillarSchemaProxy> getSchemasInOrder() {
        // Trigger schema loading if not already done
        if (!isPillarLoaded()) {
            return null;
        }

        // This will populate schemasInOrder if it hasn't been populated yet
        if (schemas == null) {
            getSchemas();
        }

        return schemasInOrder;
    }

    /**
     * Create a PillarSchemaProxy from a Pillar StructureSchema object using reflection.
     */
    private static PillarSchemaProxy createProxy(Object pillarSchema) throws Exception {
        Class<?> schemaClass = pillarSchema.getClass();

        // Extract all fields we need
        String structureName = getStringField(pillarSchema, schemaClass, "structureName");
        Object generatorTypeObj = getField(pillarSchema, schemaClass, "generatorType");
        int maxY = getIntField(pillarSchema, schemaClass, "maxY");
        int minY = getIntField(pillarSchema, schemaClass, "minY");
        float rarity = getFloatField(pillarSchema, schemaClass, "rarity");
        int minDistance = getIntField(pillarSchema, schemaClass, "minDistanceToSameTypeStructures");
        boolean generateEverywhere = getBooleanField(pillarSchema, schemaClass, "generateEverywhere");

        @SuppressWarnings("unchecked")
        List<Integer> dimensionSpawns = (List<Integer>) getField(pillarSchema, schemaClass, "dimensionSpawns");

        @SuppressWarnings("unchecked")
        List<String> biomeNameSpawns = (List<String>) getField(pillarSchema, schemaClass, "biomeNameSpawns");

        @SuppressWarnings("unchecked")
        List<String> biomeTagSpawns = (List<String>) getField(pillarSchema, schemaClass, "biomeTagSpawns");

        boolean isDimensionBlacklist = getBooleanField(pillarSchema, schemaClass, "isDimensionSpawnsBlacklist");
        boolean isBiomeNameBlacklist = getBooleanField(pillarSchema, schemaClass, "isBiomeNameSpawnsBlacklist");
        boolean isBiomeTagBlacklist = getBooleanField(pillarSchema, schemaClass, "isBiomeTagSpawnsBlacklist");

        // Convert GeneratorType enum to our proxy enum
        PillarGeneratorType generatorType = convertGeneratorType(generatorTypeObj);

        return new PillarSchemaProxy(
                structureName,
                generatorType,
                maxY,
                minY,
                rarity,
                minDistance,
                dimensionSpawns,
                biomeNameSpawns,
                biomeTagSpawns,
                isDimensionBlacklist,
                isBiomeNameBlacklist,
                isBiomeTagBlacklist,
                generateEverywhere);
    }

    /**
     * Convert Pillar's GeneratorType enum to our proxy enum.
     */
    private static PillarGeneratorType convertGeneratorType(Object pillarGeneratorType) {
        if (pillarGeneratorType == null) {
            return PillarGeneratorType.NONE;
        }

        String name = pillarGeneratorType.toString();
        try {
            return PillarGeneratorType.valueOf(name);
        } catch (IllegalArgumentException e) {
            SimpleStructureScanner.LOGGER.warn("Unknown Pillar GeneratorType: {}", name);
            return PillarGeneratorType.NONE;
        }
    }

    /**
     * Get Pillar's rarity multiplier config value.
     */
    public static float getRarityMultiplier() {
        if (rarityMultiplier != null) {
            return rarityMultiplier;
        }

        if (!isPillarLoaded()) {
            return 1.0f;
        }

        try {
            Class<?> pillarClass = Class.forName("vazkii.pillar.Pillar");
            Field field = pillarClass.getField("rarityMultiplier");
            rarityMultiplier = field.getFloat(null);
            return rarityMultiplier;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get Pillar rarity multiplier", e);
            return 1.0f;
        }
    }

    /**
     * Get Pillar's max structures per chunk config value.
     */
    public static int getMaxStructuresInOneChunk() {
        if (maxStructuresInOneChunk != null) {
            return maxStructuresInOneChunk;
        }

        if (!isPillarLoaded()) {
            return 1;
        }

        try {
            Class<?> pillarClass = Class.forName("vazkii.pillar.Pillar");
            Field field = pillarClass.getField("maxStructuresInOneChunk");
            maxStructuresInOneChunk = field.getInt(null);
            return maxStructuresInOneChunk;
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.error("Failed to get Pillar maxStructuresInOneChunk", e);
            return 1;
        }
    }

    // Reflection helper methods

    private static Object getField(Object obj, Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static String getStringField(Object obj, Class<?> clazz, String fieldName) throws Exception {
        return (String) getField(obj, clazz, fieldName);
    }

    private static int getIntField(Object obj, Class<?> clazz, String fieldName) throws Exception {
        return ((Number) getField(obj, clazz, fieldName)).intValue();
    }

    private static float getFloatField(Object obj, Class<?> clazz, String fieldName) throws Exception {
        return ((Number) getField(obj, clazz, fieldName)).floatValue();
    }

    private static boolean getBooleanField(Object obj, Class<?> clazz, String fieldName) throws Exception {
        return (Boolean) getField(obj, clazz, fieldName);
    }
}
