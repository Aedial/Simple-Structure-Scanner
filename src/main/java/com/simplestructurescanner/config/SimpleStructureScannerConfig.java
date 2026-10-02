package com.simplestructurescanner.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Loader;

import com.simplestructurescanner.Tags;


@Config(modid = Tags.MODID, name = Tags.MODID, category = "")
@Config.LangKey("config.structurescanner.title")
public final class SimpleStructureScannerConfig {
    private static final String CLIENT_CATEGORY = "client";
    private static final String HIDDEN_CATEGORY = "hidden";

    private static File configRootDirectory;

    // HUD position enum
    public enum HudPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    // Client settings
    @Config.Name(CLIENT_CATEGORY)
    @Config.LangKey("config.structurescanner.client")
    public static ClientCategory client = new ClientCategory();

    // Server settings
    @Config.Name("server")
    @Config.LangKey("config.structurescanner.server")
    public static ServerCategory server = new ServerCategory();

    // Persistent client preferences
    @Config.Name(HIDDEN_CATEGORY)
    public static HiddenCategory hidden = new HiddenCategory();

    static {
        migrateLegacyHiddenSettings();
    }

    private SimpleStructureScannerConfig() {
    }

    public static boolean isHiddenCategory(String categoryName) {
        return HIDDEN_CATEGORY.equals(categoryName);
    }

    public static void init(File configFile) {
        configRootDirectory = new File(configFile.getParentFile(), Tags.MODID);
        if (!configRootDirectory.exists()) configRootDirectory.mkdirs();

        normalizeClientLists();
        syncConfig();
    }

    public static void syncConfig() {
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        normalizeClientLists();
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
    }

    public static boolean isProviderEnabled(String providerId) {
        return ProviderConfig.isEnabled(providerId);
    }

    public static void saveIfChanged() {
        syncConfig();
    }

    public static File getConfigRootDirectory() {
        return configRootDirectory;
    }

    private static void migrateLegacyHiddenSettings() {
        File configFile = new File(Loader.instance().getConfigDir(), Tags.MODID + ".cfg");
        Configuration config = new Configuration(configFile);
        config.load();

        if (!config.hasCategory(CLIENT_CATEGORY)) return;

        ConfigCategory clientCategory = config.getCategory(CLIENT_CATEGORY);
        ConfigCategory hiddenCategory = config.getCategory(HIDDEN_CATEGORY);
        boolean changed = false;

        changed |= moveLegacyBoolean(config, clientCategory, hiddenCategory, "i18nNames", true);
        changed |= moveLegacyString(config, clientCategory, hiddenCategory, "lastSelectedStructure", "");
        changed |= moveLegacyString(config, clientCategory, hiddenCategory, "filterText", "");
        changed |= moveLegacyBoolean(config, clientCategory, hiddenCategory, "showNonSearchable", true);
        changed |= moveLegacyBoolean(config, clientCategory, hiddenCategory, "showCurrentDimensionOnly", false);
        changed |= moveLegacyStrings(config, clientCategory, hiddenCategory, "searchedStructureIds");
        changed |= moveLegacyStrings(config, clientCategory, hiddenCategory, "blacklistedLocations");

        if (changed) config.save();
    }

    private static boolean moveLegacyBoolean(Configuration config, ConfigCategory clientCategory,
            ConfigCategory hiddenCategory, String name, boolean defaultValue) {
        Property property = clientCategory.get(name);
        if (property == null) return false;

        if (!hiddenCategory.containsKey(name)) {
            config.get(HIDDEN_CATEGORY, name, defaultValue).set(property.getBoolean(defaultValue));
        }

        clientCategory.remove(name);
        return true;
    }

    private static boolean moveLegacyString(Configuration config, ConfigCategory clientCategory,
            ConfigCategory hiddenCategory, String name, String defaultValue) {
        Property property = clientCategory.get(name);
        if (property == null) return false;

        if (!hiddenCategory.containsKey(name)) {
            config.get(HIDDEN_CATEGORY, name, defaultValue).set(property.getString());
        }

        clientCategory.remove(name);
        return true;
    }

