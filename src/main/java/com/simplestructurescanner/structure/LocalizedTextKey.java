package com.simplestructurescanner.structure;

import javax.annotation.Nullable;


/**
 * Key for text that has yet to be localized.
 */
final class LocalizedTextKey {

    private final boolean translatable;
    private final String value;

    private LocalizedTextKey(LocalizedText text) {
        this.translatable = text.isTranslatable();
        this.value = text.getValue();
    }

    @Nullable
    static LocalizedTextKey of(@Nullable LocalizedText text) {
        return text == null ? null : new LocalizedTextKey(text);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        LocalizedTextKey that = (LocalizedTextKey) obj;
        return translatable == that.translatable && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(translatable);
        return 31 * result + value.hashCode();
    }
}