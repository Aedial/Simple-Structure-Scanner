package com.simplestructurescanner.config;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.DummyConfigElement.DummyCategoryElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.simplestructurescanner.Tags;
import com.simplestructurescanner.client.ClientSettings;

public class SimpleStructureScannerGuiConfig extends GuiConfig {

    public SimpleStructureScannerGuiConfig(GuiScreen parentScreen) {
        super(
            parentScreen,
            getConfigElements(),
            Tags.MODID,
            false,
            false,
            I18n.format("config.structurescanner.title")
        );
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        // Apply config GUI values to the annotated fields
        SimpleStructureScannerConfig.syncConfig();
        ClientSettings.syncFromConfig();
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

        for (IConfigElement category : ConfigElement.from(SimpleStructureScannerConfig.class).getChildElements()) {
            if (SimpleStructureScannerConfig.isHiddenCategory(category.getName())) continue;
            if (category.getChildElements().isEmpty()) continue;

            if ("client".equals(category.getName())) {
                list.add(createClientCategory(category));
                continue;
            }

            if ("enabledProviders".equals(category.getName())) {
                list.add(createProviderCategory(category));
                continue;
            }

            list.add(category);
        }

        return list;
    }

    private static IConfigElement createClientCategory(IConfigElement category) {
        List<IConfigElement> clientElements = new ArrayList<>();
        for (IConfigElement element : category.getChildElements()) {
            if ("hudPosition".equals(element.getName())) continue;
            clientElements.add(element);
        }

        // Add the HUD position selector as a config entry
        clientElements.add(new HudPositionConfigElement());

        return new DummyCategoryElement(
            category.getName(),
            category.getLanguageKey(),
            clientElements
        );
    }

    private static IConfigElement createProviderCategory(IConfigElement category) {
        List<IConfigElement> providerElements = new ArrayList<>();
        for (IConfigElement childCategory : category.getChildElements()) {
            if (!"providers".equals(childCategory.getName())) continue;

            for (IConfigElement element : childCategory.getChildElements()) {
                providerElements.add(new ProviderConfigElement(element));
            }
        }

        return new DummyCategoryElement(
            category.getName(),
            "config.structurescanner.enabledProviders",
            providerElements
        );
    }
}
