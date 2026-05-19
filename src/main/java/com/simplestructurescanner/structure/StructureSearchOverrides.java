package com.simplestructurescanner.structure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.config.ModConfig;


/**
 * Loads per-provider hidden and search blacklist files from the config directory.
 */
public final class StructureSearchOverrides {

    private static final String FILE_EXTENSION = ".txt";

    private static final Map<String, ProviderRules> HIDDEN_RULES = new LinkedHashMap<>();
    private static final Map<String, ProviderRules> SEARCH_RULES = new LinkedHashMap<>();

    public enum BlacklistType {
        HIDDEN("hidden-blacklists"),
        SEARCH("search-blacklists");

        private final String directoryName;

        BlacklistType(String directoryName) {
            this.directoryName = directoryName;
        }

        public String getDirectoryName() {
            return directoryName;
        }
    }

    public enum EntryType {
        STRUCTURE,
        DIMENSION,
        STRUCTURE_DIMENSION
    }

    private static final class ProviderRules {
        private final Set<ResourceLocation> structures = new HashSet<>();
        private final Set<Integer> dimensions = new HashSet<>();
        private final Map<ResourceLocation, Set<Integer>> structureDimensions = new HashMap<>();
    }

    private static final class ParsedEntry {
        private final EntryType type;
        @Nullable
        private final ResourceLocation structureId;
        @Nullable
        private final Integer dimensionId;

        private ParsedEntry(EntryType type, @Nullable ResourceLocation structureId, @Nullable Integer dimensionId) {
            this.type = type;
            this.structureId = structureId;
            this.dimensionId = dimensionId;
        }
    }

    private StructureSearchOverrides() {
    }

    public static void load() {
        HIDDEN_RULES.clear();
        SEARCH_RULES.clear();

        loadBlacklist(BlacklistType.HIDDEN, HIDDEN_RULES);
        loadBlacklist(BlacklistType.SEARCH, SEARCH_RULES);
    }

    public static boolean isStructureHidden(String providerId, ResourceLocation structureId) {
        return isStructureBlacklisted(HIDDEN_RULES, providerId, structureId);
    }

    public static boolean isStructureHiddenInDimension(String providerId, ResourceLocation structureId, int dimensionId) {
        return isStructureBlacklistedInDimension(HIDDEN_RULES, providerId, structureId, dimensionId);
    }

    public static boolean isStructureSearchBlacklisted(String providerId, ResourceLocation structureId) {
        return isStructureBlacklisted(SEARCH_RULES, providerId, structureId);
    }

    public static boolean isStructureSearchBlacklistedInDimension(String providerId,
            ResourceLocation structureId, int dimensionId) {
        return isStructureBlacklistedInDimension(SEARCH_RULES, providerId, structureId, dimensionId);
    }

    public static boolean canStructureBeSearched(String providerId, ResourceLocation structureId,
            @Nullable Integer dimensionId, boolean providerCanSearch) {
        if (!providerCanSearch) return false;
        if (dimensionId == null) return !isStructureSearchBlacklisted(providerId, structureId);

        return !isStructureSearchBlacklistedInDimension(providerId, structureId, dimensionId);
    }

