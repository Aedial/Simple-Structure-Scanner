package com.simplestructurescanner.client.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.resources.I18n;

import com.simplestructurescanner.integration.JEIHelper;
import com.simplestructurescanner.structure.StructureInfo;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;


/**
 * Modal popup window that displays structure blocks.
 * Shows a grid of blocks with their counts.
 */
public class GuiBlocksWindow {
    private static final int ITEM_SIZE = 18;
    private static final int ITEM_PADDING = 4;
    private static final int ITEM_COUNT_HEIGHT = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;

    private final GuiScreen parent;
    private final ResourceLocation structureId;
    private final StructureInfo structureInfo;

    private boolean visible = false;
    private boolean hiddenForNavigation = false;
    private int windowX, windowY, windowW, windowH;

    // Item layout
    private int columns, rows;
    private int gridStartX, gridStartY;

    // Hover state
    private int hoveredItemIndex = -1;
    private boolean hoveringCount = false;
    private int countHoverIndex = -1;
    private boolean hoveringTotal = false;

    // Block data
    private List<BlockEntry> blocks;
    private int totalBlocks = 0;

    public GuiBlocksWindow(GuiScreen parent, ResourceLocation structureId, StructureInfo structureInfo) {
        this.parent = parent;
        this.structureId = structureId;
        this.structureInfo = structureInfo;
        this.blocks = structureInfo.getBlocks();
        this.totalBlocks = blocks.stream().mapToInt(b -> b.count).sum();
    }

    public void show() {
        visible = true;
        hiddenForNavigation = false;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hiddenForNavigation = false;
        hoveredItemIndex = -1;
        hoveringCount = false;
        countHoverIndex = -1;
        hoveringTotal = false;
    }

    private void hideForNavigation() {
        visible = false;
        hiddenForNavigation = true;
        hoveredItemIndex = -1;
        hoveringCount = false;
        countHoverIndex = -1;
        hoveringTotal = false;
    }

    public boolean restoreIfHiddenForNavigation() {
        if (hiddenForNavigation) {
            visible = true;
            hiddenForNavigation = false;
            calculateLayout();

            return true;
        }

        return false;
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

        float screenRatio = (float) screenW / screenH;

        int itemCount = blocks.size();

        if (itemCount > 0) {
            int itemsPerRow = Math.max(1, (int) Math.ceil(Math.sqrt(itemCount * screenRatio)));
            int rowCount = (int) Math.ceil((double) itemCount / itemsPerRow);

            itemsPerRow = Math.max(1, Math.min(itemsPerRow, 16));
            rowCount = Math.max(1, Math.min(rowCount, 12));

            columns = itemsPerRow;
            rows = rowCount;
        } else {
            columns = 4;
            rows = 2;
        }

        int gridW = columns * ITEM_SIZE + (columns + 1) * ITEM_PADDING;
        int gridH = rows * (ITEM_SIZE + ITEM_COUNT_HEIGHT) + (rows + 1) * ITEM_PADDING;

        windowW = Math.max(gridW + 20, 180);
        windowH = HEADER_HEIGHT + gridH + FOOTER_HEIGHT + 10;

        windowW = Math.min(windowW, screenW - 40);
        windowH = Math.min(windowH, screenH - 40);

        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        gridStartX = windowX + (windowW - gridW) / 2;
        gridStartY = windowY + HEADER_HEIGHT + 5;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (mouseX < windowX || mouseX > windowX + windowW ||
            mouseY < windowY || mouseY > windowY + windowH) {
            hide();

            return true;
        }

        if (hoveredItemIndex >= 0 && hoveredItemIndex < blocks.size()) {
            BlockEntry entry = blocks.get(hoveredItemIndex);

            if (entry.displayStack != null) {
                if (mouseButton == 0) {
                    JEIHelper.showItemRecipes(entry.displayStack);
                } else if (mouseButton == 1) {
                    JEIHelper.showItemUses(entry.displayStack);
                }
            }
        }

        return true;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();

            return true;
        }

