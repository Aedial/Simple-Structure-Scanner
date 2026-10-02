package com.simplestructurescanner.config;

import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.ConfigGuiType;
import net.minecraftforge.fml.client.config.GuiConfigEntries.IConfigEntry;
import net.minecraftforge.fml.client.config.GuiEditArrayEntries.IArrayEntry;
import net.minecraftforge.fml.client.config.IConfigElement;


public class ProviderConfigElement implements IConfigElement {
    private final IConfigElement element;

    public ProviderConfigElement(IConfigElement element) {
        this.element = element;
    }

    @Override
    public boolean isProperty() {
        return element.isProperty();
    }

    @Override
    public Class<? extends IConfigEntry> getConfigEntryClass() {
        return element.getConfigEntryClass();
    }

    @Override
    public Class<? extends IArrayEntry> getArrayEntryClass() {
        return element.getArrayEntryClass();
    }

    @Override
    public String getName() {
        return element.getName();
    }

    @Override
    public String getQualifiedName() {
        return element.getQualifiedName();
    }

    @Override
    public String getLanguageKey() {
        return "gui.structurescanner.provider." + getName();
    }

    @Override
    public String getComment() {
        return I18n.format("config.structurescanner.enabledProviders.provider.tooltip");
    }

    @Override
    public List<IConfigElement> getChildElements() {
        return element.getChildElements();
    }

    @Override
    public ConfigGuiType getType() {
        return element.getType();
    }

    @Override
    public boolean isList() {
        return element.isList();
    }

    @Override
    public boolean isListLengthFixed() {
        return element.isListLengthFixed();
    }

    @Override
    public int getMaxListLength() {
        return element.getMaxListLength();
    }

    @Override
    public boolean isDefault() {
        return Boolean.parseBoolean(element.get().toString());
    }

    @Override
    public Object getDefault() {
        return true;
    }

    @Override
    public Object[] getDefaults() {
        return new Object[] { true };
    }

    @Override
    public void setToDefault() {
        element.set(true);
    }

    @Override
    public boolean requiresWorldRestart() {
        return element.requiresWorldRestart();
    }

    @Override
    public boolean showInGui() {
        return element.showInGui();
    }

    @Override
    public boolean requiresMcRestart() {
        return true;
    }

    @Override
    public Object get() {
        return element.get();
    }

    @Override
    public Object[] getList() {
        return element.getList();
    }

    @Override
    public void set(Object value) {
        element.set(value);
    }

    @Override
    public void set(Object[] value) {
        element.set(value);
    }

    @Override
    public String[] getValidValues() {
        return element.getValidValues();
    }

    @Override
    public Object getMinValue() {
        return element.getMinValue();
    }

    @Override
    public Object getMaxValue() {
        return element.getMaxValue();
    }

    @Override
    public Pattern getValidationPattern() {
        return element.getValidationPattern();
    }
}
