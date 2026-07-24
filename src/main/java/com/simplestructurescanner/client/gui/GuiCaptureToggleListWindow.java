package com.simplestructurescanner.client.gui;

import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;


/**
 * Shared modal for row-based include/exclude lists.
 */
public abstract class GuiCaptureToggleListWindow<T> {

    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 18;
    private static final int ENTRY_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int TOGGLE_BUTTON_WIDTH = 56;
    private static final int ALL_BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 14;

    private final GuiScreen parent;

    private boolean visible;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int allButtonX;
    private int allButtonY;
    private float scrollOffset;
    private int hoveredIndex = -1;

    protected GuiCaptureToggleListWindow(GuiScreen parent) {
        this.parent = parent;
    }

    public void show() {
        visible = true;
        scrollOffset = 0;
        calculateLayout();
    }

    public void hide() {
        visible = false;
        hoveredIndex = -1;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= windowX && mouseX <= windowX + windowW
            && mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public boolean handleKey(int keyCode) {
        if (!visible) return false;

        if (keyCode == Keyboard.KEY_ESCAPE) {
            hide();
            return true;
        }

        return true;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        if (!isMouseOver(mouseX, mouseY)) {
            hide();
            return true;
        }

        if (isInBounds(mouseX, mouseY, allButtonX, allButtonY, ALL_BUTTON_WIDTH, BUTTON_HEIGHT)) {
            setAllExcluded(!areAllExcluded());
            return true;
        }

        int entryIndex = getEntryIndexAt(mouseX, mouseY);
        if (entryIndex >= 0 && entryIndex < getEntries().size()) {
            T entry = getEntries().get(entryIndex);
            if (isInBounds(mouseX, mouseY, getToggleButtonX(), getEntryY(entryIndex) + 3, TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT)) {
                setEntryExcluded(entry, !isEntryExcluded(entry));
            }

            return true;
        }

        return true;
    }

    public boolean handleMouseInput(int mouseX, int mouseY, int wheel) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;

        scrollOffset -= wheel * 0.25f;
        clampScroll();
        return true;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, 0xFF303030);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xFF1A1A1A);
        Gui.drawRect(windowX, windowY + HEADER_HEIGHT - 1, windowX + windowW, windowY + HEADER_HEIGHT, 0xFF404040);

        font.drawString(getTitle(), windowX + PADDING, windowY + 8, 0xFFFFFF);

        String allButtonText = areAllExcluded()
            ? I18n.format("gui.structurescanner.capture.includeAll")
            : I18n.format("gui.structurescanner.capture.excludeAll");
        drawButton(allButtonX, allButtonY, ALL_BUTTON_WIDTH, BUTTON_HEIGHT, allButtonText,
            isInBounds(mouseX, mouseY, allButtonX, allButtonY, ALL_BUTTON_WIDTH, BUTTON_HEIGHT));

        Gui.drawRect(listX, listY, listX + listW, listY + listH, 0x40000000);

        hoveredIndex = -1;
        List<T> entries = getEntries();
        if (entries.isEmpty()) {
            font.drawString(getEmptyMessage(), listX + 4, listY + 6, 0xFF7777);
        } else {
            int visibleStart = (int) (scrollOffset / ENTRY_HEIGHT);
            int visibleEnd = Math.min(entries.size(), visibleStart + (listH / ENTRY_HEIGHT) + 2);

            for (int index = visibleStart; index < visibleEnd; index++) {
                int entryY = getEntryY(index);
                if (entryY + ENTRY_HEIGHT < listY || entryY > listY + listH) continue;

                T entry = entries.get(index);
                boolean hovered = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT;
                if (hovered) hoveredIndex = index;

                Gui.drawRect(listX, entryY, listX + listW, entryY + ENTRY_HEIGHT - 1, hovered ? 0x30FFFFFF : 0x20000000);

                String label = font.trimStringToWidth(getRowLabel(entry), listW - TOGGLE_BUTTON_WIDTH - 16);
                font.drawString(label, listX + 4, entryY + 6, hovered ? 0xFFFFAA : 0xDDDDDD);

                boolean excluded = isEntryExcluded(entry);
                String buttonText = excluded
                    ? I18n.format("gui.structurescanner.capture.include")
                    : I18n.format("gui.structurescanner.capture.exclude");
                drawToggleButton(
                    getToggleButtonX(),
                    entryY + 3,
                    buttonText,
                    isInBounds(mouseX, mouseY, getToggleButtonX(), entryY + 3, TOGGLE_BUTTON_WIDTH, BUTTON_HEIGHT),
                    excluded
                );
            }
        }

        font.drawString(getFooterText(), windowX + PADDING, windowY + windowH - FOOTER_HEIGHT + 4, 0xCCCCCC);

        if (getMaxScroll() > 0) {
            int scrollbarX = windowX + windowW - 5;
            int scrollbarHeight = Math.max(20, (int) ((float) listH / getContentHeight() * listH));
            int scrollbarY = listY + (int) ((scrollOffset / getMaxScroll()) * (listH - scrollbarHeight));
            Gui.drawRect(scrollbarX, listY, scrollbarX + 3, listY + listH, 0x40101010);
            Gui.drawRect(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarHeight, 0xA0FFFFFF);
        }
    }

    public void drawTooltips(int mouseX, int mouseY) {
        if (!visible || hoveredIndex < 0 || hoveredIndex >= getEntries().size()) return;

        List<String> tooltipLines = getTooltipLines(getEntries().get(hoveredIndex));
        if (tooltipLines == null || tooltipLines.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen currentScreen = mc.currentScreen;
        int screenWidth = currentScreen != null ? currentScreen.width : mc.displayWidth;
        int screenHeight = currentScreen != null ? currentScreen.height : mc.displayHeight;
        GuiUtils.drawHoveringText(tooltipLines, mouseX, mouseY, screenWidth, screenHeight, -1, mc.fontRenderer);
    }

    protected abstract List<T> getEntries();

    protected abstract String getTitle();

    protected abstract String getEmptyMessage();

    protected abstract String getRowLabel(T entry);

    protected abstract List<String> getTooltipLines(T entry);

    protected abstract boolean isEntryExcluded(T entry);

    protected abstract void setEntryExcluded(T entry, boolean excluded);

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int screenW = resolution.getScaledWidth();
        int screenH = resolution.getScaledHeight();

        windowW = Math.min(420, screenW - 40);
        windowH = Math.min(280, screenH - 40);
        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        listX = windowX + PADDING;
        listY = windowY + HEADER_HEIGHT + 4;
        listW = windowW - PADDING * 2 - 4;
        listH = windowH - HEADER_HEIGHT - FOOTER_HEIGHT - 8;

        allButtonX = windowX + windowW - ALL_BUTTON_WIDTH - PADDING;
        allButtonY = windowY + 5;
    }

    private int getEntryIndexAt(int mouseX, int mouseY) {
        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) return -1;

        int relativeY = mouseY - listY + (int) scrollOffset;
        return relativeY / ENTRY_HEIGHT;
    }

    private int getEntryY(int index) {
        return listY + index * ENTRY_HEIGHT - (int) scrollOffset;
    }

    private int getToggleButtonX() {
        return listX + listW - TOGGLE_BUTTON_WIDTH - 4;
    }

    private int getContentHeight() {
        return getEntries().size() * ENTRY_HEIGHT;
    }

    private float getMaxScroll() {
        return Math.max(0, getContentHeight() - listH);
    }

    private void clampScroll() {
        if (scrollOffset < 0) scrollOffset = 0;

        float maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private boolean areAllExcluded() {
        List<T> entries = getEntries();
        if (entries.isEmpty()) return false;

        for (T entry : entries) {
            if (!isEntryExcluded(entry)) return false;
        }

        return true;
    }

    private void setAllExcluded(boolean excluded) {
        for (T entry : getEntries()) setEntryExcluded(entry, excluded);
    }

    private String getFooterText() {
        int totalCount = getEntries().size();
        int excludedCount = 0;
        for (T entry : getEntries()) {
            if (isEntryExcluded(entry)) excludedCount++;
        }

        return I18n.format("gui.structurescanner.capture.summary", totalCount, excludedCount);
    }

    private void drawButton(int x, int y, int width, int height, String text, boolean hovered) {
        Gui.drawRect(x, y, x + width, y + height, hovered ? 0x60FFFFFF : 0x40FFFFFF);
        Minecraft.getMinecraft().fontRenderer.drawString(text, x + 4, y + 3, hovered ? 0xFFFFAA : 0xCCCCCC);
    }

    private void drawToggleButton(int x, int y, String text, boolean hovered, boolean excluded) {
        int background;
        int textColor;

        if (excluded) {
            background = hovered ? 0x8055AA55 : 0x60338833;
            textColor = 0xEEFFEE;
        } else {
            background = hovered ? 0x80AA5555 : 0x60883333;
            textColor = 0xFFEEEE;
        }

        Gui.drawRect(x, y, x + TOGGLE_BUTTON_WIDTH, y + BUTTON_HEIGHT, background);
        Minecraft.getMinecraft().fontRenderer.drawString(text, x + 4, y + 3, textColor);
    }

    private boolean isInBounds(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}