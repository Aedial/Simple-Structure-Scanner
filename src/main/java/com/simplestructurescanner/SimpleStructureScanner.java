package com.simplestructurescanner;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(
    modid = SimpleStructureScanner.MODID,
    name = SimpleStructureScanner.NAME,
    version = SimpleStructureScanner.VERSION,
    guiFactory = "com.simplestructurescanner.config.ConfigGuiFactory"
)
public class SimpleStructureScanner {
    public static final String MODID = "simplestructurescanner";
    public static final String NAME = "Simple Structure Scanner";
    public static final String VERSION = "0.4.1";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static SimpleStructureScanner instance;

    @SidedProxy(clientSide = "com.simplestructurescanner.client.ClientProxy", serverSide = "com.simplestructurescanner.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
