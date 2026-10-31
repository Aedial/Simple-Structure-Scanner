package com.simplestructurescanner.structure;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.config.SimpleStructureScannerConfig;
import com.simplestructurescanner.util.ItemStackKey;


/**
 * Utility class to resolve loot table contents.
 * Simulates loot generation to get accurate item drops with mod support.
 */
public class LootTableResolver {
    private static final int SIMULATION_COUNT = 1000;
    private static final Random RANDOM = new Random();

    private static Field lootTablePoolsField;
    private static Field lootPoolEntriesField;
    private static Field lootEntryItemField;
    private static boolean reflectionInitialized = false;
    private static boolean reflectionFailed = false;

    // Wizardry mod spell book/scroll detection cache
    private static boolean wizardryChecked = false;
    private static boolean wizardryLoaded = false;
    private static Class<?> spellBookClass = null;
    private static Class<?> spellScrollClass = null;

    // Corail Tombstone scroll_buff detection cache
    private static boolean tombstoneChecked = false;
    private static boolean tombstoneLoaded = false;
    private static Class<?> scrollBuffClass = null;

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            lootTablePoolsField = LootTable.class.getDeclaredField("pools");
            lootTablePoolsField.setAccessible(true);

            lootPoolEntriesField = LootPool.class.getDeclaredField("lootEntries");
            lootPoolEntriesField.setAccessible(true);

