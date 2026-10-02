package com.simplestructurescanner.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.SimpleStructureScanner;


/**
 * Reflective wrapper for Spark and Flare, allowing profiling without direct dependency on the mod itself.
 * We cannot use Spark's API, as it doesn't expose the necessary hooks for starting and stopping profiles.
 */
public final class SparkWrapper {
    private static final String SPARK_MOD_ID = "spark";
     private static final String FLARE_MOD_ID = "flare";
    private static final String SPARK_PROVIDER_CLASS = "me.lucko.spark.api.SparkProvider";
    private static final String SPARK_PLATFORM_CLASS = "me.lucko.spark.common.SparkPlatform";
    private static final String SAMPLER_CLASS = "me.lucko.spark.common.sampler.Sampler";
    private static final String SAMPLER_BUILDER_CLASS = "me.lucko.spark.common.sampler.SamplerBuilder";
    private static final String THREAD_DUMPER_CLASS = "me.lucko.spark.common.sampler.ThreadDumper";
    private static final String THREAD_DUMPER_SPECIFIC_CLASS = "me.lucko.spark.common.sampler.ThreadDumper$Specific";
    private static final String EXPORT_PROPS_CLASS = "me.lucko.spark.common.sampler.Sampler$ExportProps";
    private static final String COMMAND_SENDER_DATA_CLASS = "me.lucko.spark.common.command.sender.CommandSender$Data";
    private static final String MERGE_STRATEGY_CLASS = "me.lucko.spark.common.sampler.java.MergeStrategy";
    private static final String CLASS_SOURCE_LOOKUP_CLASS = "me.lucko.spark.common.sampler.source.ClassSourceLookup";
    private static final String FLARE_API_CLASS = "com.cleanroommc.flare.api.FlareAPI";
    private static final String FLARE_SAMPLER_CLASS = "com.cleanroommc.flare.api.sampler.Sampler";
    private static final String FLARE_ABSTRACT_SAMPLER_CLASS = "com.cleanroommc.flare.common.sampler.AbstractSampler";
    private static final String FLARE_SAMPLER_BUILDER_CLASS = "com.cleanroommc.flare.api.sampler.SamplerBuilder";
    private static final String FLARE_THREAD_DUMPER_CLASS = "com.cleanroommc.flare.api.sampler.thread.ThreadDumper";
    private static final String FLARE_THREAD_DUMPER_SPECIFIC_CLASS = "com.cleanroommc.flare.api.sampler.thread.ThreadDumper$Specific";
    private static final String FLARE_EXPORT_PROPS_CLASS = "com.cleanroommc.flare.common.sampler.ExportProps";

    private static final AtomicBoolean warnedMissingSpark = new AtomicBoolean();
    private static final AtomicBoolean warnedStartFailure = new AtomicBoolean();
    private static final AtomicLong profileCount = new AtomicLong();

    private final Object platform;
    private final Object sampler;
    private final boolean flare;
    private final String profileName;
    private final String fileName;
    private final long startNanos;
    private boolean stopped;

    private SparkWrapper(Object platform, Object sampler, boolean flare, String profileName) {
        this.platform = platform;
        this.sampler = sampler;
        this.flare = flare;
        this.profileName = profileName;
        this.fileName = toFileName(profileName) + "-" + profileCount.incrementAndGet();
        this.startNanos = System.nanoTime();
    }

    public static SparkWrapper start(String profileName, int samplingIntervalMillis) {
        if (profileName == null || profileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name is required");
        }

        if (samplingIntervalMillis <= 0) {
            throw new IllegalArgumentException("Sampling interval must be positive");
        }

        boolean sparkLoaded = Loader.isModLoaded(SPARK_MOD_ID);
        boolean flareLoaded = Loader.isModLoaded(FLARE_MOD_ID);
        if (!sparkLoaded && !flareLoaded) {
            if (warnedMissingSpark.compareAndSet(false, true)) {
                SimpleStructureScanner.LOGGER.warn(
                    "Skipped Spark profile {} because Spark/Flare is not loaded", profileName);
            }

            return null;
        }

        try {
            Object platform = sparkLoaded ? getPlatform() : getFlare();
            Object sampler = sparkLoaded ? createSampler(platform, samplingIntervalMillis)
                : createFlareSampler(platform, samplingIntervalMillis);
            return new SparkWrapper(platform, sampler, !sparkLoaded, profileName);
        } catch (Exception | LinkageError e) {
            if (warnedStartFailure.compareAndSet(false, true)) {
                SimpleStructureScanner.LOGGER.warn("Failed to start Spark profile {}", profileName, unwrap(e));
            }

            return null;
        }
    }

