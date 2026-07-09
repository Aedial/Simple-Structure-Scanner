package com.simplestructurescanner.structure.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
     * Get a static field value from a class, returning null on failure.
     *
     * @param clazz the class containing the field, or null
     * @param fieldName the name of the field
     * @return the field value, or null if it cannot be read
     */
    @Nullable
    public static Object getStaticFieldOrNull(@Nullable Class<?> clazz, String fieldName) {
        if (clazz == null) return null;

        try {
            return getStaticField(clazz, fieldName);
        } catch (ReflectionException e) {
            return null;
        }
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
     * Get a static boolean field value from a class.
     *
     * @param clazz the class containing the field
     * @param fieldName the name of the field
     * @return the boolean value
     * @throws ReflectionException if the field cannot be accessed
     */
    public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) throws ReflectionException {
        return getBooleanField(null, clazz, fieldName);
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
     * Read a static boolean field value from a class, falling back to a default when
     * the class, field, or conversion is unavailable.
     *
     * @param ownerClass the class containing the field, or null
     * @param fieldName the name of the field, or null
     * @param defaultValue the fallback value to return on failure
     * @return the field value, or {@code defaultValue} if it cannot be read
     */
    public static boolean readStaticBoolean(@Nullable Class<?> ownerClass, @Nullable String fieldName,
            boolean defaultValue) {
        if (ownerClass == null || fieldName == null) return defaultValue;

        try {
            return getStaticBooleanField(ownerClass, fieldName);
        } catch (ReflectionException e) {
            return defaultValue;
        }
    }

    /**
     * Read a static int field value from a class, falling back to a default when
     * the class, field, or conversion is unavailable.
     *
     * @param ownerClass the class containing the field, or null
     * @param fieldName the name of the field, or null
     * @param defaultValue the fallback value to return on failure
     * @return the field value, or {@code defaultValue} if it cannot be read
     */
    public static int readStaticIntField(@Nullable Class<?> ownerClass, @Nullable String fieldName,
            int defaultValue) {
        if (ownerClass == null || fieldName == null) return defaultValue;

        try {
            return getStaticIntField(ownerClass, fieldName);
        } catch (ReflectionException e) {
            return defaultValue;
        }
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
     * Invoke a public instance method with no parameters.
     *
     * @param target the target object
     * @param methodName the method name
     * @return the invoked method result
     * @throws ReflectionException if the method cannot be invoked
     */
    public static Object invokeRequired(Object target, String methodName) throws ReflectionException {
        return invokeRequired(target, methodName, new Class<?>[0]);
    }

    /**
     * Invoke a public instance method using an explicit signature.
     *
     * @param target the target object
     * @param methodName the method name
     * @param parameterTypes the exact parameter signature to resolve
     * @param args the method arguments
     * @return the invoked method result
     * @throws ReflectionException if the method cannot be invoked
     */
    public static Object invokeRequired(Object target, String methodName, Class<?>[] parameterTypes,
            Object... args) throws ReflectionException {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new ReflectionException(
                "Failed to invoke method '" + methodName + "' on " + target.getClass().getName(), e);
        }
    }

    /**
     * Invoke a public static method using an explicit signature.
     *
     * @param ownerClass the class declaring the static method
     * @param methodName the method name
     * @param parameterTypes the exact parameter signature to resolve
     * @param args the method arguments
     * @return the invoked method result
     * @throws ReflectionException if the method cannot be invoked
     */
    public static Object invokeStaticRequired(Class<?> ownerClass, String methodName,
            Class<?>[] parameterTypes, Object... args) throws ReflectionException {
        try {
            Method method = ownerClass.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new ReflectionException(
                "Failed to invoke static method '" + methodName + "' on " + ownerClass.getName(), e);
        }
    }

    /**
     * Invoke an instance method and require a boolean result.
     *
     * @param target the target object
     * @param methodName the method name
     * @param parameterTypes the exact parameter signature to resolve
     * @param args the method arguments
     * @return the boolean result
     * @throws ReflectionException if the method cannot be invoked or does not return a boolean
     */
    public static boolean invokeBooleanRequired(Object target, String methodName,
            Class<?>[] parameterTypes, Object... args) throws ReflectionException {
        Object value = invokeRequired(target, methodName, parameterTypes, args);

        if (value instanceof Boolean) return (Boolean) value;

        throw new ReflectionException("Unexpected boolean reflection payload from '" + methodName + "': " + value);
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
