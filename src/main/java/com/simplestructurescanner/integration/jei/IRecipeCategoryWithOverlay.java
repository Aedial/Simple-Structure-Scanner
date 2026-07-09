package com.simplestructurescanner.integration.jei;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Marker interface for JEI recipe categories that need to draw overlays after JEI renders the items.
 */
@SideOnly(Side.CLIENT)
public interface IRecipeCategoryWithOverlay {

    /**
     * Draws overlays after JEI renders the category's ingredients.
     */
    void drawOverlay(Minecraft minecraft, int offsetX, int offsetY, int mouseX, int mouseY);
}