        if (hoveredItemIndex >= 0 && hoveredItemIndex < blocks.size()) {
            BlockEntry entry = blocks.get(hoveredItemIndex);

            if (entry.displayStack != null) {
                if (keyCode == Keyboard.KEY_R) {
                    JEIHelper.showItemRecipes(entry.displayStack);

                    return true;
                } else if (keyCode == Keyboard.KEY_U) {
                    JEIHelper.showItemUses(entry.displayStack);

                    return true;
                }
            }
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
        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0x80303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0x801A1A1A);

        // Draw header
        String title = I18n.format("gui.structurescanner.blocks.title", structureInfo.getDisplayName());
        String elidedTitle = font.trimStringToWidth(title, windowW - 16);
        if (!elidedTitle.equals(title)) elidedTitle += "…";
        font.drawString(elidedTitle, windowX + 6, windowY + 6, 0xFFFFFF);

        // Draw footer with total count
        hoveringTotal = false;
        int footerY = windowY + windowH - FOOTER_HEIGHT + 2;
        String footer = I18n.format("gui.structurescanner.blocks.count", blocks.size());

        if (mouseX >= windowX + 6 && mouseX <= windowX + 6 + font.getStringWidth(footer) &&
            mouseY >= footerY && mouseY <= footerY + 10) {
            hoveringTotal = true;
        }

        font.drawString(footer, windowX + 6, footerY, hoveringTotal ? 0xFFFFAA : 0xCCCCCC);

