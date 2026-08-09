package com.simplestructurescanner.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import zone.rong.mixinbooter.ILateMixinLoader;


/**
 * Registers optional mixin configs for Simple Structure Scanner.
 */
@Optional.Interface(iface = "zone.rong.mixinbooter.ILateMixinLoader", modid = "mixinbooter")
public class SimpleStructureScannerMixinPlugin implements ILateMixinLoader {

    @Override
    @Optional.Method(modid = "mixinbooter")
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        configs.add("mixins.simplestructurescanner.json");

        if (Loader.isModLoaded("jei")) configs.add("mixins.simplestructurescanner.jei.json");

        if (Loader.isModLoaded("reccomplex")) configs.add("mixins.simplestructurescanner.rcv.json");

        return configs;
    }
}