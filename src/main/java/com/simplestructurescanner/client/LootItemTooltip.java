package com.simplestructurescanner.client;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTippedArrow;


/**
 * Shared tooltip text for loot entries that combine NBT variants.
 */
public final class LootItemTooltip {
    private LootItemTooltip() {
    }

    public static void apply(List<String> tooltip, ItemStack stack, boolean grouped) {
        if (!grouped) return;

        Item item = stack.getItem();
        String name = item instanceof ItemPotion || item instanceof ItemTippedArrow
            ? I18n.format(item.getTranslationKey() + ".name") : stack.getDisplayName();
        if (item == Items.SPAWN_EGG) name = I18n.format("gui.structurescanner.loot.spawnEgg");

        tooltip.clear();
        tooltip.add(I18n.format("gui.structurescanner.loot.groupedName", name));
        tooltip.add(I18n.format("gui.structurescanner.loot.groupedTooltip"));
    }
}
