package com.simplestructurescanner.client.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import com.simplestructurescanner.client.render.StructurePreviewRenderer;


/**
 * Shared modal popup window that displays a large 3D structure preview.
 */
public class GuiPreviewWindow extends Gui {
    private static final int HEADER_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int WINDOW_MARGIN = 40;

    private final String title;
    private final StructurePreviewRenderer previewRenderer;
    private final WindowPalette palette;

    private boolean visible = false;
    private boolean hiddenForNavigation = false;
    private int windowX, windowY, windowW, windowH;

    private GuiPreviewWindow(String title, StructurePreviewRenderer previewRenderer, WindowPalette palette) {
        this.title = title;
        this.previewRenderer = previewRenderer;
        this.palette = palette;
    }

    public static GuiPreviewWindow createScannerPreview(String title, StructurePreviewRenderer previewRenderer) {
        return new GuiPreviewWindow(title, previewRenderer, WindowPalette.SCANNER);
    }

    public static GuiPreviewWindow createCapturePreview(StructurePreviewRenderer previewRenderer) {
        return new GuiPreviewWindow(
            I18n.format("gui.structurescanner.capture.preview.title"),
            previewRenderer,
            WindowPalette.CAPTURE
        );
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

    public void release() {
        previewRenderer.release();
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        // Make a large square window that fits the screen
        int maxSize = Math.max(80, Math.min(screenW - WINDOW_MARGIN, screenH - WINDOW_MARGIN));
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

    public boolean handleMouseInput(int mouseX, int mouseY, int wheel) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (wheel == 0) return true;

        if (wheel > 0) {
            previewRenderer.zoomIn();
        } else {
            previewRenderer.zoomOut();
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

        return mouseX >= windowX && mouseX <= windowX + windowW &&
               mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        // Draw window background
        Gui.drawRect(windowX - 1, windowY - 1, windowX + windowW + 1, windowY + windowH + 1, palette.frameColor);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, palette.backgroundColor);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + HEADER_HEIGHT, palette.headerTopColor);
        if (palette.headerTopColor != palette.headerBottomColor) {
            this.drawGradientRect(windowX, windowY, windowX + windowW, windowY + HEADER_HEIGHT,
                palette.headerTopColor, palette.headerBottomColor);
        }

        // Draw header
        String elidedTitle = font.trimStringToWidth(title, windowW - 16);
        if (!elidedTitle.equals(title)) elidedTitle += "...";
        font.drawStringWithShadow(elidedTitle, windowX + 8, windowY + 6, palette.titleColor);

        // Draw header separator line
        Gui.drawRect(windowX, windowY + HEADER_HEIGHT - 1, windowX + windowW, windowY + HEADER_HEIGHT, palette.separatorColor);

        // Draw preview area background
        int previewX = windowX + PADDING;
        int previewY = windowY + HEADER_HEIGHT + PADDING;
        int previewSize = windowW - PADDING * 2;
        int previewHeight = windowH - HEADER_HEIGHT - PADDING * 2;

        Gui.drawRect(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewHeight + 1, 0xFF333333);
        Gui.drawRect(previewX, previewY, previewX + previewSize, previewY + previewHeight, 0xFF1A1A1A);

        if (!previewRenderer.isBuildReady()) {
            drawCenteredPreviewStatus(font, previewX, previewY, previewSize, previewHeight,
                getAnimatedLoadingText(I18n.format("gui.structurescanner.preview.loading")), 0xAAAAAA);
            return;
        }

        if (!previewRenderer.hasRenderableBlocks()) {
            drawCenteredPreviewStatus(font, previewX, previewY, previewSize, previewHeight,
                I18n.format("gui.structurescanner.preview.unavailable"), 0x888888);
            return;
        }

        // Draw the structure preview
        if (previewRenderer.getWorld() != null) {
            previewRenderer.setBackgroundColor(0xFF1A1A1A);
            previewRenderer.render(previewX, previewY, previewSize, previewHeight);
        }
    }

    public void drawTooltips(int mouseX, int mouseY) {
        // No tooltips needed for the preview window
    }

    static void drawCenteredPreviewStatus(FontRenderer font, int x, int y, int width, int height, String text,
            int color) {
        int textX = x + (width - font.getStringWidth(text)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawString(text, textX, textY, color);
    }

    static String getAnimatedLoadingText(String baseText) {
        int dotCount = (int) (Minecraft.getSystemTime() / 250L % 3L) + 1;
        StringBuilder builder = new StringBuilder(baseText);

        for (int i = 0; i < dotCount; i++) builder.append('.');

        return builder.toString();
    }

    private static class WindowPalette {
        private static final WindowPalette SCANNER = new WindowPalette(
            0xFF303030,
            0xFF1A1A1A,
            0xFF1A1A1A,
            0xFF1A1A1A,
            0xFF404040,
            0xFFFFFF
        );
        private static final WindowPalette CAPTURE = new WindowPalette(
            0xFF4F3820,
            0xE014120F,
            0xF0372B18,
            0xF01E1811,
            0xFFC89254,
            0xFFF5EEE4
        );

        private final int frameColor;
        private final int backgroundColor;
        private final int headerTopColor;
        private final int headerBottomColor;
        private final int separatorColor;
        private final int titleColor;

        private WindowPalette(int frameColor, int backgroundColor, int headerTopColor, int headerBottomColor,
                int separatorColor, int titleColor) {
            this.frameColor = frameColor;
            this.backgroundColor = backgroundColor;
            this.headerTopColor = headerTopColor;
            this.headerBottomColor = headerBottomColor;
            this.separatorColor = separatorColor;
            this.titleColor = titleColor;
        }
    }
}
