package com.simplestructurescanner.structure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    // Stage-qualified blacklist entries are only evaluated after the GUI captures a client snapshot.
    @Nullable
    private static Set<String> ACTIVE_STAGE_SNAPSHOT = null;

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

    public enum StageConditionType {
        PRESENT("stage"),
        MISSING("nostage");

        private final String token;

        StageConditionType(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        @Nullable
        public static StageConditionType fromToken(String token) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "stage":
                case "gamestage":
                    return PRESENT;
                case "nostage":
                case "missingstage":
                    return MISSING;
                default:
                    return null;
            }
        }
    }

    public static final class StageCondition {
        private final StageConditionType type;
        private final String stageName;

        private StageCondition(StageConditionType type, String stageName) {
            this.type = type;
            this.stageName = stageName;
        }

        public static StageCondition of(StageConditionType type, String stageName) {
            return new StageCondition(type, normalizeStageName(stageName));
        }

        public StageConditionType getType() {
            return type;
        }

        public String getStageName() {
            return stageName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StageCondition)) return false;

            StageCondition that = (StageCondition) other;
            return type == that.type && stageName.equals(that.stageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, stageName);
        }
    }

    private static final class RuleEntries {
        private final Set<ResourceLocation> structures = new HashSet<>();
        private final Set<Integer> dimensions = new HashSet<>();
        private final Map<ResourceLocation, Set<Integer>> structureDimensions = new HashMap<>();
    }

    private static final class ProviderRules {
        private final RuleEntries always = new RuleEntries();
        private final Map<String, RuleEntries> whenStagePresent = new HashMap<>();
        private final Map<String, RuleEntries> whenStageMissing = new HashMap<>();
    }

    private static final class ParsedEntry {
        private final EntryType type;
        @Nullable
        private final ResourceLocation structureId;
        @Nullable
        private final Integer dimensionId;
        @Nullable
        private final StageCondition stageCondition;

        private ParsedEntry(EntryType type, @Nullable ResourceLocation structureId, @Nullable Integer dimensionId,
                @Nullable StageCondition stageCondition) {
            this.type = type;
            this.structureId = structureId;
            this.dimensionId = dimensionId;
            this.stageCondition = stageCondition;
        }
    }

    private StructureSearchOverrides() {
    }

    public static void setActiveStageSnapshot(@Nullable Set<String> stages) {
        if (stages == null) {
            ACTIVE_STAGE_SNAPSHOT = null;
            return;
        }

        Set<String> normalizedStages = new HashSet<>();

        for (String stage : stages) {
            if (stage == null) continue;

            String normalizedStage = stage.trim().toLowerCase(Locale.ROOT);
            if (!normalizedStage.isEmpty()) normalizedStages.add(normalizedStage);
        }

        ACTIVE_STAGE_SNAPSHOT = Collections.unmodifiableSet(normalizedStages);
    }

    public static void clearActiveStageSnapshot() {
        ACTIVE_STAGE_SNAPSHOT = null;
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
            @Nullable ResourceLocation structureId, @Nullable Integer dimensionId,
            @Nullable StageCondition stageCondition) {
        File file = getBlacklistFile(blacklistType, providerId);
        if (file == null || !file.exists()) return false;

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> updatedLines = new ArrayList<>(lines.size());
            boolean removed = false;

            for (String line : lines) {
                ParsedEntry parsed = parseEntry(blacklistType, providerId, line, file.getName(), -1, false);
                if (!removed && matches(entryType, structureId, dimensionId, stageCondition, parsed)) {
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
        RuleEntries target = getRuleEntries(rules, entry.stageCondition);

        applyEntry(target, entry);
    }

    private static RuleEntries getRuleEntries(ProviderRules rules, @Nullable StageCondition stageCondition) {
        if (stageCondition == null) return rules.always;

        Map<String, RuleEntries> stageRules = stageCondition.getType() == StageConditionType.PRESENT
            ? rules.whenStagePresent
            : rules.whenStageMissing;

        return stageRules.computeIfAbsent(stageCondition.getStageName(), key -> new RuleEntries());
    }

    private static void applyEntry(RuleEntries rules, ParsedEntry entry) {
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
            @Nullable Integer dimensionId, @Nullable StageCondition stageCondition, @Nullable ParsedEntry parsed) {
        if (parsed == null) return false;
        if (!Objects.equals(parsed.stageCondition, stageCondition)) return false;
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

        if (isBlacklisted(rules.always, structureId, null)) return true;

        return matchesConditionalRules(rules, structureId, null);
    }

    private static boolean isStructureBlacklistedInDimension(Map<String, ProviderRules> rulesByProvider,
            String providerId, ResourceLocation structureId, int dimensionId) {
        ProviderRules rules = rulesByProvider.get(providerId);
        if (rules == null) return false;

        if (isBlacklisted(rules.always, structureId, dimensionId)) return true;

        return matchesConditionalRules(rules, structureId, dimensionId);
    }

    private static boolean isBlacklisted(RuleEntries rules, ResourceLocation structureId, @Nullable Integer dimensionId) {
        if (rules.structures.contains(structureId)) return true;
        if (dimensionId == null) return false;
        if (rules.dimensions.contains(dimensionId)) return true;

        Set<Integer> structureDimensions = rules.structureDimensions.get(structureId);
        if (structureDimensions == null) return false;

        return structureDimensions.contains(dimensionId);
    }

    // Stage-qualified entries only participate after a GUI-open snapshot is available.
    private static boolean matchesConditionalRules(ProviderRules rules, ResourceLocation structureId,
            @Nullable Integer dimensionId) {
        Set<String> activeStages = ACTIVE_STAGE_SNAPSHOT;
        if (activeStages == null) return false;

        for (String activeStage : activeStages) {
            RuleEntries stageRules = rules.whenStagePresent.get(activeStage);
            if (stageRules != null && isBlacklisted(stageRules, structureId, dimensionId)) return true;
        }

        for (Map.Entry<String, RuleEntries> entry : rules.whenStageMissing.entrySet()) {
            if (activeStages.contains(entry.getKey())) continue;
            if (isBlacklisted(entry.getValue(), structureId, dimensionId)) return true;
        }

        return false;
    }

    @Nullable
    private static ParsedEntry parseEntry(BlacklistType blacklistType, String providerId, String line,
            String sourceName, int lineNumber, boolean logErrors) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) return null;

        try {
            int entryIndex = 0;
            StageCondition stageCondition = null;

            StageConditionType stageConditionType = StageConditionType.fromToken(parts[0]);
            if (stageConditionType != null) {
                if (parts.length < 4) return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);

                stageCondition = StageCondition.of(stageConditionType, parts[1]);
                entryIndex = 2;
            }

            if ("structure".equalsIgnoreCase(parts[entryIndex])) {
                if (parts.length != entryIndex + 2) {
                    return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);
                }

                return new ParsedEntry(EntryType.STRUCTURE,
                    parseStructureId(providerId, parts[entryIndex + 1]), null, stageCondition);
            }

            if (!"dimension".equalsIgnoreCase(parts[entryIndex])) {
                return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);
            }
            if (parts.length == entryIndex + 2) {
                return new ParsedEntry(EntryType.DIMENSION, null, Integer.valueOf(parts[entryIndex + 1]),
                    stageCondition);
            }

            if (parts.length != entryIndex + 3) {
                return invalidEntry(blacklistType, sourceName, lineNumber, trimmed, logErrors);
            }
            if ("*".equals(parts[entryIndex + 1]) || "all".equalsIgnoreCase(parts[entryIndex + 1])) {
                return new ParsedEntry(EntryType.DIMENSION, null, Integer.valueOf(parts[entryIndex + 2]),
                    stageCondition);
            }

            return new ParsedEntry(EntryType.STRUCTURE_DIMENSION,
                parseStructureId(providerId, parts[entryIndex + 1]), Integer.valueOf(parts[entryIndex + 2]),
                stageCondition);
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

    private static String normalizeStageName(String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Stage name cannot be empty");

        return normalized;
    }

    private static ResourceLocation parseStructureId(String providerId, String token) {
        return token.contains(":") ? new ResourceLocation(token) : new ResourceLocation(providerId, token);
    }
}