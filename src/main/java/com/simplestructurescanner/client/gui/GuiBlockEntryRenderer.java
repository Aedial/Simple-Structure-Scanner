package com.simplestructurescanner.client.gui;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import com.simplestructurescanner.structure.StructureInfo.BlockEntry;


public final class GuiBlockEntryRenderer {
    private static final int ITEM_RENDER_SIZE = 16;

    private GuiBlockEntryRenderer() {}

    public static void render(Minecraft mc, BlockEntry entry, int itemX, int itemY) {
        if (entry.displayStack != null) {
            mc.getRenderItem().renderItemIntoGUI(entry.displayStack, itemX, itemY);

            return;
        }

        if (entry.displayFluid == null || renderFluidIntoGui(mc, entry.displayFluid, itemX, itemY)) return;

        // Fall back to a filled container when the fluid sprite cannot be rendered directly.
        ItemStack filledBucket = FluidUtil.getFilledBucket(entry.displayFluid);
        if (!filledBucket.isEmpty()) mc.getRenderItem().renderItemIntoGUI(filledBucket, itemX, itemY);
    }

    private static boolean renderFluidIntoGui(Minecraft mc, FluidStack fluidStack, int itemX, int itemY) {
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) return false;

        ResourceLocation stillTexture = fluid.getStill();
        if (stillTexture == null) return false;

        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(stillTexture.toString());

        int color = fluid.getColor(fluidStack);
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        if (alpha <= 0.0F) alpha = 1.0F;

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(red, green, blue, alpha);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        drawTexturedRect(itemX, itemY, sprite, ITEM_RENDER_SIZE, ITEM_RENDER_SIZE);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        RenderHelper.enableGUIStandardItemLighting();

        return true;
    }

    private static void drawTexturedRect(int x, int y, TextureAtlasSprite sprite, int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0.0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y + height, 0.0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y, 0.0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(x, y, 0.0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();

        tessellator.draw();
    }
}