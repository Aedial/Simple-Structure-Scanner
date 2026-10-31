package com.simplestructurescanner.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.simplestructurescanner.config.SimpleStructureScannerConfig;
import com.simplestructurescanner.structure.LootTableResolver.LootItem;
import com.simplestructurescanner.util.ItemStackKey;


/**
 * Accumulator for cleaned loot variants and their combined drop counts.
 */
public final class LootItemAccumulator {
    private final Map<ItemStackKey, Bucket> buckets = new LinkedHashMap<>();

    public void add(ItemStack stack, int dropCount) {
        if (stack == null || stack.isEmpty() || dropCount <= 0) return;

        add(new LootItem(stack, dropCount));
    }

    public void add(LootItem item) {
        if (item == null || item.stack.isEmpty() || item.dropCount <= 0) return;

        ItemStackKey groupKey = createGroupKey(item.stack);
        Bucket bucket = buckets.computeIfAbsent(groupKey, ignored -> new Bucket(item.stack));
        bucket.add(item);
    }

    public List<LootItem> getItems() {
        List<LootItem> result = new ArrayList<>();
        for (Bucket bucket : buckets.values()) {
            if (bucket.groupedItem != null) result.add(bucket.groupedItem);
            else result.addAll(bucket.variants.values());
        }

        result.sort((first, second) -> Integer.compare(second.dropCount, first.dropCount));
        return result;
    }

    public boolean isGrouped(ItemStack stack) {
        Bucket bucket = buckets.get(createGroupKey(stack));
        return bucket != null && bucket.groupedItem != null;
    }

    public ItemStackKey getDisplayKey(ItemStack stack) {
        return isGrouped(stack) ? createGroupKey(stack) : LootTableResolver.createAggregationKey(stack);
    }

    public ItemStack getDisplayStack(ItemStack stack) {
        Bucket bucket = buckets.get(createGroupKey(stack));
        return bucket != null && bucket.groupedItem != null ? bucket.groupedItem.stack.copy() : stack.copy();
    }

    private static ItemStackKey createGroupKey(ItemStack stack) {
        ItemStack groupStack = new ItemStack(stack.getItem(), 1, stack.getItemDamage());
        return ItemStackKey.ofTransient(groupStack);
    }

    private static final class Bucket {
        private final ItemStack stack;
        private final boolean eligible;
        private final Map<ItemStackKey, LootItem> variants = new LinkedHashMap<>();
        private LootItem groupedItem;

        private Bucket(ItemStack stack) {
            this.stack = stack.copy();
            this.eligible = LootTableResolver.canGroupItem(stack);
        }

        private void add(LootItem item) {
            if (groupedItem != null) {
                groupedItem.dropCount += item.dropCount;
                return;
            }

            // A source that already reached the threshold keeps the combined result grouped
            if (item.grouped) {
                collapse(item.dropCount);
                return;
            }

            ItemStackKey key = ItemStackKey.of(item.stack);
            LootItem existing = variants.get(key);
            if (existing == null) variants.put(key, new LootItem(item.stack, item.dropCount));
            else existing.dropCount += item.dropCount;

            int threshold = Math.max(1, SimpleStructureScannerConfig.client.lootGroupingThreshold);
            if (eligible && variants.size() >= threshold) collapse(0);
        }

        private void collapse(int additionalCount) {
            int dropCount = additionalCount;
            for (LootItem variant : variants.values()) dropCount += variant.dropCount;

            groupedItem = new LootItem(stack, dropCount, true);
            variants.clear();
        }
    }
}
