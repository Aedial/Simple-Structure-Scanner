package com.simplestructurescanner.config;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.simplestructurescanner.config.ModConfig.HudPosition;


public class GuiHudPositionSelector extends GuiScreen {
    private static final int PADDING = 5;

    // Create 3x3 grid of position buttons (the button IDs are the same as the ordinal of the HudPosition enum)
    private static final HudPosition[] POSITIONS = {
        HudPosition.TOP_LEFT, HudPosition.TOP_CENTER, HudPosition.TOP_RIGHT,
        HudPosition.CENTER_LEFT, HudPosition.CENTER, HudPosition.CENTER_RIGHT,
        HudPosition.BOTTOM_LEFT, HudPosition.BOTTOM_CENTER, HudPosition.BOTTOM_RIGHT
    };
    private static final String[] LABELS = {
        "top_left", "top_center", "top_right",
        "center_left", "center", "center_right",
        "bottom_left", "bottom_center", "bottom_right"
    };

    private final GuiScreen parentScreen;

    public GuiHudPositionSelector() {
        this(null);
    }

    public GuiHudPositionSelector(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();

        int gridStartX = PADDING;
        int gridStartY = PADDING;

        int gridWidth = width - 2 * PADDING;
        int gridHeight = height - 2 * PADDING;

        String[] buttonLabels = new String[9];
        for (int i = 0; i < LABELS.length; i++) {
            buttonLabels[i] = I18n.format("gui.structurescanner.hudPosition." + LABELS[i]);
        }

        int btnH = 20;
        int btnW = 0;
        for (String label : buttonLabels) {
            int labelW = mc.fontRenderer.getStringWidth(label) + 10;
            if (labelW > btnW) btnW = labelW;
        }

        int[] positionsX = {
            gridStartX,
            gridStartX + gridWidth / 2 - btnW / 2,
            gridStartX + gridWidth - btnW
        };
        int[] positionsY = {
            gridStartY,
            gridStartY + gridHeight / 2 - btnH / 2,
            gridStartY + gridHeight - btnH
        };

        for (int btn = 0; btn < POSITIONS.length; btn++) {
            int btnX = positionsX[btn % 3];
            int btnY = positionsY[btn / 3];
            String label = buttonLabels[btn];
            int index = POSITIONS[btn].ordinal();

            buttonList.add(new GuiButton(index, btnX, btnY, btnW, btnH, label));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= POSITIONS[0].ordinal() && button.id <= POSITIONS[POSITIONS.length - 1].ordinal()) {
            ModConfig.setClientHudPosition(POSITIONS[button.id]);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // ESC key to return to parent
        if (keyCode == 1 && parentScreen != null) {
            mc.displayGuiScreen(parentScreen);

            return;
        }

        super.keyTyped(typedChar, keyCode);
    }
}
