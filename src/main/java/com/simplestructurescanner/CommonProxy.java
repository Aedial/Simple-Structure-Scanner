package com.simplestructurescanner;

import java.io.File;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.simplestructurescanner.config.ModConfig;
import com.simplestructurescanner.item.ModItems;
import com.simplestructurescanner.network.NetworkHandler;
import com.simplestructurescanner.structure.StructureProviderRegistry;


public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        File configFile = new File(event.getModConfigurationDirectory(), Tags.MODID + ".cfg");
        ModConfig.loadConfigs(configFile);
        ModItems.registerItems();
        NetworkHandler.init();
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
        StructureProviderRegistry.discoverProviders();
    }
}
