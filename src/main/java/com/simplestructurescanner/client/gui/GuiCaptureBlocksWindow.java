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
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraftforge.fml.client.config.GuiUtils;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureSummary.BlockSummary;
import com.simplestructurescanner.structure.StructureInfo.BlockEntry;


/**
 * Grid-based block exclusion window for the capture preview.
 */
public class GuiCaptureBlocksWindow {

    private static final int ITEM_SIZE = 18;
    private static final int ITEM_PADDING = 4;
    private static final int ITEM_COUNT_HEIGHT = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 16;

    private final GuiScreen parent;
    private final List<BlockSummary> blockSummaries;
    private final List<BlockEntry> blockEntries = new ArrayList<BlockEntry>();
    private final StructureCaptureExclusions exclusions;

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private int columns;
    private int rows;
    private int gridStartX;
    private int gridStartY;
    private int hoveredIndex = -1;
    private boolean hoveringCount;
    private int countHoverIndex = -1;
    private boolean hoveringFooter;
    private int totalBlocks;

    public GuiCaptureBlocksWindow(GuiScreen parent, List<BlockSummary> blockSummaries,
            StructureCaptureExclusions exclusions) {
        this.parent = parent;
        this.blockSummaries = blockSummaries;
        this.exclusions = exclusions;

        for (BlockSummary blockSummary : blockSummaries) {
            BlockEntry blockEntry = blockSummary.toBlockEntry();
            if (blockEntry == null) continue;

            blockEntries.add(blockEntry);
            totalBlocks += blockEntry.count;
        }
    }

