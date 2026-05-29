package com.simplestructurescanner.client.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import com.simplestructurescanner.client.render.StructurePreviewRenderer;


/**
 * Simple modal window for the capture preview renderer.
 * The window closes on outside clicks or on escape without affecting the parent screen.
 */
public class GuiCapturePreviewWindow extends Gui {
    private static final int HEADER_HEIGHT = 20;
    private static final int PADDING = 10;

    private final StructurePreviewRenderer previewRenderer;

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    public GuiCapturePreviewWindow(StructurePreviewRenderer previewRenderer) {
        this.previewRenderer = previewRenderer;
    }

    public void show() {
        visible = true;
        calculateLayout();
    }

    public void hide() {
        visible = false;
    }

    public void release() {
        previewRenderer.release();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (!isMouseOver(mouseX, mouseY)) {
            hide();
            return true;
        }

        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();
            return true;
        }

        return false;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        drawRect(windowX - 2, windowY - 2, windowX + windowW + 2, windowY + windowH + 2, 0xFF4F3820);
        drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xE014120F);

        drawGradientRect(windowX, windowY, windowX + windowW, windowY + HEADER_HEIGHT, 0xF0372B18, 0xF01E1811);
        drawRect(windowX, windowY + HEADER_HEIGHT, windowX + windowW, windowY + HEADER_HEIGHT + 1, 0xFFC89254);
        font.drawStringWithShadow(I18n.format("gui.structurescanner.capture.preview.title"), windowX + 8, windowY + 6, 0xFFF5EEE4);

        int previewX = windowX + PADDING;
        int previewY = windowY + HEADER_HEIGHT + PADDING;
        int previewWidth = windowW - PADDING * 2;
        int previewHeight = windowH - HEADER_HEIGHT - PADDING * 2;

        drawRect(previewX - 1, previewY - 1, previewX + previewWidth + 1, previewY + previewHeight + 1, 0xFF333333);
        drawRect(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF1A1A1A);

        if (previewRenderer != null && previewRenderer.getWorld() != null && !previewRenderer.getWorld().renderedBlocks.isEmpty()) {
            previewRenderer.setBackgroundColor(0xFF1A1A1A);
            previewRenderer.render(previewX, previewY, previewWidth, previewHeight);
            return;
        }

        String noPreview = I18n.format("gui.structurescanner.preview.unavailable");
        int textX = previewX + (previewWidth - font.getStringWidth(noPreview)) / 2;
        int textY = previewY + previewHeight / 2 - 4;
        font.drawString(noPreview, textX, textY, 0x888888);
    }

    public void drawTooltips(int mouseX, int mouseY) {
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        int screenW = scaledResolution.getScaledWidth();
        int screenH = scaledResolution.getScaledHeight();
        int maxSize = Math.min(screenW - 40, screenH - 40);

        windowW = maxSize;
        windowH = maxSize;
        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;
    }
}