            lootEntryItemField = LootEntryItem.class.getDeclaredField("item");
            lootEntryItemField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            try {
                lootTablePoolsField = LootTable.class.getDeclaredField("field_186466_c");
                lootTablePoolsField.setAccessible(true);

                lootPoolEntriesField = LootPool.class.getDeclaredField("field_186453_a");
                lootPoolEntriesField.setAccessible(true);

                lootEntryItemField = LootEntryItem.class.getDeclaredField("field_186369_a");
                lootEntryItemField.setAccessible(true);
            } catch (NoSuchFieldException e2) {
                SimpleStructureScanner.LOGGER.warn("Failed to access loot table fields via reflection: {}", e2.getMessage());
                reflectionFailed = true;
            }
        }
    }

    /**
     * Represents an aggregated loot item with drop count/rate.
     */
    public static class LootItem {
        public final ItemStack stack;
        public final boolean grouped;
        public int dropCount;

        public LootItem(ItemStack stack, int dropCount) {
            this(stack, dropCount, false);
        }

        public LootItem(ItemStack stack, int dropCount, boolean grouped) {
            // Keep a normalized copy for cleaner display
            this.stack = normalizeForDisplay(stack);
            this.grouped = grouped;
            this.dropCount = dropCount;
            if (grouped) this.stack.setTagCompound(null);
        }

        public String formatDropRate(int simulationCount) {
            return LootTableResolver.formatDropRate(dropCount, simulationCount);
        }
    }

    public static String formatDropRate(int dropCount, int simulationCount) {
        double rate = (double) dropCount / simulationCount;
        double percent = rate * 100.0;

        if (rate >= 1.0) return String.format("%.1f", rate);

        if (percent >= 10.0) return String.format("%.0f%%", percent);

        if (percent >= 0.1) return String.format("%.1f%%", percent);

        if (percent >= 0.01) return String.format("%.2f%%", percent);

        if (percent > 0.0) return "<0.01%";

        return "0%";
    }

    /**
     * Resolve loot table items by simulating loot generation.
     * This properly fires events for mod compatibility.
     *
     * @param world The world (must be WorldServer for proper simulation)
     * @param lootTableId The loot table to resolve
     * @param player Optional player for context (can be null)
     * @return List of aggregated loot items with drop counts
     */
    public static List<LootItem> resolveLootTableWithSimulation(World world, ResourceLocation lootTableId, EntityPlayer player) {
        LootItemAccumulator items = new LootItemAccumulator();

        if (!(world instanceof WorldServer)) return resolveLootTableFallback(world, lootTableId);

        WorldServer worldServer = (WorldServer) world;
        LootTableManager manager = worldServer.getLootTableManager();
        LootTable table = manager.getLootTableFromLocation(lootTableId);

        if (table == LootTable.EMPTY_LOOT_TABLE) return new ArrayList<>();

        for (int i = 0; i < SIMULATION_COUNT; i++) {
            LootContext.Builder builder = new LootContext.Builder(worldServer);

            if (player != null) builder.withPlayer(player);

            builder.withLuck(0);
            LootContext context = builder.build();

            List<ItemStack> drops = table.generateLootForPools(RANDOM, context);

            for (ItemStack stack : drops) {
                if (stack.isEmpty()) continue;

                items.add(stack, stack.getCount());
            }
        }

        return items.getItems();
    }

    /**
     * Fallback method using reflection when simulation isn't available.
     * This extracts items directly from loot table entries but won't fire events.
     */
    private static List<LootItem> resolveLootTableFallback(World world, ResourceLocation lootTableId) {
        initReflection();

        List<LootItem> items = new ArrayList<>();

        if (reflectionFailed || world == null) return items;

        try {
            LootTableManager manager = world.getLootTableManager();
            LootTable table = manager.getLootTableFromLocation(lootTableId);

            if (table == LootTable.EMPTY_LOOT_TABLE) return items;

            Map<ItemStackKey, LootItem> itemMap = new LinkedHashMap<>();
            List<LootPool> pools = getPools(table);

            for (LootPool pool : pools) {
                List<LootEntry> entries = getEntries(pool);

                for (LootEntry entry : entries) {
                    if (!(entry instanceof LootEntryItem)) continue;

                    Item item = getItem((LootEntryItem) entry);
                    if (item == null) continue;

                    ItemStack stack = new ItemStack(item);
                    ItemStackKey key = getItemKey(stack);

                    if (!itemMap.containsKey(key)) itemMap.put(key, new LootItem(stack, 1));
                }
            }

            items.addAll(itemMap.values());
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.debug("Error resolving loot table {}: {}", lootTableId, e.getMessage());
        }

        return items;
    }

    /**
     * Get a normalized item key for aggregation purposes.
     * Compare item, normalized metadata, and NBT left after configured cleanup.
     */
    private static ItemStackKey getItemKey(ItemStack stack) {
        return ItemStackKey.ofTransient(normalizeForDisplay(stack));
    }

    /**
     * Create the aggregation key used to merge equivalent loot results.
     */
    public static ItemStackKey createAggregationKey(ItemStack stack) {
        return getItemKey(stack);
    }

    public static boolean canGroupItem(ItemStack stack) {
        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null) return false;

        String id = itemId.toString();
        String subtype = id + "@" + stack.getMetadata();
        for (String entry : SimpleStructureScannerConfig.client.lootGroupingItems) {
            String selector = entry.trim();
            if (selector.equals(id) || selector.equals(subtype)) return true;
        }

        return false;
    }

    /**
     * Check if an item should have its metadata stripped for grouping.
     * Includes configured item IDs, Wizardry spell books/scrolls, and Tombstone scroll_buff.
     */
    private static boolean isMetadataStrippedItem(Item item) {
        if (isWizardrySpellItem(item) || isTombstoneScrollBuff(item)) return true;

        ResourceLocation itemId = item.getRegistryName();
        if (itemId == null) return false;

        for (String entry : SimpleStructureScannerConfig.client.lootMetadataIgnoredItems) {
            if (itemId.toString().equals(entry.trim())) return true;
        }

        return false;
    }

    /**
     * Check if an item is a Wizardry mod spell book or scroll.
     * These have per-spell metadata that should be stripped for display grouping.
     */
    private static boolean isWizardrySpellItem(Item item) {
        if (!wizardryChecked) {
            wizardryChecked = true;
            wizardryLoaded = Loader.isModLoaded("ebwizardry");

            if (wizardryLoaded) {
                try {
                    spellBookClass = Class.forName("electroblob.wizardry.item.ItemSpellBook");
                } catch (ClassNotFoundException e) {
                    SimpleStructureScanner.LOGGER.debug("Wizardry mod detected but ItemSpellBook class not found");
                }

                try {
                    spellScrollClass = Class.forName("electroblob.wizardry.item.ItemScroll");
                } catch (ClassNotFoundException e) {
                    SimpleStructureScanner.LOGGER.debug("Wizardry mod detected but ItemScroll class not found");
                }
            }
        }

        if (!wizardryLoaded) return false;
        if (spellBookClass != null && spellBookClass.isInstance(item)) return true;
        if (spellScrollClass != null && spellScrollClass.isInstance(item)) return true;

        return false;
    }

    /**
     * Check if an item is a Corail Tombstone scroll_buff.
     * These have per-buff metadata that should be stripped for display grouping.
     */
    private static boolean isTombstoneScrollBuff(Item item) {
        if (!tombstoneChecked) {
            tombstoneChecked = true;
            tombstoneLoaded = Loader.isModLoaded("tombstone");

            if (tombstoneLoaded) {
                try {
                    scrollBuffClass = Class.forName("ovh.corail.tombstone.item.ItemScrollBuff");
                } catch (ClassNotFoundException e) {
                    SimpleStructureScanner.LOGGER.debug("Tombstone mod detected but ItemScrollBuff class not found");
                }
            }
        }

        if (!tombstoneLoaded) return false;
        if (scrollBuffClass != null && scrollBuffClass.isInstance(item)) return true;

        return false;
    }

    /**
     * Create a normalized ItemStack for display.
     * Strips configured NBT tags and normalizes metadata before comparing loot variants.
     */
    public static ItemStack normalizeForDisplay(ItemStack stack) {
        ItemStack normalized = stack.copy();
        Item item = normalized.getItem();

        // Strip specific NBT tags that cause noise
        if (normalized.hasTagCompound()) {
            // Enchanted books use the configured StoredEnchantments rule
            List<String> keysToRemove = new ArrayList<>();
            for (String entry : SimpleStructureScannerConfig.client.lootNbtTagsToStrip) {
                String tag = entry.trim();
                if (tag.isEmpty()) continue;

                if (tag.endsWith("*")) {
                    String prefix = tag.substring(0, tag.length() - 1);
                    for (String key : normalized.getTagCompound().getKeySet()) {
                        if (key.startsWith(prefix)) keysToRemove.add(key);
                    }
                } else {
                    keysToRemove.add(tag);
                }
            }

            for (String key : keysToRemove) normalized.getTagCompound().removeTag(key);

            // Clean up empty compound
            if (normalized.getTagCompound().isEmpty()) normalized.setTagCompound(null);
        }

        // Items with durability ignore damage values, including unbreakable equipment
        if (normalized.getMaxDamage() > 0) normalized.setItemDamage(0);

        // Wizardry spell books/scrolls and Tombstone scroll_buff ignore per-spell metadata
        // Reset metadata on configured items and spell/buff variants
        if (isMetadataStrippedItem(item)) normalized.setItemDamage(0);

        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static List<LootPool> getPools(LootTable table) {
        try {
            return (List<LootPool>) lootTablePoolsField.get(table);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LootEntry> getEntries(LootPool pool) {
        try {
            return (List<LootEntry>) lootPoolEntriesField.get(pool);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Item getItem(LootEntryItem entry) {
        try {
            return (Item) lootEntryItemField.get(entry);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if proper loot simulation is available.
     */
    public static boolean isSimulationAvailable(World world) {
        return world instanceof WorldServer;
    }

    /**
     * Get the simulation count used for drop rate calculation.
     */
    public static int getSimulationCount() {
        return SIMULATION_COUNT;
    }

    /**
     * Check if loot table resolution is available.
     */
    public static boolean isAvailable() {
        return !reflectionFailed;
    }
}
