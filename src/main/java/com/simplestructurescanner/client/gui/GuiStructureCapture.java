package com.simplestructurescanner.client.gui;

import java.io.IOException;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureSummary;
import com.simplestructurescanner.client.capture.StructureCaptureClientController;
import com.simplestructurescanner.network.NetworkHandler;
import com.simplestructurescanner.network.PacketRequestStructureCaptureSave;


/**
 * Main capture preview screen opened after the third click with the ruler item.
 */
public class GuiStructureCapture extends GuiScreen {

    private static final int BUTTON_BLOCKS = 1;
    private static final int BUTTON_ENTITIES = 2;
    private static final int BUTTON_LOOT = 3;
    private static final int BUTTON_SAVE = 4;
    private static final int BUTTON_CANCEL = 5;

    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 28;
    private static final int SIZE_BAND_HEIGHT = 22;
    private static final int CORNER_CARD_HEIGHT = 38;
    private static final int SECTION_CARD_HEIGHT = 72;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int CARD_GAP = 8;

    private final StructureCaptureSummary summary;
    private final BlockPos firstCorner;
    private final BlockPos secondCorner;
    private final StructureCaptureExclusions exclusions = new StructureCaptureExclusions();

    private GuiCaptureBlocksWindow blocksWindow;
    private GuiCaptureEntitiesWindow entitiesWindow;
    private GuiCaptureLootWindow lootWindow;
    private boolean saveRequested;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public GuiStructureCapture(StructureCaptureSummary summary, BlockPos firstCorner, BlockPos secondCorner) {
        this.summary = summary;
        this.firstCorner = firstCorner;
        this.secondCorner = secondCorner;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        calculateLayout();

        int statsButtonY = getStatsY() + SECTION_CARD_HEIGHT - 22;
        int statsButtonWidth = getStatCardWidth() - 12;
        int actionButtonWidth = (panelWidth - PANEL_PADDING * 3) / 2;
        int actionButtonY = getActionY();

        // TODO: get proper constants for these values
        // TODO: refactoe this mess

        buttonList.add(new CaptureButton(
            BUTTON_BLOCKS,
            getStatCardX(0) + 6,
            statsButtonY,
            statsButtonWidth,
            16,
            "",
            0xC02B3244,
            0xE03B4660,
            0xFF5E6C90,
            0xFF8CA3D9
        ));
        buttonList.add(new CaptureButton(
            BUTTON_ENTITIES,
            getStatCardX(1) + 6,
            statsButtonY,
            statsButtonWidth,
            16,
            "",
            0xC0443929,
            0xE05D4D35,
            0xFF8D6D3B,
            0xFFD7B46A
        ));
        buttonList.add(new CaptureButton(
            BUTTON_LOOT,
            getStatCardX(2) + 6,
            statsButtonY,
            statsButtonWidth,
            16,
            "",
            0xC0283F3A,
            0xE0385951,
            0xFF4E8A7C,
            0xFF77C7B1
        ));
        buttonList.add(new CaptureButton(
            BUTTON_SAVE,
            panelX + PANEL_PADDING,
            actionButtonY,
            actionButtonWidth,
            ACTION_BUTTON_HEIGHT,
            I18n.format("gui.structurescanner.capture.save"),
            0xC0275134,
            0xE03B7348,
            0xFF61A06E,
            0xFF95D59E
        ));
        buttonList.add(new CaptureButton(
            BUTTON_CANCEL,
            panelX + PANEL_PADDING * 2 + actionButtonWidth,
            actionButtonY,
            actionButtonWidth,
            ACTION_BUTTON_HEIGHT,
            I18n.format("gui.structurescanner.capture.cancel"),
            0xC05A2A26,
            0xE07B3833,
            0xFFB56B63,
            0xFFF0A79B
        ));

        blocksWindow = new GuiCaptureBlocksWindow(this, summary.getBlocks(), exclusions);
        entitiesWindow = new GuiCaptureEntitiesWindow(this, summary.getEntities(), exclusions);
        lootWindow = new GuiCaptureLootWindow(this, summary.getContainers(), exclusions);
        updateButtonLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_BLOCKS) {
            blocksWindow.show();
            return;
        }

