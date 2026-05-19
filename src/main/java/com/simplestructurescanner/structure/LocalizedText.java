package com.simplestructurescanner.structure;

import java.util.Arrays;

import javax.annotation.Nullable;


/**
 * Holds either a translation key with arguments or a literal fallback string.
 * This lets common code keep text data without resolving it server-side.
 */
public final class LocalizedText {

    private static final Object[] NO_ARGS = new Object[0];

    private final String value;
    private final boolean translatable;
    private final Object[] args;
    @Nullable
    private final LocalizedText fallback;

    private LocalizedText(String value, boolean translatable, Object[] args, @Nullable LocalizedText fallback) {
        this.value = value;
        this.translatable = translatable;
        this.args = args.length == 0 ? NO_ARGS : Arrays.copyOf(args, args.length);
        this.fallback = fallback;
    }

    public static LocalizedText translatable(String key, Object... args) {
        return new LocalizedText(key, true, args == null ? NO_ARGS : args, null);
    }

    public static LocalizedText translatableWithFallback(String key, LocalizedText fallback, Object... args) {
        return new LocalizedText(key, true, args == null ? NO_ARGS : args, fallback);
    }

    public static LocalizedText literal(String text) {
        return new LocalizedText(text, false, NO_ARGS, null);
    }

    public String getValue() {
        return value;
    }

    public boolean isTranslatable() {
        return translatable;
    }

    public Object[] getArgs() {
        return Arrays.copyOf(args, args.length);
    }

    @Nullable
    public LocalizedText getFallback() {
        return fallback;
    }

    @Override
    public String toString() {
        return value;
    }
}