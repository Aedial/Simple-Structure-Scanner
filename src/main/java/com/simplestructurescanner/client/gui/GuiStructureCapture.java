package com.simplestructurescanner.client.gui;

import java.io.IOException;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import com.simplestructurescanner.capture.StructureCaptureExclusions;
import com.simplestructurescanner.capture.StructureCaptureSummary;
import com.simplestructurescanner.client.capture.StructureCaptureClientController;
import com.simplestructurescanner.client.render.StructurePreviewRenderer;
import com.simplestructurescanner.network.NetworkHandler;
import com.simplestructurescanner.network.PacketRequestStructureCaptureRenderedPreview;
import com.simplestructurescanner.network.PacketRequestStructureCaptureSave;
import com.simplestructurescanner.structure.StructureNBTParser;
import com.simplestructurescanner.structure.StructureNBTParser.ParsedStructure;


/**
 * Main capture preview screen opened after the third click with the ruler item.
 */
public class GuiStructureCapture extends GuiScreen {

    private static final int BUTTON_BLOCKS = 1;
    private static final int BUTTON_ENTITIES = 2;
    private static final int BUTTON_LOOT = 3;
    private static final int BUTTON_PREVIEW = 4;
    private static final int BUTTON_SAVE = 5;
    private static final int BUTTON_CANCEL = 6;

    private static final int PANEL_PADDING = 12;
    private static final int HEADER_HEIGHT = 28;
    private static final int SIZE_BAND_HEIGHT = 22;
    private static final int CORNER_CARD_HEIGHT = 38;
    private static final int SECTION_CARD_HEIGHT = 72;
    private static final int ACTION_BUTTON_HEIGHT = 18;
    private static final int CARD_GAP = 8;
    private static final int ACTION_BUTTON_COUNT = 3;
    private static final int FOOTER_LINE_SPACING = 11;
    private static final int STAT_BUTTON_HEIGHT = 16;
    private static final int STAT_BUTTON_SIDE_MARGIN = 6;

    private final StructureCaptureSummary summary;
    private final BlockPos firstCorner;
    private final BlockPos secondCorner;
    private final StructureCaptureExclusions exclusions = new StructureCaptureExclusions();

    private GuiCaptureBlocksWindow blocksWindow;
    private GuiCaptureEntitiesWindow entitiesWindow;
    private GuiCaptureLootWindow lootWindow;
    private GuiCapturePreviewWindow previewWindow;
    private boolean previewRequestPending;
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

        int statsButtonY = getStatsY() + SECTION_CARD_HEIGHT - STAT_BUTTON_HEIGHT - STAT_BUTTON_SIDE_MARGIN;
        int statsButtonWidth = getStatCardWidth() - STAT_BUTTON_SIDE_MARGIN * 2;
        int actionButtonWidth = (panelWidth - PANEL_PADDING * (ACTION_BUTTON_COUNT + 1)) / ACTION_BUTTON_COUNT;
        int actionButtonY = getActionY();

        addCaptureButtons(statsButtonY, statsButtonWidth, actionButtonY, actionButtonWidth);