        if (button.id == BUTTON_ENTITIES) {
            entitiesWindow.show();
            return;
        }

        if (button.id == BUTTON_LOOT) {
            lootWindow.show();
            return;
        }

        if (button.id == BUTTON_SAVE) {
            saveRequested = true;
            NetworkHandler.INSTANCE.sendToServer(new PacketRequestStructureCaptureSave(firstCorner, secondCorner, exclusions.copy()));
            StructureCaptureClientController.clearSelection();
            mc.displayGuiScreen(null);
            return;
        }

        if (button.id == BUTTON_CANCEL) mc.displayGuiScreen(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (blocksWindow.isVisible() && blocksWindow.handleKey(keyCode)) return;
        if (entitiesWindow.isVisible() && entitiesWindow.handleKey(keyCode)) return;
        if (lootWindow.isVisible() && lootWindow.handleKey(keyCode)) return;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (blocksWindow.isVisible() && blocksWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (entitiesWindow.isVisible() && entitiesWindow.handleClick(mouseX, mouseY, mouseButton)) return;
        if (lootWindow.isVisible() && lootWindow.handleClick(mouseX, mouseY, mouseButton)) return;

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (blocksWindow.isVisible()) return;
        if (entitiesWindow.isVisible() && entitiesWindow.handleMouseInput(mouseX, mouseY)) return;
        if (lootWindow.isVisible() && lootWindow.handleMouseInput(mouseX, mouseY)) return;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, width, height, 0x60110D08, 0x78000000);
        updateButtonLabels();

        boolean modalVisible = blocksWindow.isVisible() || entitiesWindow.isVisible() || lootWindow.isVisible();
        int effectiveMouseX = modalVisible ? -1 : mouseX;
        int effectiveMouseY = modalVisible ? -1 : mouseY;

        drawCapturePanel();
        super.drawScreen(effectiveMouseX, effectiveMouseY, partialTicks);

        if (blocksWindow.isVisible()) {
            blocksWindow.draw(mouseX, mouseY, partialTicks);
            blocksWindow.drawTooltips(mouseX, mouseY);
        }

        if (entitiesWindow.isVisible()) {
            entitiesWindow.draw(mouseX, mouseY, partialTicks);
            entitiesWindow.drawTooltips(mouseX, mouseY);
        }

        if (lootWindow.isVisible()) {
            lootWindow.draw(mouseX, mouseY, partialTicks);
            lootWindow.drawTooltips(mouseX, mouseY);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (!saveRequested) StructureCaptureClientController.clearSelection();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateButtonLabels() {
        for (GuiButton button : buttonList) {
            if (button.id == BUTTON_BLOCKS) {
                button.displayString = I18n.format("gui.structurescanner.capture.blocksButton", getExcludedBlockCount());
                continue;
            }

            if (button.id == BUTTON_ENTITIES) {
                button.displayString = I18n.format("gui.structurescanner.capture.entitiesButton", getExcludedEntityCount());
                continue;
            }

            if (button.id == BUTTON_LOOT) {
                button.displayString = I18n.format("gui.structurescanner.capture.lootButton", getExcludedContainerCount());
            }
        }
    }

    private void calculateLayout() {
        panelWidth = Math.min(382, width - 28);
        panelHeight = Math.min(246, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    private void drawCapturePanel() {
        Gui.drawRect(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0x50000000);
        Gui.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF4F3820);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE014120F);

        drawGradientRect(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, 0xF0372B18, 0xF01E1811);
        Gui.drawRect(panelX, panelY + HEADER_HEIGHT, panelX + panelWidth, panelY + HEADER_HEIGHT + 1, 0xFFC89254);

        fontRenderer.drawStringWithShadow(
            I18n.format("gui.structurescanner.capture.title"),
            panelX + PANEL_PADDING,
            panelY + 10,
            0xFFF5EEE4
        );

        // TODO: refactor this mess

        int x = summary.getSizeX();
        int y = summary.getSizeY();
        int z = summary.getSizeZ();
        drawInfoBand(
            panelX + PANEL_PADDING,
            getSizeBandY(),
            panelWidth - PANEL_PADDING * 2,
            SIZE_BAND_HEIGHT,
            0xD019241D,
            0xFF4E8A64,
            I18n.format("gui.structurescanner.capture.size", x, y, z),
            0xFFB8F3C6
        );

        int cornerY = getCornerCardsY();
        int cornerCardWidth = getCornerCardWidth();
        drawInfoCard(
            panelX + PANEL_PADDING,
            cornerY,
            cornerCardWidth,
            CORNER_CARD_HEIGHT,
            0xC01A1917,
            0xFF8A6330,
            I18n.format("gui.structurescanner.capture.cornerA.short"),
            formatCorner(firstCorner)
        );
        drawInfoCard(
            panelX + PANEL_PADDING * 2 + cornerCardWidth,
            cornerY,
            cornerCardWidth,
            CORNER_CARD_HEIGHT,
            0xC01A1917,
            0xFF8A6330,
            I18n.format("gui.structurescanner.capture.cornerB.short"),
            formatCorner(secondCorner)
        );

        int statsY = getStatsY();
        drawSectionCard(
            getStatCardX(0),
            statsY,
            getStatCardWidth(),
            SECTION_CARD_HEIGHT,
            0xC01A1919,
            0xFF8CA3D9,
            I18n.format("gui.structurescanner.capture.blocks.short"),
            I18n.format("gui.structurescanner.capture.groups", summary.getBlocks().size()),
            I18n.format("gui.structurescanner.capture.totalBlocks", formatCompactCount(summary.getTotalBlockCount()))
        );
        drawSectionCard(
            getStatCardX(1),
            statsY,
            getStatCardWidth(),
            SECTION_CARD_HEIGHT,
            0xC01D1A16,
            0xFFD7B46A,
            I18n.format("gui.structurescanner.capture.entities.short"),
            I18n.format("gui.structurescanner.capture.entityTypes", summary.getEntityTypeCount()),
            I18n.format("gui.structurescanner.capture.entityInstances", summary.getEntities().size())
        );
        drawSectionCard(
            getStatCardX(2),
            statsY,
            getStatCardWidth(),
            SECTION_CARD_HEIGHT,
            0xC0161D1B,
            0xFF77C7B1,
            I18n.format("gui.structurescanner.capture.loot.short"),
            I18n.format("gui.structurescanner.capture.containerGroups", summary.getContainers().size()),
            I18n.format("gui.structurescanner.capture.containerInstances", summary.getTotalContainerCount())
        );

        int footerY = getFooterY();
        fontRenderer.drawString(
            fontRenderer.trimStringToWidth(I18n.format("gui.structurescanner.capture.outputFolder"), panelWidth - PANEL_PADDING * 2),
            panelX + PANEL_PADDING,
            footerY,
            0xFFD6BC93
        );
        fontRenderer.drawString(
            fontRenderer.trimStringToWidth(I18n.format("gui.structurescanner.capture.hint"), panelWidth - PANEL_PADDING * 2),
            panelX + PANEL_PADDING,
            footerY + 11,
            0xFFAAA39A
        );
    }

    private void drawInfoBand(int x, int y, int width, int height, int backgroundColor, int borderColor,
            String text, int textColor) {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + width, y + 1, borderColor);
        fontRenderer.drawString(text, x + 8, y + 7, textColor);
    }

    private void drawInfoCard(int x, int y, int width, int height, int backgroundColor, int accentColor,
            String title, String value) {
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + 2, y + height, accentColor);
        fontRenderer.drawString(title, x + 8, y + 6, 0xFFE8D8BE);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(value, width - 14), x + 8, y + 20, 0xFFD7D0C7);
    }

    private void drawSectionCard(int x, int y, int width, int height, int backgroundColor, int accentColor,
            String title, String lineOne, String lineTwo) {
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + 2, y + height, accentColor);
        Gui.drawRect(x, y, x + width, y + 1, accentColor);

        fontRenderer.drawString(title, x + 8, y + 6, 0xFFF5EEE4);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(lineOne, width - 14), x + 8, y + 22, 0xFFE1C89C);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(lineTwo, width - 14), x + 8, y + 34, 0xFFB8B8B8);
    }

    private int getSizeBandY() {
        return panelY + HEADER_HEIGHT + 6;
    }

    private int getCornerCardsY() {
        return getSizeBandY() + SIZE_BAND_HEIGHT + CARD_GAP;
    }

    private int getStatsY() {
        return getCornerCardsY() + CORNER_CARD_HEIGHT + CARD_GAP;
    }

    private int getFooterY() {
        return getActionY() - 24;
    }

    private int getActionY() {
        return panelY + panelHeight - PANEL_PADDING - ACTION_BUTTON_HEIGHT;
    }

    private int getCornerCardWidth() {
        return (panelWidth - PANEL_PADDING * 3) / 2;
    }

    private int getStatCardWidth() {
        return (panelWidth - PANEL_PADDING * 4) / 3;
    }

    private int getStatCardX(int index) {
        return panelX + PANEL_PADDING + index * (getStatCardWidth() + PANEL_PADDING);
    }

    private String formatCorner(BlockPos corner) {
        return corner.getX() + ", " + corner.getY() + ", " + corner.getZ();
    }

    private String formatCompactCount(int value) {
        int absoluteValue = Math.abs(value);
        if (absoluteValue < 1000) return String.valueOf(value);

        String suffix = I18n.format("gui.structurescanner.k");
        if (absoluteValue < 10000) return String.format("%.1f%s", value / 1000.0, suffix);

        return String.format("%d%s", value / 1000, suffix);
    }

    private int getExcludedBlockCount() {
        int count = 0;
        for (StructureCaptureSummary.BlockSummary block : summary.getBlocks()) {
            if (exclusions.isBlockExcluded(block.getKey())) count++;
        }

        return count;
    }

    private int getExcludedEntityCount() {
        int count = 0;
        for (StructureCaptureSummary.EntityInstance entity : summary.getEntities()) {
            if (exclusions.isEntityExcluded(entity.getUuid())) count++;
        }

        return count;
    }

    private int getExcludedContainerCount() {
        int count = 0;
        for (StructureCaptureSummary.ContainerSummary container : summary.getContainers()) {
            if (exclusions.isContainerExcluded(container.getKey())) count++;
        }

        return count;
    }

    private static class CaptureButton extends GuiButton {
        private final int baseColor;
        private final int hoveredColor;
        private final int borderColor;
        private final int accentColor;

        private CaptureButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
                int baseColor, int hoveredColor, int borderColor, int accentColor) {
            super(buttonId, x, y, widthIn, heightIn, buttonText);
            this.baseColor = baseColor;
            this.hoveredColor = hoveredColor;
            this.borderColor = borderColor;
            this.accentColor = accentColor;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

            int backgroundColor;
            int textColor;
            int edgeColor;

            if (!enabled) {
                backgroundColor = 0x66222222;
                textColor = 0x777777;
                edgeColor = 0x88333333;
            } else {
                backgroundColor = hovered ? hoveredColor : baseColor;
                textColor = 0xFFF3ECE2;
                edgeColor = hovered ? 0xFFF0E2C6 : borderColor;
            }

            Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, edgeColor);
            Gui.drawRect(x, y, x + width, y + height, backgroundColor);
            Gui.drawRect(x, y, x + width, y + 1, accentColor);

            String text = mc.fontRenderer.trimStringToWidth(displayString, width - 8);
            int textX = x + (width - mc.fontRenderer.getStringWidth(text)) / 2;
            int textY = y + (height - mc.fontRenderer.FONT_HEIGHT) / 2;
            mc.fontRenderer.drawString(text, textX, textY, textColor);
        }
    }
}