        // Draw content
        if (blocks.isEmpty()) {
            String msg = I18n.format("gui.structurescanner.blocks.noBlocks");
            int textW = font.getStringWidth(msg);
            font.drawString(msg, windowX + (windowW - textW) / 2, gridStartY + 20, 0xFF6666);
        } else {
            drawItemGrid(mouseX, mouseY);
        }
    }

    private void drawItemGrid(int mouseX, int mouseY) {
        float textScale = 0.5f;
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        hoveredItemIndex = -1;
        hoveringCount = false;
        countHoverIndex = -1;

        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();

        for (int i = 0; i < blocks.size() && i < columns * rows; i++) {
            int col = i % columns;
            int row = i / columns;
            int itemX = gridStartX + ITEM_PADDING + col * (ITEM_SIZE + ITEM_PADDING);
            int itemY = gridStartY + ITEM_PADDING + row * (ITEM_SIZE + ITEM_PADDING + ITEM_COUNT_HEIGHT);

            BlockEntry entry = blocks.get(i);

            // Draw slot background
            Gui.drawRect(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF373737);

            // Check hover
            boolean hoveringItem = mouseX >= itemX && mouseX < itemX + 16 &&
                                   mouseY >= itemY && mouseY < itemY + 16;
            if (hoveringItem) {
                hoveredItemIndex = i;
                Gui.drawRect(itemX - 1, itemY - 1, itemX + 17, itemY + 17, 0xFF555555);
            }

            // Render item
            if (entry.displayStack != null) mc.getRenderItem().renderItemIntoGUI(entry.displayStack, itemX, itemY);

            // Draw count below item
            String countStr = entry.formatCount();
            int countW = (int) (font.getStringWidth(countStr) * textScale);
            int countX = itemX + (16 - countW) / 2;
            int countY = itemY + 18;

            boolean hoveringCountText = mouseX >= countX - 1 && mouseX <= countX + countW + 1 &&
                                        mouseY >= countY && mouseY <= countY + 8;
            if (hoveringCountText) {
                hoveringCount = true;
                countHoverIndex = i;
            }

            GlStateManager.pushMatrix();
            GlStateManager.scale(textScale, textScale, 1.0f);
            int countColor = hoveringCountText ? 0xFFFFAA : 0xCCCCCC;
            font.drawString(countStr, (int) (countX / textScale), (int) (countY / textScale), countColor);
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();

        // Item tooltip
        if (hoveredItemIndex >= 0 && hoveredItemIndex < blocks.size()) {
            BlockEntry entry = blocks.get(hoveredItemIndex);

            if (entry.displayStack != null) {
                ITooltipFlag.TooltipFlags tooltipFlag = mc.gameSettings.advancedItemTooltips ?
                    ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
                List<String> tooltip = entry.displayStack.getTooltip(mc.player, tooltipFlag);

                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, 500);
                drawHoveringText(tooltip, mouseX, mouseY, mc.fontRenderer);
                GlStateManager.popMatrix();
            }
        }

        // Count tooltip
        if (hoveringCount && countHoverIndex >= 0 && countHoverIndex < blocks.size()) {
            BlockEntry entry = blocks.get(countHoverIndex);
            String tooltip = I18n.format("gui.structurescanner.blocks.count", entry.count);

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 500);
            drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY, mc.fontRenderer);
            GlStateManager.popMatrix();
        }

        // Total count tooltip
        if (hoveringTotal) {
            String tooltip = I18n.format("gui.structurescanner.blocks.countTooltip", totalBlocks);

            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 500);
            drawHoveringText(Collections.singletonList(tooltip), mouseX, mouseY, mc.fontRenderer);
            GlStateManager.popMatrix();
        }
    }

    private void drawHoveringText(List<String> textLines, int x, int y, FontRenderer font) {
        if (textLines.isEmpty()) return;

        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        int maxAllowedWidth = screenW - x - 8;
        List<String> wrappedLines = new ArrayList<>();

        for (String line : textLines) {
            int lineWidth = font.getStringWidth(line);
            if (lineWidth > maxAllowedWidth) {
                wrappedLines.addAll(font.listFormattedStringToWidth(line, maxAllowedWidth));
            } else {
                wrappedLines.add(line);
            }
        }

        int maxWidth = 0;
        for (String s : wrappedLines) {
            int w = font.getStringWidth(s);
            if (w > maxWidth) maxWidth = w;
        }

        int posX = x + 12;
        int posY = y - 12;
        int height = 8;
        if (wrappedLines.size() > 1) height += 2 + (wrappedLines.size() - 1) * 10;

        if (posX + maxWidth > screenW) posX -= 28 + maxWidth;
        if (posY + height + 6 > screenH) posY = screenH - height - 6;

        int bgColor = 0xF0100010;
        int borderColorStart = 0x505000FF;
        int borderColorEnd = 0x5028007F;

        Gui.drawRect(posX - 3, posY - 4, posX + maxWidth + 3, posY - 3, bgColor);
        Gui.drawRect(posX - 3, posY + height + 3, posX + maxWidth + 3, posY + height + 4, bgColor);
        Gui.drawRect(posX - 3, posY - 3, posX + maxWidth + 3, posY + height + 3, bgColor);
        Gui.drawRect(posX - 4, posY - 3, posX - 3, posY + height + 3, bgColor);
        Gui.drawRect(posX + maxWidth + 3, posY - 3, posX + maxWidth + 4, posY + height + 3, bgColor);

        Gui.drawRect(posX - 3, posY - 3 + 1, posX - 3 + 1, posY + height + 3 - 1, borderColorStart);
        Gui.drawRect(posX + maxWidth + 2, posY - 3 + 1, posX + maxWidth + 3, posY + height + 3 - 1, borderColorStart);
        Gui.drawRect(posX - 3, posY - 3, posX + maxWidth + 3, posY - 3 + 1, borderColorStart);
        Gui.drawRect(posX - 3, posY + height + 2, posX + maxWidth + 3, posY + height + 3, borderColorEnd);

        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            font.drawStringWithShadow(line, posX, posY, -1);
            posY += (i == 0) ? 12 : 10;
        }

        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
    }
}