        blocksWindow = new GuiCaptureBlocksWindow(this, summary.getBlocks(), exclusions);
        entitiesWindow = new GuiCaptureEntitiesWindow(this, summary.getEntities(), exclusions);
        lootWindow = new GuiCaptureLootWindow(this, summary.getContainers(), exclusions);
        updateButtonLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_PREVIEW) {
            if (previewRequestPending) return;

            previewRequestPending = true;
            NetworkHandler.INSTANCE.sendToServer(new PacketRequestStructureCaptureRenderedPreview(
                firstCorner,
                secondCorner,
                exclusions.copy()
            ));
            updateButtonLabels();
            return;
        }

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
        if (previewWindow != null && previewWindow.isVisible() && previewWindow.handleKey(keyCode)) return;
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
        if (previewWindow != null && previewWindow.isVisible() && previewWindow.handleClick(mouseX, mouseY, mouseButton)) return;
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

        if (previewWindow != null && previewWindow.isVisible()) return;
        if (blocksWindow.isVisible()) return;
        if (entitiesWindow.isVisible() && entitiesWindow.handleMouseInput(mouseX, mouseY)) return;
        if (lootWindow.isVisible() && lootWindow.handleMouseInput(mouseX, mouseY)) return;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Warm smoke overlay that fades into black behind the capture UI.
        drawGradientRect(0, 0, width, height, 0x60110D08, 0x78000000);
        updateButtonLabels();

        boolean previewVisible = previewWindow != null && previewWindow.isVisible();
        boolean modalVisible = previewVisible || blocksWindow.isVisible() || entitiesWindow.isVisible() || lootWindow.isVisible();
        int effectiveMouseX = modalVisible ? -1 : mouseX;
        int effectiveMouseY = modalVisible ? -1 : mouseY;

        drawCapturePanel();
        super.drawScreen(effectiveMouseX, effectiveMouseY, partialTicks);

        if (previewVisible) {
            previewWindow.draw(mouseX, mouseY, partialTicks);
            previewWindow.drawTooltips(mouseX, mouseY);
            return;
        }

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
        if (previewWindow != null) {
            previewWindow.release();
            previewWindow = null;
        }
        if (!saveRequested) StructureCaptureClientController.clearSelection();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void updateButtonLabels() {
        for (GuiButton button : buttonList) {
            if (button.id == BUTTON_PREVIEW) {
                button.displayString = I18n.format(
                    previewRequestPending
                        ? "gui.structurescanner.capture.preview.loading"
                        : "gui.structurescanner.capture.preview"
                );
                button.enabled = !previewRequestPending;
                continue;
            }

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

    public void handleRenderedPreviewResponse(@Nullable NBTTagCompound structureTag, String errorKey) {
        previewRequestPending = false;
        updateButtonLabels();

        if (structureTag == null) {
            sendCaptureMessage(errorKey == null || errorKey.isEmpty()
                ? "chat.structurescanner.capture.emptyAfterExclusions"
                : errorKey);
            return;
        }

        ParsedStructure parsedStructure = StructureNBTParser.parseStructureNbt(structureTag);
        if (parsedStructure == null) {
            sendCaptureMessage("chat.structurescanner.capture.previewFailed");
            return;
        }

        if (previewWindow != null) previewWindow.release();

        StructurePreviewRenderer previewRenderer = StructurePreviewRenderer.createFromLayers(parsedStructure.layers);
        previewWindow = new GuiCapturePreviewWindow(previewRenderer);
        previewWindow.show();
    }

    private void addCaptureButtons(int statsButtonY, int statsButtonWidth, int actionButtonY, int actionButtonWidth) {
        // Block button palette: smoky slate base, dusky slate hover, muted blue border, cornflower accent
        addStatButton(BUTTON_BLOCKS, 0, statsButtonY, statsButtonWidth, 0xC02B3244, 0xE03B4660, 0xFF5E6C90, 0xFF8CA3D9);
        // Entity button palette: earthy brown base, richer umber hover, brass border, sand accent
        addStatButton(BUTTON_ENTITIES, 1, statsButtonY, statsButtonWidth, 0xC0443929, 0xE05D4D35, 0xFF8D6D3B, 0xFFD7B46A);
        // Loot button palette: deep teal base, greener teal hover, sea-glass border, aqua accent
        addStatButton(BUTTON_LOOT, 2, statsButtonY, statsButtonWidth, 0xC0283F3A, 0xE0385951, 0xFF4E8A7C, 0xFF77C7B1);

        addActionButton(
            BUTTON_PREVIEW,
            0,
            actionButtonY,
            actionButtonWidth,
            "gui.structurescanner.capture.preview",
            0xC0233656,  // base: dusk blue
            0xE0334E7B,  // hover: storm blue
            0xFF5F81C2,  // border: cornflower blue
            0xFF96B5EB  // accent: powder blue
        );
        addActionButton(
            BUTTON_SAVE,
            1,
            actionButtonY,
            actionButtonWidth,
            "gui.structurescanner.capture.save",
            0xC0275134,  // base: forest green
            0xE03B7348,  // hover: brighter moss green
            0xFF61A06E,  // border: sage green
            0xFF95D59E  // accent: pale mint
        );
        addActionButton(
            BUTTON_CANCEL,
            2,
            actionButtonY,
            actionButtonWidth,
            "gui.structurescanner.capture.cancel",
            0xC05A2A26,  // base: brick red
            0xE07B3833,  // hover: ember red
            0xFFB56B63,  // border: dusty coral
            0xFFF0A79B  // accent: peach highlight
        );
    }

    private void addStatButton(int buttonId, int index, int y, int width, int baseColor, int hoveredColor,
            int borderColor, int accentColor) {
        buttonList.add(new CaptureButton(
            buttonId,
            getStatButtonX(index),
            y,
            width,
            STAT_BUTTON_HEIGHT,
            "",
            baseColor,
            hoveredColor,
            borderColor,
            accentColor
        ));
    }

    private void addActionButton(int buttonId, int index, int y, int width, String labelKey, int baseColor,
            int hoveredColor, int borderColor, int accentColor) {
        buttonList.add(new CaptureButton(
            buttonId,
            getActionButtonX(index, width),
            y,
            width,
            ACTION_BUTTON_HEIGHT,
            I18n.format(labelKey),
            baseColor,
            hoveredColor,
            borderColor,
            accentColor
        ));
    }

    private void calculateLayout() {
        panelWidth = Math.min(382, width - 28);
        panelHeight = Math.min(246, height - 24);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    private void drawCapturePanel() {
        // Soft black shadow around the panel shell
        Gui.drawRect(panelX - 3, panelY - 3, panelX + panelWidth + 3, panelY + panelHeight + 3, 0x50000000);
        // Aged bronze frame
        Gui.drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF4F3820);
        // Charred wood panel fill
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE014120F);
        // Ember-brown header gradient
        drawGradientRect(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, 0xF0372B18, 0xF01E1811);
        // Honey-gold divider below the header
        Gui.drawRect(panelX, panelY + HEADER_HEIGHT, panelX + panelWidth, panelY + HEADER_HEIGHT + 1, 0xFFC89254);

        fontRenderer.drawStringWithShadow(
            I18n.format("gui.structurescanner.capture.title"),
            panelX + PANEL_PADDING,
            panelY + 10,
            0xFFF5EEE4  // parchment white
        );

        drawCaptureSummaryContent();
    }

    private void drawCaptureSummaryContent() {
        drawSizeSummaryBand();
        drawCornerSummaryCards();
        drawStatSummaryCards();
        drawFooterSummaryText();
    }

    private void drawSizeSummaryBand() {
        drawInfoBand(
            panelX + PANEL_PADDING,
            getSizeBandY(),
            panelWidth - PANEL_PADDING * 2,
            SIZE_BAND_HEIGHT,
            0xD019241D,  // background: mossy charcoal
            0xFF4E8A64,  // border: fern green
            I18n.format(
                "gui.structurescanner.capture.size",
                summary.getSizeX(),
                summary.getSizeY(),
                summary.getSizeZ()
            ),
            0xFFB8F3C6  // text: mint green
        );
    }

    private void drawCornerSummaryCards() {
        int cornerY = getCornerCardsY();

        drawCornerCard(0, cornerY, "gui.structurescanner.capture.cornerA.short", firstCorner);
        drawCornerCard(1, cornerY, "gui.structurescanner.capture.cornerB.short", secondCorner);
    }

    private void drawCornerCard(int index, int y, String titleKey, BlockPos corner) {
        int cornerCardWidth = getCornerCardWidth();

        drawInfoCard(
            getCornerCardX(index, cornerCardWidth),
            y,
            cornerCardWidth,
            CORNER_CARD_HEIGHT,
            0xC01A1917,  // background: soot brown
            0xFF8A6330,  // accent: bronze brown
            I18n.format(titleKey),
            formatCorner(corner)
        );
    }

    private void drawStatSummaryCards() {
        int statsY = getStatsY();

        drawStatSummaryCard(
            0,
            statsY,
            0xC01A1919,  // background: slate charcoal
            0xFF8CA3D9,  // accent: cornflower blue
            "gui.structurescanner.capture.blocks.short",
            I18n.format("gui.structurescanner.capture.groups", summary.getBlocks().size()),
            I18n.format("gui.structurescanner.capture.totalBlocks", formatCompactCount(summary.getTotalBlockCount()))
        );
        drawStatSummaryCard(
            1,
            statsY,
            0xC01D1A16,  // background: umber charcoal
            0xFFD7B46A,  // accent: warm sand
            "gui.structurescanner.capture.entities.short",
            I18n.format("gui.structurescanner.capture.entityTypes", summary.getEntityTypeCount()),
            I18n.format("gui.structurescanner.capture.entityInstances", summary.getEntities().size())
        );
        drawStatSummaryCard(
            2,
            statsY,
            0xC0161D1B,  // background: deep teal charcoal
            0xFF77C7B1,  // accent: seafoam green
            "gui.structurescanner.capture.loot.short",
            I18n.format("gui.structurescanner.capture.containerGroups", summary.getContainers().size()),
            I18n.format("gui.structurescanner.capture.containerInstances", summary.getTotalContainerCount())
        );
    }

    private void drawStatSummaryCard(int index, int y, int backgroundColor, int accentColor, String titleKey,
            String lineOne, String lineTwo) {
        drawSectionCard(
            getStatCardX(index),
            y,
            getStatCardWidth(),
            SECTION_CARD_HEIGHT,
            backgroundColor,
            accentColor,
            I18n.format(titleKey),
            lineOne,
            lineTwo
        );
    }

    private void drawFooterSummaryText() {
        int footerY = getFooterY();

        // Muted sand label text
        drawFooterLine(footerY, "gui.structurescanner.capture.outputFolder", 0xFFD6BC93);
        // Cool stone hint text
        drawFooterLine(footerY + FOOTER_LINE_SPACING, "gui.structurescanner.capture.hint", 0xFFAAA39A);
    }

    private void drawFooterLine(int y, String translationKey, int color) {
        fontRenderer.drawString(
            fontRenderer.trimStringToWidth(I18n.format(translationKey), panelWidth - PANEL_PADDING * 2),
            panelX + PANEL_PADDING,
            y,
            color
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

        // Warm parchment title text
        fontRenderer.drawString(title, x + 8, y + 6, 0xFFE8D8BE);
        // Soft ash value text
        fontRenderer.drawString(fontRenderer.trimStringToWidth(value, width - 14), x + 8, y + 20, 0xFFD7D0C7);
    }

    private void drawSectionCard(int x, int y, int width, int height, int backgroundColor, int accentColor,
            String title, String lineOne, String lineTwo) {
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);
        Gui.drawRect(x, y, x + 2, y + height, accentColor);
        Gui.drawRect(x, y, x + width, y + 1, accentColor);

        // Parchment white section title text
        fontRenderer.drawString(title, x + 8, y + 6, 0xFFF5EEE4);
        // Warm gold primary stat text
        fontRenderer.drawString(fontRenderer.trimStringToWidth(lineOne, width - 14), x + 8, y + 22, 0xFFE1C89C);
        // Neutral silver secondary stat text
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

    private int getActionButtonX(int index, int buttonWidth) {
        return panelX + PANEL_PADDING + index * (buttonWidth + PANEL_PADDING);
    }

    private int getCornerCardWidth() {
        return (panelWidth - PANEL_PADDING * 3) / 2;
    }

    private int getCornerCardX(int index, int cardWidth) {
        return panelX + PANEL_PADDING + index * (cardWidth + PANEL_PADDING);
    }

    private int getStatCardWidth() {
        return (panelWidth - PANEL_PADDING * 4) / 3;
    }

    private int getStatCardX(int index) {
        return panelX + PANEL_PADDING + index * (getStatCardWidth() + PANEL_PADDING);
    }

    private int getStatButtonX(int index) {
        return getStatCardX(index) + STAT_BUTTON_SIDE_MARGIN;
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

    private void sendCaptureMessage(String translationKey) {
        if (mc == null || mc.player == null) return;
        if (translationKey == null || translationKey.isEmpty()) return;

        mc.player.sendMessage(new TextComponentTranslation(translationKey));
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
                backgroundColor = 0x66222222;   // Disabled fill uses a dim charcoal wash
                textColor = 0x777777;           // Disabled text stays a flat mid gray
                edgeColor = 0x88333333;         // Disabled edge is a slightly denser charcoal
            } else {
                backgroundColor = hovered ? hoveredColor : baseColor;
                textColor = 0xFFF3ECE2; // Active button text uses a warm ivory for readability
                // Hovered buttons get a brighter ivory edge highlight.
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