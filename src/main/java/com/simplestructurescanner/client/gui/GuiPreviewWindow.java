package com.simplestructurescanner.client.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import com.simplestructurescanner.client.render.StructurePreviewRenderer;
import com.simplestructurescanner.structure.StructureInfo;


/**
 * Modal popup window that displays a large 3D structure preview.
 * Shows the structure in a centered square window with rotation.
 */
public class GuiPreviewWindow {
    private static final int HEADER_HEIGHT = 20;
    private static final int PADDING = 10;

    private final GuiScreen parent;
    private final ResourceLocation structureId;
    private final StructureInfo structureInfo;
    private final StructurePreviewRenderer previewRenderer;

    private boolean visible = false;
    private boolean hiddenForNavigation = false;
    private int windowX, windowY, windowW, windowH;

    public GuiPreviewWindow(GuiScreen parent, ResourceLocation structureId, StructureInfo structureInfo, StructurePreviewRenderer previewRenderer) {
        this.parent = parent;
        this.structureId = structureId;
        this.structureInfo = structureInfo;
        this.previewRenderer = previewRenderer;
    }

    public void show() {
        visible = true;
        hiddenForNavigation = false;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hiddenForNavigation = false;
    }

    public boolean restoreIfHiddenForNavigation() {
        if (!hiddenForNavigation) return false;

        visible = true;
        hiddenForNavigation = false;
        calculateLayout();

        return true;
    }

    public boolean isHiddenForNavigation() {
        return hiddenForNavigation;
    }

    public boolean isVisible() {
        return visible;
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        // Make a large square window that fits the screen
        int maxSize = Math.min(screenW, screenH) - 40;
        windowW = maxSize;
        windowH = maxSize;

        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        // Click outside closes
        if (mouseX < windowX || mouseX > windowX + windowW ||
            mouseY < windowY || mouseY > windowY + windowH) {
            hide();

            return true;
        }

        // Click inside just consumes the event
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

        return mouseX >= windowX && mouseX <= windowX + windowW &&
               mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        // Draw semi-transparent overlay
        Gui.drawRect(0, 0, mc.displayWidth, mc.displayHeight, 0x80000000);

        // Draw window background
        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0xFF303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xFF1A1A1A);

        // Draw header
        String title = I18n.format("gui.structurescanner.preview.title", structureInfo.getDisplayName());
        String elidedTitle = font.trimStringToWidth(title, windowW - 16);
        if (!elidedTitle.equals(title)) elidedTitle += "…";
        font.drawStringWithShadow(elidedTitle, windowX + 8, windowY + 6, 0xFFFFFF);

        // Draw header separator line
        Gui.drawRect(windowX, windowY + HEADER_HEIGHT - 1, windowX + windowW, windowY + HEADER_HEIGHT, 0xFF404040);

        // Draw preview area background
        int previewX = windowX + PADDING;
        int previewY = windowY + HEADER_HEIGHT + PADDING;
        int previewSize = windowW - PADDING * 2;
        int previewHeight = windowH - HEADER_HEIGHT - PADDING * 2;

        Gui.drawRect(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewHeight + 1, 0xFF333333);
        Gui.drawRect(previewX, previewY, previewX + previewSize, previewY + previewHeight, 0xFF1A1A1A);

        // Draw the structure preview
        if (previewRenderer != null && previewRenderer.getWorld() != null && !previewRenderer.getWorld().renderedBlocks.isEmpty()) {
            previewRenderer.setBackgroundColor(0xFF1A1A1A);
            previewRenderer.render(previewX, previewY, previewSize, previewHeight);
        } else {
            // No preview available
            String noPreview = I18n.format("gui.structurescanner.preview.unavailable");
            int textWidth = font.getStringWidth(noPreview);
            font.drawString(noPreview, previewX + (previewSize - textWidth) / 2, previewY + previewHeight / 2 - 4, 0x888888);
        }
    }

    public void drawTooltips(int mouseX, int mouseY) {
        // No tooltips needed for the preview window
    }
}
