package com.simplestructurescanner.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.simplestructurescanner.CommonProxy;
import com.simplestructurescanner.client.event.ClientRenderEvents;


public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(new KeybindHandler());
        MinecraftForge.EVENT_BUS.register(new ClientRenderEvents());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KeybindHandler.registerKeybinds();
        ClientSettings.syncFromConfig();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
