package com.simplestructurescanner.client;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;

import com.simplestructurescanner.structure.LocalizedText;


/**
 * Resolves shared text descriptors on the client.
 */
public final class ClientTextResolver {

    private ClientTextResolver() {
    }

    public static String resolveKeyOrLiteral(@Nullable String text) {
        if (text == null || text.isEmpty()) return "";

        return I18n.hasKey(text) ? I18n.format(text) : text;
    }

    public static String resolve(@Nullable LocalizedText text) {
        if (text == null) return "";
        if (!text.isTranslatable()) return text.getValue();
        if (!I18n.hasKey(text.getValue()) && text.getFallback() != null) return resolve(text.getFallback());

        Object[] args = text.getArgs();
        Object[] resolvedArgs = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof LocalizedText) {
                resolvedArgs[i] = resolve((LocalizedText) arg);
                continue;
            }

            resolvedArgs[i] = arg;
        }

        return I18n.format(text.getValue(), resolvedArgs);
    }
}