    private static boolean moveLegacyStrings(Configuration config, ConfigCategory clientCategory,
            ConfigCategory hiddenCategory, String name) {
        Property property = clientCategory.get(name);
        if (property == null) return false;

        if (!hiddenCategory.containsKey(name)) {
            config.get(HIDDEN_CATEGORY, name, new String[0]).set(property.getStringList());
        }

        clientCategory.remove(name);
        return true;
    }

    private static void normalizeClientLists() {
        client.structureWhitelist = removeEmptyEntries(client.structureWhitelist);
        client.structureBlacklist = removeEmptyEntries(client.structureBlacklist);
        hidden.searchedStructureIds = removeEmptyEntries(hidden.searchedStructureIds);
        hidden.blacklistedLocations = removeEmptyEntries(hidden.blacklistedLocations);
    }

    private static String[] removeEmptyEntries(String[] entries) {
        boolean hasEmptyEntry = false;
        for (String entry : entries) {
            if (entry.isEmpty()) {
                hasEmptyEntry = true;
                break;
            }
        }

        if (!hasEmptyEntry) return entries;

        List<String> result = new ArrayList<>();
        for (String entry : entries) {
            if (!entry.isEmpty()) result.add(entry);
        }

        return result.toArray(new String[0]);
    }

    // --- Filter logic ---

    public enum FilterReason {
        NONE,
        NOT_WHITELISTED,
        BLACKLISTED
    }

    /**
     * Parse an entry to extract filter and optional radius.
     * Format: "filter" or "filter;radius"
     */
    private static String[] parseEntry(String entry) {
        String[] parts = entry.split(";", 2);
        String filter = parts[0];
        String radiusStr = parts.length > 1 ? parts[1].trim() : null;

        return new String[] { filter, radiusStr };
    }

    private static int parseRadius(String s) {
        if (s == null || s.isEmpty()) return -1;

        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Check if whitelist has any global entries (without radius).
     */
    public static boolean isWhitelistActive() {
        if (client.structureWhitelist.length == 0) return false;

        for (String entry : client.structureWhitelist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] == null) return true;
        }

