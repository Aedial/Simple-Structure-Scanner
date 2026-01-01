package com.simplestructurescanner.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;


public class ModConfig {
    private static Configuration config;

    // HUD position enum
    public enum HudPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    // Client settings
    public static boolean clientEnableSearch = true;
    public static boolean clientI18nNames = true;
    public static String clientLastSelectedStructure = "";
    public static String clientFilterText = "";
    public static List<String> clientTrackedStructureIds = new ArrayList<>();
    public static List<String> clientStructureWhitelist = new ArrayList<>();
    public static List<String> clientStructureBlacklist = new ArrayList<>();
    public static List<String> clientBlacklistedLocations = new ArrayList<>();
    public static boolean clientShowBlocks = true;
    public static boolean clientShowEntities = true;
    public static boolean clientShowLootTables = true;
    public static boolean clientHudEnabled = true;
    public static HudPosition clientHudPosition = HudPosition.TOP_LEFT;
    public static int clientHudPaddingExternal = 4;
    public static int clientHudPaddingInternal = 2;
    public static int clientHudLineSpacing = 2;

    // Server settings
    public static boolean serverEnableSearch = true;

    private static final List<String> hiddenConfigs = Arrays.asList(
        "i18nNames",
        "searchedStructureIds",
        "lastSelectedStructure",
        "filterText",
        "hudPosition"
    );

    public static void loadConfigs(File configFile) {
        if (config == null) config = new Configuration(configFile);

        syncFromFile();
    }

    public static void syncFromFile() {
        config.load();
        syncFromConfig();
    }

    public static void syncFromConfig() {
        Property prop;

        // Client settings
        prop = config.get("client", "enableSearch", clientEnableSearch);
        prop.setLanguageKey("config.structurescanner.client.enableSearch");
        clientEnableSearch = prop.getBoolean();

        prop = config.get("client", "showBlocks", clientShowBlocks);
        prop.setLanguageKey("config.structurescanner.client.showBlocks");
        clientShowBlocks = prop.getBoolean();

        prop = config.get("client", "showEntities", clientShowEntities);
        prop.setLanguageKey("config.structurescanner.client.showEntities");
        clientShowEntities = prop.getBoolean();

        prop = config.get("client", "showLootTables", clientShowLootTables);
        prop.setLanguageKey("config.structurescanner.client.showLootTables");
        clientShowLootTables = prop.getBoolean();

        prop = config.get("client", "hudEnabled", clientHudEnabled);
        prop.setLanguageKey("config.structurescanner.client.hudEnabled");
        clientHudEnabled = prop.getBoolean();

        prop = config.get("client", "hudPosition", clientHudPosition.name());
        prop.setLanguageKey("config.structurescanner.client.hudPosition");
        String hudPosStr = prop.getString();
        try {
            clientHudPosition = HudPosition.valueOf(hudPosStr);
        } catch (IllegalArgumentException e) {
            clientHudPosition = HudPosition.TOP_LEFT;
        }

        prop = config.get("client", "hudPaddingExternal", clientHudPaddingExternal, "", 0, 100);
        prop.setLanguageKey("config.structurescanner.client.hudPaddingExternal");
        clientHudPaddingExternal = prop.getInt();

        prop = config.get("client", "hudPaddingInternal", clientHudPaddingInternal, "", 0, 50);
        prop.setLanguageKey("config.structurescanner.client.hudPaddingInternal");
        clientHudPaddingInternal = prop.getInt();

        prop = config.get("client", "hudLineSpacing", clientHudLineSpacing, "", 0, 20);
        prop.setLanguageKey("config.structurescanner.client.hudLineSpacing");
        clientHudLineSpacing = prop.getInt();

        // Whitelist/blacklist
        prop = config.get("client", "structureWhitelist", new String[0]);
        prop.setLanguageKey("config.structurescanner.client.structureWhitelist");
        clientStructureWhitelist = new ArrayList<>();
        for (String s : prop.getStringList()) {
            if (!s.isEmpty()) clientStructureWhitelist.add(s);
        }

        prop = config.get("client", "structureBlacklist", new String[0]);
        prop.setLanguageKey("config.structurescanner.client.structureBlacklist");
        clientStructureBlacklist = new ArrayList<>();
        for (String s : prop.getStringList()) {
            if (!s.isEmpty()) clientStructureBlacklist.add(s);
        }

        // Hidden configs
        prop = config.get("client", "i18nNames", clientI18nNames);
        prop.setLanguageKey("config.structurescanner.client.i18nNames");
        clientI18nNames = prop.getBoolean();

        prop = config.get("client", "lastSelectedStructure", clientLastSelectedStructure);
        prop.setLanguageKey("config.structurescanner.client.lastSelectedStructure");
        clientLastSelectedStructure = prop.getString();

        prop = config.get("client", "filterText", clientFilterText);
        prop.setLanguageKey("config.structurescanner.client.filterText");
        clientFilterText = prop.getString();

        prop = config.get("client", "searchedStructureIds", new String[0]);
        prop.setLanguageKey("config.structurescanner.client.searchedStructureIds");
        clientTrackedStructureIds = new ArrayList<>();
        for (String s : prop.getStringList()) {
            if (!s.isEmpty()) clientTrackedStructureIds.add(s);
        }

        prop = config.get("client", "blacklistedLocations", new String[0]);
        prop.setLanguageKey("config.structurescanner.client.blacklistedLocations");
        clientBlacklistedLocations = new ArrayList<>();
        for (String s : prop.getStringList()) {
            if (!s.isEmpty()) clientBlacklistedLocations.add(s);
        }

        // Server settings
        prop = config.get("server", "enableSearch", serverEnableSearch);
        prop.setLanguageKey("config.structurescanner.server.enableSearch");
        serverEnableSearch = prop.getBoolean();

        if (config.hasChanged()) config.save();
    }