    public synchronized void stop() {
        if (stopped) return;

        stopped = true;
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        try {
            Class<?> samplerClass = Class.forName(flare ? FLARE_SAMPLER_CLASS : SAMPLER_CLASS);
            samplerClass.getMethod("stop", boolean.class).invoke(sampler, false);
            scheduleExport(elapsedMillis);
        } catch (Exception | LinkageError e) {
            SimpleStructureScanner.LOGGER.warn("Failed to stop Spark profile {}", profileName, unwrap(e));
        }
    }

    private static Object getPlatform() throws Exception {
        Class<?> providerClass = Class.forName(SPARK_PROVIDER_CLASS);
        Object api = providerClass.getMethod("get").invoke(null);
        Field platformField = api.getClass().getDeclaredField("platform");
        platformField.setAccessible(true);
        return platformField.get(api);
    }

    private static Object getFlare() throws Exception {
        Class<?> flareClass = Class.forName(FLARE_API_CLASS);
        return flareClass.getMethod("getInstance").invoke(null);
    }

    private static Object createSampler(Object platform, int samplingIntervalMillis) throws Exception {
        Class<?> platformClass = Class.forName(SPARK_PLATFORM_CLASS);
        Class<?> builderClass = Class.forName(SAMPLER_BUILDER_CLASS);
        Class<?> threadDumperClass = Class.forName(THREAD_DUMPER_CLASS);
        Class<?> specificThreadDumperClass = Class.forName(THREAD_DUMPER_SPECIFIC_CLASS);
        Object builder = builderClass.getConstructor().newInstance();
        Object threadDumper = specificThreadDumperClass.getConstructor(Thread.class)
            .newInstance(Thread.currentThread());

        builderClass.getMethod("samplingInterval", double.class).invoke(builder, (double) samplingIntervalMillis);
        builderClass.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
        builderClass.getMethod("threadDumper", threadDumperClass).invoke(builder, threadDumper);

        return builderClass.getMethod("start", platformClass).invoke(builder, platform);
    }

    private static Object createFlareSampler(Object flare, int samplingIntervalMillis) throws Exception {
        Class<?> builderClass = Class.forName(FLARE_SAMPLER_BUILDER_CLASS);
        Class<?> samplerClass = Class.forName(FLARE_SAMPLER_CLASS);
        Class<?> threadDumperClass = Class.forName(FLARE_THREAD_DUMPER_CLASS);
        Class<?> specificThreadDumperClass = Class.forName(FLARE_THREAD_DUMPER_SPECIFIC_CLASS);
        Object builder = flare.getClass().getMethod("samplerBuilder").invoke(flare);
        Object threadDumper = specificThreadDumperClass.getConstructor(Thread.class)
            .newInstance(Thread.currentThread());

        builderClass.getMethod("interval", double.class).invoke(builder, (double) samplingIntervalMillis);
        builderClass.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
        builderClass.getMethod("threadDumper", threadDumperClass).invoke(builder, threadDumper);

        Object sampler = builderClass.getMethod("build").invoke(builder);
        samplerClass.getMethod("start").invoke(sampler);
        return sampler;
    }

    private void scheduleExport(long elapsedMillis) throws Exception {
        Runnable export = () -> exportProfile(elapsedMillis);

        try {
            if (flare) {
                platform.getClass().getMethod("runAsync", Runnable.class).invoke(platform, export);
            } else {
                Object plugin = platform.getClass().getMethod("getPlugin").invoke(platform);
                plugin.getClass().getMethod("executeAsync", Runnable.class).invoke(plugin, export);
            }
        } catch (Exception e) {
            export.run();
        }
    }

