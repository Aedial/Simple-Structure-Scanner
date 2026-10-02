package com.simplestructurescanner.mixin.rcv;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.math.ChunkPos;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.structure.providers.recurrentcomplex.RCVRandomCache;
import com.simplestructurescanner.structure.providers.recurrentcomplex.RCVPredictionContext;
import com.simplestructurescanner.structure.providers.recurrentcomplex.RCVRandomSeedAccess;


/**
 * Replaces the population random during a scanner lookup with a cached seed
 * when available, ensuring deterministic prediction results.
 */
@SuppressWarnings("public-target")
@Mixin(targets = "ivorius.reccomplex.world.gen.feature.StructureLocator", remap = false)
public class MixinStructureLocator {

    private static boolean loggedFirstHit = false;

    @Inject(method = "populationRandom", at = @At("RETURN"), cancellable = true, remap = false)
    private static void simplestructurescanner$useCachedRandom(long worldSeed, ChunkPos chunkPos, CallbackInfoReturnable<Random> cir) {
        Integer dimensionId = RCVPredictionContext.getRandomCacheDimension();
        if (dimensionId == null) return;

        long cachedSeed = RCVRandomCache.get(worldSeed, dimensionId, chunkPos.x, chunkPos.z);
        if (cachedSeed == Long.MIN_VALUE) return;

        Random random = RCVRandomSeedAccess.withInternalSeed(cachedSeed);
        if (random == null) return;

        cir.setReturnValue(random);
        if (!loggedFirstHit) {
            loggedFirstHit = true;
            SimpleStructureScanner.LOGGER.debug("Recurrent Complex populationRandom cache hit for chunk({},{})",
                chunkPos.x, chunkPos.z);
        }
    }
}
