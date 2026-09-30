package com.simplestructurescanner.mixin.rcv;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.recurrentcomplex.RCVRandomCache;
import com.simplestructurescanner.structure.recurrentcomplex.RCVPredictionContext;
import com.simplestructurescanner.structure.recurrentcomplex.RCVRandomSeedAccess;


/**
 * Captures the Recurrent Complex decoration random seed at the HEAD of the
 * chunk-populate handler and cancels the original body during prediction.
 */
@SuppressWarnings("public-target")
@Mixin(targets = "ivorius.reccomplex.events.handlers.RCForgeEventHandler", remap = false)
public class MixinRCForgeEventHandler {

    @Inject(method = "onPreChunkDecoration", at = @At("HEAD"), cancellable = true, remap = false)
    public void simplestructurescanner$captureRandom(PopulateChunkEvent.Pre event, CallbackInfo ci) {
        try {
            Random rand = event.getRand();
            if (rand == null) return;

            AtomicLong randomSeed = RCVRandomSeedAccess.getSeedAtomic(rand);
            if (randomSeed == null) return;

            long internalSeed = randomSeed.get();

            long worldSeed = event.getWorld().getSeed();
            int dimensionId = event.getWorld().provider.getDimension();
            boolean predicting = RCVPredictionContext.isPredicting();

            if (predicting) {
                RCVRandomCache.recordSimulated(worldSeed, dimensionId, event.getChunkX(), event.getChunkZ(),
                    internalSeed);
                RCVPredictionContext.signalCaptured();
                ci.cancel();
            } else {
                RCVRandomCache.recordObserved(worldSeed, dimensionId, event.getChunkX(), event.getChunkZ(),
                    internalSeed);
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to capture Recurrent Complex random seed", e);
        }
    }
}
