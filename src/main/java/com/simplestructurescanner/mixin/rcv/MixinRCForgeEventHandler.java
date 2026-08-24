package com.simplestructurescanner.mixin.rcv;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVRandomCache;
import com.simplestructurescanner.rcv.RCVPredictionContext;


/**
 * Captures the Recurrent Complex decoration random seed at the HEAD of the
 * chunk-populate handler and cancels the original body during prediction.
 */
@Mixin(targets = "ivorius.reccomplex.events.handlers.RCForgeEventHandler", remap = false)
public class MixinRCForgeEventHandler {

    private static volatile boolean seedFieldReady = false;
    private static Field cachedSeedField = null;

    @Inject(method = "onPreChunkDecoration", at = @At("HEAD"), cancellable = true, remap = false)
    public void simplestructurescanner$captureRandom(PopulateChunkEvent.Pre event, CallbackInfo ci) {
        try {
            Random rand = event.getRand();
            if (rand == null) return;

            Field seedField = getCachedSeedField();
            if (seedField == null) return;

            long internalSeed = ((AtomicLong) seedField.get(rand)).get();
            long worldSeed = event.getWorld().getSeed();

            RCVRandomCache.store(worldSeed, event.getChunkX(), event.getChunkZ(), internalSeed);
            RCVPredictionContext.signalCaptured();

            if (!RCVPredictionContext.isPredicting()) {
                SimpleStructureScanner.LOGGER.debug("Captured Recurrent Complex random seed for chunk({},{}) cacheSize={}",
                        event.getChunkX(), event.getChunkZ(), RCVRandomCache.size());
            } else {
                ci.cancel();
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to capture Recurrent Complex random seed", e);
        }
    }

    private static Field getCachedSeedField() {
        if (seedFieldReady) return cachedSeedField;

        synchronized (MixinRCForgeEventHandler.class) {
            if (seedFieldReady) return cachedSeedField;

            seedFieldReady = true;
            try {
                cachedSeedField = Random.class.getDeclaredField("seed");
                cachedSeedField.setAccessible(true);
                SimpleStructureScanner.LOGGER.debug("Random.seed field initialized for Recurrent Complex mixin");
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Failed to get Random.seed field for Recurrent Complex mixin", e);
            }

            return cachedSeedField;
        }
    }
}
