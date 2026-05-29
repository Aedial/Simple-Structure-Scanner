package com.simplestructurescanner;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

import com.simplestructurescanner.capture.StructureCaptureService;


public class CaptureSessionEvents {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.player == null) return;

        StructureCaptureService.clearFrozenCapture(event.player.getUniqueID());
    }
}