    public void show() {
        visible = true;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hoveredIndex = -1;
        hoveringCount = false;
        countHoverIndex = -1;
        hoveringFooter = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (!isMouseOver(mouseX, mouseY)) {
            hide();
            return true;
        }

        if (hoveredIndex >= 0 && hoveredIndex < blockSummaries.size()) {
            BlockSummary blockSummary = blockSummaries.get(hoveredIndex);
            exclusions.setBlockExcluded(blockSummary.getKey(), !exclusions.isBlockExcluded(blockSummary.getKey()));
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

        return true;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0xFF303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xFF1A1A1A);

        font.drawString(I18n.format("gui.structurescanner.capture.blocks.title"), windowX + 6, windowY + 6, 0xFFFFFF);

        hoveringFooter = false;
        String footer = I18n.format("gui.structurescanner.capture.blocks.footer", blockEntries.size(), getExcludedCount());
        int footerY = windowY + windowH - FOOTER_HEIGHT + 2;
        if (mouseX >= windowX + 6 && mouseX <= windowX + 6 + font.getStringWidth(footer)
                && mouseY >= footerY && mouseY <= footerY + 10) {
            hoveringFooter = true;
        }

        font.drawString(footer, windowX + 6, footerY, hoveringFooter ? 0xFFFFAA : 0xCCCCCC);

        if (blockEntries.isEmpty()) {
            font.drawString(I18n.format("gui.structurescanner.capture.blocks.empty"), gridStartX, gridStartY + 12, 0xFF7777);
            return;
        }

        drawGrid(mouseX, mouseY);
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen;
        int screenWidth = currentScreen != null ? currentScreen.width : mc.displayWidth;
        int screenHeight = currentScreen != null ? currentScreen.height : mc.displayHeight;

        if (hoveredIndex >= 0 && hoveredIndex < blockEntries.size()) {
            BlockEntry entry = blockEntries.get(hoveredIndex);
            List<String> tooltipLines = new ArrayList<String>();

            if (entry.displayStack != null) {
                ITooltipFlag.TooltipFlags tooltipFlag = mc.gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED
                    : ITooltipFlag.TooltipFlags.NORMAL;
                tooltipLines.addAll(entry.displayStack.getTooltip(mc.player, tooltipFlag));
            } else if (entry.displayFluid != null) {
                tooltipLines.add(entry.displayFluid.getLocalizedName());
                tooltipLines.add(I18n.format("gui.structurescanner.blocks.fluidAmount", (long) entry.displayFluid.amount * entry.count));
            } else {
                tooltipLines.add(blockSummaries.get(hoveredIndex).getKey());
            }

            if (exclusions.isBlockExcluded(blockSummaries.get(hoveredIndex).getKey())) {
                tooltipLines.add(I18n.format("gui.structurescanner.capture.excludedTooltip"));
            }

            GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
            return;
        }

        if (hoveringCount && countHoverIndex >= 0 && countHoverIndex < blockEntries.size()) {
            BlockEntry entry = blockEntries.get(countHoverIndex);
            GuiUtils.drawHoveringText(
                Collections.singletonList(I18n.format("gui.structurescanner.blocks.count", entry.count)),
                mouseX,
                mouseY,
                screenWidth,
                screenHeight,
                -1,
                mc.fontRenderer
            );
            return;
        }

        if (hoveringFooter) {
            GuiUtils.drawHoveringText(
                Collections.singletonList(I18n.format("gui.structurescanner.blocks.countTooltip", totalBlocks)),
                mouseX,
                mouseY,
                screenWidth,
                screenHeight,
                -1,
                mc.fontRenderer
            );
        }
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenW = resolution.getScaledWidth();
        int screenH = resolution.getScaledHeight();

        int itemCount = Math.max(1, blockEntries.size());
        columns = Math.max(1, Math.min(16, (int) Math.ceil(Math.sqrt(itemCount))));
        rows = Math.max(1, Math.min(12, (int) Math.ceil((double) itemCount / columns)));

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

    private void drawGrid(int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        float textScale = 0.5f;

        hoveredIndex = -1;
        hoveringCount = false;
        countHoverIndex = -1;

        GlStateManager.pushMatrix();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();

        for (int index = 0; index < blockEntries.size() && index < columns * rows; index++) {
            int column = index % columns;
            int row = index / columns;
            int itemX = gridStartX + ITEM_PADDING + column * (ITEM_SIZE + ITEM_PADDING);
            int itemY = gridStartY + ITEM_PADDING + row * (ITEM_SIZE + ITEM_PADDING + ITEM_COUNT_HEIGHT);

            BlockEntry entry = blockEntries.get(index);
            boolean excluded = exclusions.isBlockExcluded(blockSummaries.get(index).getKey());
            boolean hoveringItem = mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16;
            if (hoveringItem) hoveredIndex = index;

            Gui.drawRect(itemX - 1, itemY - 1, itemX + 17, itemY + 17, hoveringItem ? 0xFF555555 : 0xFF373737);
            GuiBlockEntryRenderer.render(mc, entry, itemX, itemY);
            if (excluded) drawExcludedOverlay(itemX, itemY);

            String countString = entry.formatCount();
            int countWidth = (int) (font.getStringWidth(countString) * textScale);
            int countX = itemX + (16 - countWidth) / 2;
            int countY = itemY + 18;
            boolean hoveringCountText = mouseX >= countX - 1 && mouseX <= countX + countWidth + 1
                && mouseY >= countY && mouseY <= countY + 8;
            if (hoveringCountText) {
                hoveringCount = true;
                countHoverIndex = index;
            }

            GlStateManager.pushMatrix();
            GlStateManager.scale(textScale, textScale, 1.0f);
            font.drawString(countString, (int) (countX / textScale), (int) (countY / textScale),
                excluded ? 0xFFBBBB : (hoveringCountText ? 0xFFFFAA : 0xCCCCCC));
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private void drawExcludedOverlay(int itemX, int itemY) {
        Gui.drawRect(itemX, itemY, itemX + 16, itemY + 16, 0x55FF0000);
        for (int pixel = 0; pixel < 16; pixel++) {
            Gui.drawRect(itemX + pixel, itemY + pixel, itemX + pixel + 1, itemY + pixel + 1, 0xCCFF5555);
            Gui.drawRect(itemX + pixel, itemY + 15 - pixel, itemX + pixel + 1, itemY + 16 - pixel, 0xCCFF5555);
        }
    }

    private int getExcludedCount() {
        int excludedCount = 0;
        for (BlockSummary blockSummary : blockSummaries) {
            if (exclusions.isBlockExcluded(blockSummary.getKey())) excludedCount++;
        }

        return excludedCount;
    }
}