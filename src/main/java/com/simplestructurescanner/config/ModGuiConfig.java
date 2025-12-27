package com.simplestructurescanner.config;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.simplestructurescanner.SimpleStructureScanner;


public class ModGuiConfig extends GuiConfig {

    public ModGuiConfig(GuiScreen parentScreen) {
        super(
            parentScreen,
            getConfigElements(),
            SimpleStructureScanner.MODID,
            false,
            false,
            I18n.format("config.structurescanner.title")
        );
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // Reload config values from in-memory Configuration into static fields after GUI closes
        ModConfig.syncFromConfig();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // ESC key (keyCode 1) should save config like Done button
        if (keyCode == Keyboard.KEY_ESCAPE && this.entryList != null) {
            this.entryList.saveConfigElements();
        }

        super.keyTyped(typedChar, keyCode);
    }

    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<>();

        for (IConfigElement el : new ConfigElement(ModConfig.getConfig().getCategory("client")).getChildElements()) {
            if (ModConfig.isConfigHidden(el.getName())) continue;
            list.add(el);
        }

        // Add the HUD position selector as a config entry
        list.add(new HudPositionConfigElement());

        return list;
    }
}
