package com.simplestructurescanner.structure.util;

import java.lang.reflect.Field;
import java.util.List;

import javax.annotation.Nullable;

import com.simplestructurescanner.SimpleStructureScanner;


/**
 * Utility class for common reflection operations used by structure providers
 * to access mod classes without compile-time dependencies.
 */
public final class ReflectionHelper {

    private ReflectionHelper() {
    }

    /**
     * Get a field value from an object using reflection.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the field value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static Object getField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            return field.get(obj);
        } catch (Exception e) {
            throw new ReflectionException("Failed to get field '" + fieldName + "' from " + clazz.getName(), e);
        }
    }

    /**
     * Get a field value from a class (static field) using reflection.
     *
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the field value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static Object getStaticField(Class<?> clazz, String fieldName) throws ReflectionException {
        return getField(null, clazz, fieldName);
    }

    /**
     * Get a String field value from an object.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the String value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static String getStringField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        return (String) getField(obj, clazz, fieldName);
    }

    /**
     * Get an int field value from an object.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the int value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static int getIntField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        Object value = getField(obj, clazz, fieldName);

        if (value instanceof Number) return ((Number) value).intValue();

        throw new ReflectionException("Field '" + fieldName + "' is not a number: " + value);
    }

    /**
     * Get a float field value from an object.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the float value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static float getFloatField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        Object value = getField(obj, clazz, fieldName);

        if (value instanceof Number) return ((Number) value).floatValue();

        throw new ReflectionException("Field '" + fieldName + "' is not a number: " + value);
    }

    /**
     * Get a boolean field value from an object.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the boolean value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static boolean getBooleanField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        Object value = getField(obj, clazz, fieldName);

        if (value instanceof Boolean) return (Boolean) value;

        throw new ReflectionException("Field '" + fieldName + "' is not a boolean: " + value);
    }

    /**
     * Get a static int field value from a class.
     *
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the int value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static int getStaticIntField(Class<?> clazz, String fieldName) throws ReflectionException {
        return getIntField(null, clazz, fieldName);
    }

    /**
     * Get a static float field value from a class.
     *
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the float value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static float getStaticFloatField(Class<?> clazz, String fieldName) throws ReflectionException {
        return getFloatField(null, clazz, fieldName);
    }

    /**
     * Get a List field value from an object.
     *
     * @param obj the object to get the field from
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @param <T> the type of list elements
     * @return the List value, or null if the field is null
     * @throws ReflectionException if the field cannot be accessed
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> List<T> getListField(Object obj, Class<?> clazz, String fieldName) throws ReflectionException {
        return (List<T>) getField(obj, clazz, fieldName);
    }

    /**
     * Safely load a class by name without throwing an exception.
     *
     * @param className the fully qualified class name
     * @return the class, or null if not found
     */
    @Nullable
    public static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            SimpleStructureScanner.LOGGER.debug("Class not found: {}", className);
            return null;
        }
    }

    /**
     * Load a class by name, throwing an exception if not found.
     *
     * @param className the fully qualified class name
     * @return the class
     * @throws ReflectionException if the class cannot be found
     */
    public static Class<?> loadClassRequired(String className) throws ReflectionException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new ReflectionException("Class not found: " + className, e);
        }
    }

    /**
     * Exception thrown when reflection operations fail.
     */
    public static class ReflectionException extends Exception {

        public ReflectionException(String message) {
            super(message);
        }

        public ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