    public static Configuration getConfig() {
        return config;
    }

    public static boolean isConfigHidden(String name) {
        return hiddenConfigs.contains(name);
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
        if (clientStructureWhitelist == null || clientStructureWhitelist.isEmpty()) return false;

        for (String entry : clientStructureWhitelist) {
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

        for (String entry : clientStructureWhitelist) {
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

        for (String entry : clientStructureBlacklist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] != null) continue;
            if (id.contains(parsed[0])) return true;
        }

        return false;
    }

    public static boolean isStructureAllowed(String id) {
        if (id == null) return false;
        if (!clientEnableSearch) return false;
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
        for (String entry : clientStructureWhitelist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] == null) continue;

            hasLocalWhitelist = true;
            int radius = parseRadius(parsed[1]);

            if (id.contains(parsed[0]) && distance <= radius) return true;
        }

        if (hasLocalWhitelist) return false;

        // Check local blacklist entries (with radius)
        for (String entry : clientStructureBlacklist) {
            String[] parsed = parseEntry(entry);

            if (parsed[1] == null) continue;

            int radius = parseRadius(parsed[1]);

            if (id.contains(parsed[0]) && distance > radius) return false;
        }

        return true;
    }

    // --- Getters and setters ---

    public static void setClientI18nNames(boolean value) {
        if (clientI18nNames == value) return;

        clientI18nNames = value;
        if (config != null) {
            config.get("client", "i18nNames", true).set(value);
            config.save();
        }
    }

    public static String getClientLastSelectedStructure() {
        return clientLastSelectedStructure;
    }

    public static void setClientLastSelectedStructure(String structureId) {
        if (structureId == null) structureId = "";
        if (clientLastSelectedStructure.equals(structureId)) return;

        clientLastSelectedStructure = structureId;
        if (config != null) {
            config.get("client", "lastSelectedStructure", "").set(structureId);
            config.save();
        }
    }

    public static String getClientFilterText() {
        return clientFilterText;
    }

    public static void setClientFilterText(String text) {
        if (text == null) text = "";
        if (clientFilterText.equals(text)) return;

        clientFilterText = text;
        if (config != null) {
            config.get("client", "filterText", "").set(text);
            config.save();
        }
    }

    public static List<String> getClientTrackedIds() {
        return new ArrayList<>(clientTrackedStructureIds);
    }

    public static void setClientTrackedIds(Collection<String> ids) {
        List<String> newList = new ArrayList<>(ids);
        if (clientTrackedStructureIds.equals(newList)) return;

        clientTrackedStructureIds = newList;
        if (config != null) {
            config.get("client", "searchedStructureIds", new String[0]).set(newList.toArray(new String[0]));
            config.save();
        }
    }

    public static HudPosition getClientHudPosition() {
        return clientHudPosition;
    }

    public static void setClientHudPosition(HudPosition position) {
        if (clientHudPosition == position) return;

        clientHudPosition = position;
        if (config != null) {
            config.get("client", "hudPosition", HudPosition.TOP_LEFT.name()).set(position.name());
            config.save();
        }
    }

    public static boolean isClientHudEnabled() {
        return clientHudEnabled;
    }

    public static boolean isSearchEnabled() {
        return clientEnableSearch && serverEnableSearch;
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

        if (!clientBlacklistedLocations.contains(entry)) {
            clientBlacklistedLocations.add(entry);
            saveBlacklistedLocations();
        }
    }

    public static void removeBlacklistedLocation(long worldSeed, String structureId, int x, int y, int z, boolean yAgnostic) {
        String entry = yAgnostic
            ? String.format("%d|%s|%d|%d", worldSeed, structureId, x, z)
            : String.format("%d|%s|%d|%d|%d", worldSeed, structureId, x, y, z);

        if (clientBlacklistedLocations.remove(entry)) {
            saveBlacklistedLocations();
        }
    }

    public static boolean isLocationBlacklisted(long worldSeed, String structureId, int x, int y, int z) {
        // Check y-agnostic format first
        String yAgnosticEntry = String.format("%d|%s|%d|%d", worldSeed, structureId, x, z);
        if (clientBlacklistedLocations.contains(yAgnosticEntry)) return true;

        // Check exact format
        String exactEntry = String.format("%d|%s|%d|%d|%d", worldSeed, structureId, x, y, z);

        return clientBlacklistedLocations.contains(exactEntry);
    }

    private static void saveBlacklistedLocations() {
        if (config != null) {
            config.get("client", "blacklistedLocations", new String[0])
                .set(clientBlacklistedLocations.toArray(new String[0]));
            config.save();
        }
    }

    public static List<String> getBlacklistedLocations() {
        return new ArrayList<>(clientBlacklistedLocations);
    }
}
