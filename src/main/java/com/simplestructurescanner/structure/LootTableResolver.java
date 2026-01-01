package com.simplestructurescanner.structure;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
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

    /**
     * NBT tags to strip from items for cleaner display.
     * These are tags that cause visual noise or grouping issues but aren't essential for item identity.
     */
    private static final String[] NBT_TAGS_TO_STRIP = {
        // Enchantments
        "ench",                 // Regular enchantments
        "StoredEnchantments",   // Enchanted books
        "RepairCost",           // Anvil repair cost

        // Ender IO
        "eio.yourface",         // EIO addon data

        // Thaumcraft
        "Aspects",              // Vis crystal aspects

        // Corail Tombstone
        "stored_xp",            // XP stored in items
        "dead_pet",             // Pet storage

        // General
        "Damage",               // Durability damage
        "display",              // Custom names/lore
    };

    /**
     * NBT tag prefixes to strip from items for cleaner display.
     * These are prefixes of tags that cause visual noise but aren't essential for item identity.
     */
    private static final String[] NBT_TAGS_TO_STRIP_STARTING_WITH = {
        // Ender IO
        "enderio.darksteel.upgrade.",   // EIO upgrade
    };

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
        public int dropCount;

        public LootItem(ItemStack stack, int dropCount) {
            // Store a normalized copy for cleaner display
            this.stack = normalizeForDisplay(stack);
            this.dropCount = dropCount;
        }

        public String formatDropRate(int simulationCount) {
            double rate = (double) dropCount / simulationCount;

            if (rate >= 1.0) return String.format("%.1f", rate);

            return String.format("%.0f%%", rate * 100);
        }
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
        Map<String, LootItem> itemMap = new HashMap<>();

        if (world == null || !(world instanceof WorldServer)) return resolveLootTableFallback(world, lootTableId);

        WorldServer worldServer = (WorldServer) world;
        LootTableManager manager = worldServer.getLootTableManager();
        LootTable table = manager.getLootTableFromLocation(lootTableId);

        if (table == null || table == LootTable.EMPTY_LOOT_TABLE) return new ArrayList<>();

        for (int i = 0; i < SIMULATION_COUNT; i++) {
            LootContext.Builder builder = new LootContext.Builder(worldServer);

            if (player != null) builder.withPlayer(player);

            builder.withLuck(0);
            LootContext context = builder.build();

            List<ItemStack> drops = table.generateLootForPools(RANDOM, context);

            for (ItemStack stack : drops) {
                if (stack.isEmpty()) continue;

                String key = getItemKey(stack);
                LootItem existing = itemMap.get(key);

                if (existing != null) {
                    existing.dropCount += stack.getCount();
                } else {
                    itemMap.put(key, new LootItem(stack.copy(), stack.getCount()));
                }
            }
        }

        List<LootItem> result = new ArrayList<>(itemMap.values());
        result.sort((a, b) -> Integer.compare(b.dropCount, a.dropCount));

        return result;
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

            if (table == null || table == LootTable.EMPTY_LOOT_TABLE) return items;

            Map<String, LootItem> itemMap = new HashMap<>();
            List<LootPool> pools = getPools(table);

            for (LootPool pool : pools) {
                List<LootEntry> entries = getEntries(pool);

                for (LootEntry entry : entries) {
                    if (!(entry instanceof LootEntryItem)) continue;

                    Item item = getItem((LootEntryItem) entry);
                    if (item == null) continue;

                    ItemStack stack = new ItemStack(item);
                    String key = getItemKey(stack);

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
     * This unifies items by removing enchantments, damage values (for tools), and spell book specifics.
     */
    private static String getItemKey(ItemStack stack) {
        Item item = stack.getItem();
        int meta = stack.getMetadata();

        // Enchanted books - treat all as the same item
        if (item == Items.ENCHANTED_BOOK) return item.getRegistryName().toString() + "@enchanted";

        // Items with durability - ignore damage value (metadata)
        if (stack.isItemStackDamageable()) return item.getRegistryName().toString() + "@0";

        // Wizardry spell books/scrolls and Tombstone scroll_buff - ignore per-spell metadata
        if (isMetadataStrippedItem(item)) return item.getRegistryName().toString() + "@0";

        return item.getRegistryName() + "@" + meta;
    }

    /**
     * Check if an item should have its metadata stripped for grouping.
     * This includes Wizardry spell books/scrolls and Tombstone scroll_buff.
     */
    private static boolean isMetadataStrippedItem(Item item) {
        return isWizardrySpellItem(item) || isTombstoneScrollBuff(item);
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
     * Strips specific NBT tags that cause noise while preserving item identity.
     */
    public static ItemStack normalizeForDisplay(ItemStack stack) {
        ItemStack normalized = stack.copy();
        Item item = normalized.getItem();

        // Strip specific NBT tags that cause noise
        if (normalized.hasTagCompound()) {
            for (String tag : NBT_TAGS_TO_STRIP) normalized.getTagCompound().removeTag(tag);

            for (String tagStart : NBT_TAGS_TO_STRIP_STARTING_WITH) {
                List<String> keysToRemove = new ArrayList<>();
                for (String key : normalized.getTagCompound().getKeySet()) {
                    if (key.startsWith(tagStart)) keysToRemove.add(key);
                }

                for (String key : keysToRemove) normalized.getTagCompound().removeTag(key);
            }

            // Clean up empty compound
            if (normalized.getTagCompound().isEmpty()) normalized.setTagCompound(null);
        }

        // Reset damage/metadata on damageable items
        if (normalized.isItemStackDamageable()) normalized.setItemDamage(0);

        // Reset metadata on items that use it for spell/buff variants
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
