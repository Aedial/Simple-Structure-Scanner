package com.simplestructurescanner.client.gui;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;


/**
 * Simple confirmation dialog with OK and Cancel buttons.
 */
public class GuiConfirmDialog {
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 16;
    private static final int PADDING = 10;

    private final String title;
    private final String message;
    private final Runnable onConfirm;

    private boolean visible = false;
    private int windowX, windowY, windowW, windowH;
    private int okButtonX, okButtonY;
    private int cancelButtonX, cancelButtonY;

    public GuiConfirmDialog(String title, String message, Runnable onConfirm) {
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    public void show() {
        visible = true;
        calculateLayout();
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    private void calculateLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRenderer;
        ScaledResolution sr = new ScaledResolution(mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();

        int titleWidth = fontRenderer.getStringWidth(title);
        int messageWidth = fontRenderer.getStringWidth(message);
        int buttonsWidth = BUTTON_WIDTH * 2 + PADDING;

        windowW = Math.max(Math.max(titleWidth, messageWidth), buttonsWidth) + PADDING * 2;
        windowH = 10 + 12 + 10 + 12 + 10 + BUTTON_HEIGHT + PADDING;

        windowX = (screenW - windowW) / 2;
        windowY = (screenH - windowH) / 2;

        int buttonsY = windowY + windowH - PADDING - BUTTON_HEIGHT;
        int buttonsStartX = windowX + (windowW - buttonsWidth) / 2;

        okButtonX = buttonsStartX;
        okButtonY = buttonsY;
        cancelButtonX = buttonsStartX + BUTTON_WIDTH + PADDING;
        cancelButtonY = buttonsY;
    }

    public boolean handleClick(int mouseX, int mouseY, int mouseButton) {
        if (!visible) return false;

        // Check OK button
        if (mouseX >= okButtonX && mouseX <= okButtonX + BUTTON_WIDTH &&
            mouseY >= okButtonY && mouseY <= okButtonY + BUTTON_HEIGHT) {
            if (onConfirm != null) onConfirm.run();

            hide();

            return true;
        }

        // Check Cancel button
        if (mouseX >= cancelButtonX && mouseX <= cancelButtonX + BUTTON_WIDTH &&
            mouseY >= cancelButtonY && mouseY <= cancelButtonY + BUTTON_HEIGHT) {
            hide();

            return true;
        }

        // Click outside closes dialog
        if (mouseX < windowX || mouseX > windowX + windowW ||
            mouseY < windowY || mouseY > windowY + windowH) {
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

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (onConfirm != null) onConfirm.run();

            hide();

            return true;
        }

        return false;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return visible && mouseX >= windowX && mouseX <= windowX + windowW &&
               mouseY >= windowY && mouseY <= windowY + windowH;
    }

    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRenderer;

        // Draw background
        Gui.drawRect(windowX - 2, windowY - 2, windowX + windowW + 2, windowY + windowH + 2, 0xFF000000);
        Gui.drawRect(windowX, windowY, windowX + windowW, windowY + windowH, 0xFF202020);

        // Draw title
        int titleX = windowX + (windowW - fontRenderer.getStringWidth(title)) / 2;
        fontRenderer.drawString(title, titleX, windowY + 10, 0xFFFFAA);

        // Draw message
        int messageX = windowX + (windowW - fontRenderer.getStringWidth(message)) / 2;
        fontRenderer.drawString(message, messageX, windowY + 10 + 12 + 6, 0xFFFFFF);

        // Draw OK button
        boolean okHovered = mouseX >= okButtonX && mouseX <= okButtonX + BUTTON_WIDTH &&
                           mouseY >= okButtonY && mouseY <= okButtonY + BUTTON_HEIGHT;
        int okBgColor = okHovered ? 0xFF404040 : 0xFF303030;
        Gui.drawRect(okButtonX, okButtonY, okButtonX + BUTTON_WIDTH, okButtonY + BUTTON_HEIGHT, okBgColor);
        Gui.drawRect(okButtonX, okButtonY, okButtonX + BUTTON_WIDTH, okButtonY + 1, 0xFF505050);
        Gui.drawRect(okButtonX, okButtonY + BUTTON_HEIGHT - 1, okButtonX + BUTTON_WIDTH, okButtonY + BUTTON_HEIGHT, 0xFF101010);

        String okText = I18n.format("gui.structurescanner.ok");
        int okTextX = okButtonX + (BUTTON_WIDTH - fontRenderer.getStringWidth(okText)) / 2;
        fontRenderer.drawString(okText, okTextX, okButtonY + 4, okHovered ? 0xFFFFAA : 0xCCCCCC);

        // Draw Cancel button
        boolean cancelHovered = mouseX >= cancelButtonX && mouseX <= cancelButtonX + BUTTON_WIDTH &&
                               mouseY >= cancelButtonY && mouseY <= cancelButtonY + BUTTON_HEIGHT;
        int cancelBgColor = cancelHovered ? 0xFF404040 : 0xFF303030;
        Gui.drawRect(cancelButtonX, cancelButtonY, cancelButtonX + BUTTON_WIDTH, cancelButtonY + BUTTON_HEIGHT, cancelBgColor);
        Gui.drawRect(cancelButtonX, cancelButtonY, cancelButtonX + BUTTON_WIDTH, cancelButtonY + 1, 0xFF505050);
        Gui.drawRect(cancelButtonX, cancelButtonY + BUTTON_HEIGHT - 1, cancelButtonX + BUTTON_WIDTH, cancelButtonY + BUTTON_HEIGHT, 0xFF101010);

        String cancelText = I18n.format("gui.structurescanner.cancel");
        int cancelTextX = cancelButtonX + (BUTTON_WIDTH - fontRenderer.getStringWidth(cancelText)) / 2;
        fontRenderer.drawString(cancelText, cancelTextX, cancelButtonY + 4, cancelHovered ? 0xFFFFAA : 0xCCCCCC);
    }
}
