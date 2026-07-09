package com.simplestructurescanner.integration.jei;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;

import com.simplestructurescanner.Tags;


/**
 * Fixed-size JEI category shell for one structure tab.
 */
public class StructureJeiCategory implements IRecipeCategory<StructureJeiRecipe>, IRecipeCategoryWithOverlay {
    /** Shared panel width used by every structure tab. */
    public static final int WIDTH = 150;

    /** Shared panel height used by every structure tab. */
    public static final int HEIGHT = 192;

    private final StructureJeiView view;
    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotDrawable;
    private final String title;

    @Nullable
    private StructureJeiRecipe currentRecipe;

    /**
     * Creates the JEI category wrapper for one logical structure view.
     */
    public StructureJeiCategory(IGuiHelper guiHelper, StructureJeiView view) {
        this.view = view;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = new ItemStackDrawable(getIconStack(view));
        this.slotDrawable = guiHelper.getSlotDrawable();
        this.title = I18n.format(view.getTitleKey());
    }

    @Nonnull
    @Override
    public String getUid() {
        return view.getCategoryUid();
    }

    @Nonnull
    @Override
    public String getTitle() {
        return title;
    }

    @Nonnull
    @Override
    public String getModName() {
        return Tags.MODNAME;
    }

    @Nonnull
    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @Nonnull StructureJeiRecipe recipeWrapper, IIngredients ingredients) {
        this.currentRecipe = recipeWrapper;
        if (view == StructureJeiView.PREVIEW) return;

        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStacks = view == StructureJeiView.BLOCKS ? recipeLayout.getFluidStacks() : null;
        recipeWrapper.bindJeiLayout(itemStacks, fluidStacks, slotDrawable);
        recipeWrapper.syncJeiIngredientLayout();
    }

    @Override
    public void drawOverlay(Minecraft minecraft, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (currentRecipe == null) return;

        currentRecipe.drawOverlay(minecraft, offsetX, offsetY, mouseX, mouseY);
    }

    private static ItemStack getIconStack(StructureJeiView view) {
        if (view == StructureJeiView.PREVIEW) return new ItemStack(Blocks.STRUCTURE_BLOCK);
        if (view == StructureJeiView.BLOCKS) return new ItemStack(Blocks.STONEBRICK);

        return new ItemStack(Blocks.CHEST);
    }

    /** Draws a small item-stack icon without depending on extra JEI helper APIs. */
    private static class ItemStackDrawable implements IDrawable {
        private final ItemStack stack;

        private ItemStackDrawable(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }

        @Override
        public void draw(Minecraft minecraft, int xOffset, int yOffset) {
            GlStateManager.pushMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            minecraft.getRenderItem().renderItemIntoGUI(stack, xOffset, yOffset);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();
        }
    }
}