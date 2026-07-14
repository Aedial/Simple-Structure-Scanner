package com.simplestructurescanner.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.simplestructurescanner.CommonProxy;
import com.simplestructurescanner.client.command.CommandStructureSearchBlacklist;
import com.simplestructurescanner.client.event.ClientRenderEvents;
import com.simplestructurescanner.integration.jei.StructureJeiClientEvents;
import com.simplestructurescanner.item.ModItems;


public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ModItems.registerModels();
        MinecraftForge.EVENT_BUS.register(new KeybindHandler());
        MinecraftForge.EVENT_BUS.register(new ClientRenderEvents());
        MinecraftForge.EVENT_BUS.register(new StructureJeiClientEvents());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KeybindHandler.registerKeybinds();
        ClientSettings.syncFromConfig();
        ClientCommandHandler.instance.registerCommand(new CommandStructureSearchBlacklist());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