        return false;
    }

    /**
     * Check if structure matches whitelist (global entries only, without radius).
     */
    public static boolean isWhitelisted(String id) {
        if (id == null) return false;

        for (String entry : client.structureWhitelist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] != null) continue;
            if (id.contains(parsed[0])) return true;
        }

        return false;
    }

    /**
     * Check if structure matches blacklist (global entries only, without radius).
     */
    public static boolean isBlacklisted(String id) {
        if (id == null) return false;

        for (String entry : client.structureBlacklist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] != null) continue;
            if (id.contains(parsed[0])) return true;
        }

        return false;
    }

    public static boolean isStructureAllowed(String id) {
        if (id == null) return false;
        if (!client.enableSearch) return false;
        if (isWhitelistActive()) return isWhitelisted(id);
        if (isBlacklisted(id)) return false;

        return true;
    }

    public static FilterReason getFilterReason(String id) {
        if (id == null) return FilterReason.BLACKLISTED;
        if (isWhitelistActive()) return isWhitelisted(id) ? FilterReason.NONE : FilterReason.NOT_WHITELISTED;

        return isBlacklisted(id) ? FilterReason.BLACKLISTED : FilterReason.NONE;
    }

    /**
     * Check if a structure is allowed by local whitelist/blacklist when within radius.
     * Entries with ";radius" are local entries.
     */
    public static boolean isLocallyAllowed(String id, double distance) {
        if (id == null) return false;

        // Check local whitelist entries (with radius)
        boolean hasLocalWhitelist = false;
        for (String entry : client.structureWhitelist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] == null) continue;

            hasLocalWhitelist = true;
            int radius = parseRadius(parsed[1]);

            if (id.contains(parsed[0]) && distance <= radius) return true;
        }

        if (hasLocalWhitelist) return false;

        // Check local blacklist entries (with radius)
        for (String entry : client.structureBlacklist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] == null) continue;

            int radius = parseRadius(parsed[1]);

            if (id.contains(parsed[0]) && distance > radius) return false;
        }

        return true;
    }

    // --- Getters and setters ---

    public static void setClientI18nNames(boolean value) {
        if (hidden.i18nNames == value) return;

        hidden.i18nNames = value;
        syncConfig();
    }

    public static String getClientLastSelectedStructure() {
        return hidden.lastSelectedStructure;
    }

    public static void setClientLastSelectedStructure(String structureId) {
        if (structureId == null) structureId = "";
        if (hidden.lastSelectedStructure.equals(structureId)) return;

        hidden.lastSelectedStructure = structureId;
        syncConfig();
    }

    public static String getClientFilterText() {
        return hidden.filterText;
    }

    public static void setClientFilterText(String text) {
        if (text == null) text = "";
        if (hidden.filterText.equals(text)) return;

        hidden.filterText = text;
        syncConfig();
    }

    public static void setClientShowNonSearchable(boolean value) {
        if (hidden.showNonSearchable == value) return;

        hidden.showNonSearchable = value;
        syncConfig();
    }

    public static void setClientShowCurrentDimensionOnly(boolean value) {
        if (hidden.showCurrentDimensionOnly == value) return;

        hidden.showCurrentDimensionOnly = value;
        syncConfig();
    }

    public static List<String> getClientTrackedIds() {
        return new ArrayList<>(Arrays.asList(hidden.searchedStructureIds));
    }

    public static void setClientTrackedIds(Collection<String> ids) {
        String[] newIds = ids.toArray(new String[0]);
        if (Arrays.equals(hidden.searchedStructureIds, newIds)) return;

        hidden.searchedStructureIds = newIds;
        syncConfig();
    }

    public static HudPosition getClientHudPosition() {
        try {
            return HudPosition.valueOf(client.hudPosition);
        } catch (IllegalArgumentException e) {
            return HudPosition.TOP_LEFT;
        }
    }

    public static void setClientHudPosition(HudPosition position) {
        if (position == null || getClientHudPosition() == position) return;

        client.hudPosition = position.name();
        syncConfig();
    }

    public static boolean isClientHudEnabled() {
        return client.hudEnabled;
    }

    public static boolean isClientJeiCategoriesEnabled() {
        return client.enableJeiCategories;
    }

    public static boolean isClientJeiPreviewEnabled() {
        return client.enableJeiPreview;
    }

    public static boolean isClientJeiBlocksEnabled() {
        return client.enableJeiBlocks;
    }

    public static boolean isClientJeiLootEnabled() {
        return client.enableJeiLoot;
    }

    public static boolean isSearchEnabled() {
        return client.enableSearch && server.enableSearch;
    }

    // --- Blacklisted locations management ---

    /**
     * Format: "worldSeed|structureId|x|z" (y is omitted for y-agnostic locations)
     * or "worldSeed|structureId|x|y|z" for exact locations.
     */
    public static void addBlacklistedLocation(long worldSeed, String structureId, int x, int y, int z, boolean yAgnostic) {
        String entry = yAgnostic
            ? String.format("%d|%s|%d|%d", worldSeed, structureId, x, z)
            : String.format("%d|%s|%d|%d|%d", worldSeed, structureId, x, y, z);

        if (contains(hidden.blacklistedLocations, entry)) return;

        hidden.blacklistedLocations = append(hidden.blacklistedLocations, entry);
        syncConfig();
    }

    public static void removeBlacklistedLocation(long worldSeed, String structureId, int x, int y, int z, boolean yAgnostic) {
        String entry = yAgnostic
            ? String.format("%d|%s|%d|%d", worldSeed, structureId, x, z)
            : String.format("%d|%s|%d|%d|%d", worldSeed, structureId, x, y, z);

        List<String> locations = new ArrayList<>(Arrays.asList(hidden.blacklistedLocations));
        if (!locations.remove(entry)) return;

        hidden.blacklistedLocations = locations.toArray(new String[0]);
        syncConfig();
    }

    public static boolean isLocationBlacklisted(long worldSeed, String structureId, int x, int y, int z) {
        // Check y-agnostic format first
        String yAgnosticEntry = String.format("%d|%s|%d|%d", worldSeed, structureId, x, z);
        if (contains(hidden.blacklistedLocations, yAgnosticEntry)) return true;

        // Check exact format
        String exactEntry = String.format("%d|%s|%d|%d|%d", worldSeed, structureId, x, y, z);

        return contains(hidden.blacklistedLocations, exactEntry);
    }

    public static List<String> getBlacklistedLocations() {
        return new ArrayList<>(Arrays.asList(hidden.blacklistedLocations));
    }

    private static boolean contains(String[] entries, String target) {
        for (String entry : entries) {
            if (entry.equals(target)) return true;
        }

        return false;
    }

    private static String[] append(String[] entries, String value) {
        String[] result = Arrays.copyOf(entries, entries.length + 1);
        result[entries.length] = value;

        return result;
    }

    public static class ClientCategory {
        @Config.LangKey("config.structurescanner.client.enableSearch")
        public boolean enableSearch = true;

        @Config.LangKey("config.structurescanner.client.showBlocks")
        public boolean showBlocks = true;

        @Config.LangKey("config.structurescanner.client.showEntities")
        public boolean showEntities = true;

        @Config.LangKey("config.structurescanner.client.showLootTables")
        public boolean showLootTables = true;

        @Config.LangKey("config.structurescanner.client.enableJeiCategories")
        public boolean enableJeiCategories = true;

        @Config.LangKey("config.structurescanner.client.enableJeiPreview")
        public boolean enableJeiPreview = true;

        @Config.LangKey("config.structurescanner.client.enableJeiBlocks")
        public boolean enableJeiBlocks = true;

        @Config.LangKey("config.structurescanner.client.enableJeiLoot")
        public boolean enableJeiLoot = true;

        @Config.LangKey("config.structurescanner.client.hudEnabled")
        public boolean hudEnabled = true;

        @Config.LangKey("config.structurescanner.client.hudPosition")
        public String hudPosition = HudPosition.TOP_LEFT.name();

        @Config.LangKey("config.structurescanner.client.hudPaddingExternal")
        @Config.RangeInt(min = 0, max = 100)
        public int hudPaddingExternal = 4;

        @Config.LangKey("config.structurescanner.client.hudPaddingInternal")
        @Config.RangeInt(min = 0, max = 50)
        public int hudPaddingInternal = 2;

        @Config.LangKey("config.structurescanner.client.hudLineSpacing")
        @Config.RangeInt(min = 0, max = 20)
        public int hudLineSpacing = 2;

        // Whitelist/blacklist
        @Config.LangKey("config.structurescanner.client.structureWhitelist")
        public String[] structureWhitelist = new String[0];

        @Config.LangKey("config.structurescanner.client.structureBlacklist")
        public String[] structureBlacklist = new String[0];

    }

    public static class HiddenCategory {
        public boolean i18nNames = true;

        public String lastSelectedStructure = "";

        public String filterText = "";

        public boolean showNonSearchable = true;

        public boolean showCurrentDimensionOnly = false;

        public String[] searchedStructureIds = new String[0];

        public String[] blacklistedLocations = new String[0];
    }

    public static class ServerCategory {
        @Config.LangKey("config.structurescanner.server.enableSearch")
        public boolean enableSearch = true;
    }
}