    private void exportProfile(long elapsedMillis) {
        try {
            if (flare) {
                exportFlareProfile(elapsedMillis);
                return;
            }

            Class<?> platformClass = Class.forName(SPARK_PLATFORM_CLASS);
            Class<?> samplerClass = Class.forName(SAMPLER_CLASS);
            Class<?> exportPropsClass = Class.forName(EXPORT_PROPS_CLASS);
            Object exportProps = createExportProps(platformClass, exportPropsClass);
            Object profileData = samplerClass.getMethod("toProto", platformClass, exportPropsClass)
                .invoke(sampler, platform, exportProps);
            byte[] bytes = (byte[]) profileData.getClass().getMethod("toByteArray").invoke(profileData);
            Path file = (Path) platformClass.getMethod("resolveSaveFile", String.class, String.class)
                .invoke(platform, fileName, "sparkprofile");

            Files.write(file, bytes);
            SimpleStructureScanner.LOGGER.info("Saved Spark profile '{}' ({} ms) to {}",
                profileName, elapsedMillis, file);
        } catch (Exception | LinkageError e) {
            SimpleStructureScanner.LOGGER.warn("Failed to save Spark profile {}", profileName, unwrap(e));
        }
    }

    private void exportFlareProfile(long elapsedMillis) throws Exception {
        Class<?> flareClass = Class.forName(FLARE_API_CLASS);
        Class<?> samplerClass = Class.forName(FLARE_ABSTRACT_SAMPLER_CLASS);
        Class<?> exportPropsClass = Class.forName(FLARE_EXPORT_PROPS_CLASS);
        Object exportProps = createFlareExportProps(flareClass, exportPropsClass);
        Object profileData = samplerClass.getMethod("toProto", flareClass, exportPropsClass, boolean.class)
            .invoke(sampler, platform, exportProps, true);
        byte[] bytes = (byte[]) profileData.getClass().getMethod("toByteArray").invoke(profileData);
        Path file = ((Path) flareClass.getMethod("saveDirectory").invoke(platform))
            .resolve("profiler").resolve(fileName + ".sparkprofile");

        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        SimpleStructureScanner.LOGGER.info("Saved Spark profile '{}' ({} ms) to {}",
            profileName, elapsedMillis, file);
    }

    private Object createExportProps(Class<?> platformClass, Class<?> exportPropsClass) throws Exception {
        Class<?> senderDataClass = Class.forName(COMMAND_SENDER_DATA_CLASS);
        Class<?> mergeStrategyClass = Class.forName(MERGE_STRATEGY_CLASS);
        Class<?> classSourceLookupClass = Class.forName(CLASS_SOURCE_LOOKUP_CLASS);
        Object exportProps = exportPropsClass.getConstructor().newInstance();
        Object senderData = senderDataClass.getConstructor(String.class, UUID.class)
            .newInstance("Simple Structure Scanner", null);
        Object mergeStrategy = getEnumConstant(mergeStrategyClass, "SAME_METHOD");
        Method createClassSourceLookup = classSourceLookupClass.getMethod("create", platformClass);
        Supplier<Object> classSourceLookup = () -> invokeClassSourceLookup(createClassSourceLookup);

        exportPropsClass.getMethod("creator", senderDataClass).invoke(exportProps, senderData);
        exportPropsClass.getMethod("mergeStrategy", mergeStrategyClass).invoke(exportProps, mergeStrategy);
        exportPropsClass.getMethod("classSourceLookup", Supplier.class).invoke(exportProps, classSourceLookup);

        return exportProps;
    }

    private Object createFlareExportProps(Class<?> flareClass, Class<?> exportPropsClass) throws Exception {
        Object exportProps = exportPropsClass.getConstructor().newInstance();
        exportPropsClass.getMethod("setDefault", flareClass, exportPropsClass).invoke(null, platform, exportProps);
        return exportProps;
    }

    private Object invokeClassSourceLookup(Method createClassSourceLookup) {
        try {
            return createClassSourceLookup.invoke(null, platform);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Spark class source lookup", e);
        }
    }

    private static Object getEnumConstant(Class<?> enumClass, String name) {
        for (Object value : enumClass.getEnumConstants()) {
            if (value instanceof Enum && ((Enum<?>) value).name().equals(name)) return value;
        }

        throw new IllegalArgumentException("Missing enum value " + name + " on " + enumClass.getName());
    }

    private static String toFileName(String name) {
        StringBuilder fileName = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                fileName.append(character);
            } else {
                fileName.append('-');
            }
        }

        return fileName.toString();
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }
}
