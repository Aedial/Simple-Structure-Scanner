package com.simplestructurescanner.structure;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
            this.stack = stack;
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

    private static String getItemKey(ItemStack stack) {
        return stack.getItem().getRegistryName() + "@" + stack.getMetadata();
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
