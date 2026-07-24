package com.simplestructurescanner.integration.jei;

import com.simplestructurescanner.SimpleStructureScanner;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;


/**
 * Client lifecycle hooks for the JEI structure warmup pipeline.
 */
public class StructureJeiClientEvents {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        StructureJeiRecipes.onClientTick();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.getWorld().isRemote) return;

        StructureJeiRecipes.onWorldLoad();
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        StructureJeiRecipes.WarmupProgress warmup = StructureJeiRecipes.getActiveWarmup();
        if (warmup != null) {
            SimpleStructureScanner.LOGGER.info(
                "Client disconnected from server, aborting JEI warm-up for structures. " +
                "It will be re-run when the client connects to a server. Warmup reason: {}.", warmup.getReason()
            );
        }

        StructureJeiRecipes.reset();
    }
}