    public static boolean removeEntry(BlacklistType blacklistType, String providerId, EntryType entryType,
            @Nullable ResourceLocation structureId, @Nullable Integer dimensionId) {
        File file = getBlacklistFile(blacklistType, providerId);
        if (file == null || !file.exists()) return false;

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> updatedLines = new ArrayList<>(lines.size());
            boolean removed = false;

            for (String line : lines) {
                ParsedEntry parsed = parseEntry(blacklistType, providerId, line, file.getName(), -1, false);
                if (!removed && matches(entryType, structureId, dimensionId, parsed)) {
                    removed = true;
                    continue;
                }

                updatedLines.add(line);
            }

            if (!removed) return false;

            Files.write(file.toPath(), updatedLines, StandardCharsets.UTF_8);
            load();

            return true;
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to update {} file {}",
                blacklistType.getDirectoryName(), file, e);
            return false;
        }
    }

    @Nullable
    public static File getBlacklistDirectory(BlacklistType blacklistType) {
        File configRoot = ModConfig.getConfigRootDirectory();
        if (configRoot == null) return null;

        return new File(configRoot, blacklistType.getDirectoryName());
    }

    @Nullable
    public static File getBlacklistFile(BlacklistType blacklistType, String providerId) {
        File directory = getBlacklistDirectory(blacklistType);
        if (directory == null) return null;

        return new File(directory, providerId + FILE_EXTENSION);
    }

    private static void loadBlacklist(BlacklistType blacklistType, Map<String, ProviderRules> rulesByProvider) {
        File directory = getBlacklistDirectory(blacklistType);
        if (directory == null) return;
        if (!directory.exists() && !directory.mkdirs()) {
            SimpleStructureScanner.LOGGER.warn("Could not create {} directory at {}",
                blacklistType.getDirectoryName(), directory);
            return;
        }

        File[] files = directory.listFiles((dir, name) -> name.endsWith(FILE_EXTENSION));
        if (files == null || files.length == 0) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) loadFile(blacklistType, rulesByProvider, file);
    }

    private static void loadFile(BlacklistType blacklistType, Map<String, ProviderRules> rulesByProvider, File file) {
        String providerId = file.getName().substring(0, file.getName().length() - FILE_EXTENSION.length());
        ProviderRules rules = new ProviderRules();

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            for (int i = 0; i < lines.size(); i++) {
                ParsedEntry parsed = parseEntry(blacklistType, providerId, lines.get(i), file.getName(), i + 1, true);
                if (parsed == null) continue;

                applyEntry(rules, parsed);
            }
        } catch (IOException e) {
            SimpleStructureScanner.LOGGER.warn("Failed to load {} file {}",
                blacklistType.getDirectoryName(), file, e);
            return;
        }

        rulesByProvider.put(providerId, rules);
    }

    private static void applyEntry(ProviderRules rules, ParsedEntry entry) {
        switch (entry.type) {
            case STRUCTURE:
                rules.structures.add(entry.structureId);
                return;
            case DIMENSION:
                rules.dimensions.add(entry.dimensionId);
                return;
            case STRUCTURE_DIMENSION:
                rules.structureDimensions
                    .computeIfAbsent(entry.structureId, key -> new HashSet<>())
                    .add(entry.dimensionId);
                return;
            default:
        }
    }

    private static boolean matches(EntryType entryType, @Nullable ResourceLocation structureId,
            @Nullable Integer dimensionId, @Nullable ParsedEntry parsed) {
        if (parsed == null) return false;
        if (parsed.type != entryType) return false;

        switch (entryType) {
            case STRUCTURE:
                return parsed.structureId != null && parsed.structureId.equals(structureId);
            case DIMENSION:
                return parsed.dimensionId != null && parsed.dimensionId.equals(dimensionId);
            case STRUCTURE_DIMENSION:
                return parsed.structureId != null && parsed.structureId.equals(structureId)
                    && parsed.dimensionId != null && parsed.dimensionId.equals(dimensionId);
            default:
                return false;
        }
    }

    private static boolean isStructureBlacklisted(Map<String, ProviderRules> rulesByProvider,
            String providerId, ResourceLocation structureId) {
        ProviderRules rules = rulesByProvider.get(providerId);
        if (rules == null) return false;

        return rules.structures.contains(structureId);
    }

    private static boolean isStructureBlacklistedInDimension(Map<String, ProviderRules> rulesByProvider,
            String providerId, ResourceLocation structureId, int dimensionId) {
        ProviderRules rules = rulesByProvider.get(providerId);
        if (rules == null) return false;
        if (rules.structures.contains(structureId)) return true;
        if (rules.dimensions.contains(dimensionId)) return true;

        Set<Integer> structureDimensions = rules.structureDimensions.get(structureId);
        if (structureDimensions == null) return false;

        return structureDimensions.contains(dimensionId);
    }

    @Nullable
    private static ParsedEntry parseEntry(BlacklistType blacklistType, String providerId, String line,
            String sourceName, int lineNumber, boolean logErrors) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) return null;

        try {
            if ("structure".equalsIgnoreCase(parts[0])) {
                if (parts.length != 2) return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);

                return new ParsedEntry(EntryType.STRUCTURE, parseStructureId(providerId, parts[1]), null);
            }

            if (!"dimension".equalsIgnoreCase(parts[0])) {
                return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);
            }
            if (parts.length == 2) {
                return new ParsedEntry(EntryType.DIMENSION, null, Integer.valueOf(parts[1]));
            }

            if (parts.length != 3) return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);
            if ("*".equals(parts[1]) || "all".equalsIgnoreCase(parts[1])) {
                return new ParsedEntry(EntryType.DIMENSION, null, Integer.valueOf(parts[2]));
            }

            return new ParsedEntry(EntryType.STRUCTURE_DIMENSION,
                parseStructureId(providerId, parts[1]), Integer.valueOf(parts[2]));
        } catch (IllegalArgumentException e) {
            if (logErrors) {
                SimpleStructureScanner.LOGGER.warn("Invalid {} entry in {}:{} -> {}",
                    blacklistType.getDirectoryName(), sourceName, lineNumber, trimmed);
            }

            return null;
        }
    }

    @Nullable
    private static ParsedEntry invalidEntry(BlacklistType blacklistType, String sourceName,
            int lineNumber, String trimmed, boolean logErrors) {
        if (logErrors) {
            SimpleStructureScanner.LOGGER.warn("Invalid {} entry in {}:{} -> {}",
                blacklistType.getDirectoryName(), sourceName, lineNumber, trimmed);
        }

        return null;
    }

    private static ResourceLocation parseStructureId(String providerId, String token) {
        return token.contains(":") ? new ResourceLocation(token) : new ResourceLocation(providerId, token);
    }
}