package com.simplestructurescanner.mixin.rcv;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simplestructurescanner.SimpleStructureScanner;
import com.simplestructurescanner.rcv.RCVRandomCache;

import net.minecraft.util.math.ChunkPos;

/**
 * Replaces the population random in Recurrent Complex's StructureLocator with
 * a cached seed when available, ensuring deterministic prediction results.
 */
@Mixin(targets = "ivorius.reccomplex.world.gen.feature.StructureLocator", remap = false)
public class MixinStructureLocator {

    private static volatile boolean seedFieldReady = false;
    private static Field cachedSeedField = null;
    private static boolean loggedFirstHit = false;

    @Inject(method = "populationRandom", at = @At("RETURN"), cancellable = true, remap = false)
    public static void simplestructurescanner$useCachedRandom(long worldSeed, ChunkPos chunkPos, CallbackInfoReturnable<Random> cir) {
        long cachedSeed = RCVRandomCache.get(worldSeed, chunkPos.x, chunkPos.z);
        if (cachedSeed == Long.MIN_VALUE) return;

        try {
            Field seedField = getCachedSeedField();
            if (seedField == null) {
                SimpleStructureScanner.LOGGER.warn("Cache hit but seedField is null for chunk({},{})",
                        chunkPos.x, chunkPos.z);
                return;
            }

            Random r = new Random(0L);
            ((AtomicLong) seedField.get(r)).set(cachedSeed);
            cir.setReturnValue(r);

            if (!loggedFirstHit) {
                loggedFirstHit = true;
                SimpleStructureScanner.LOGGER.info("Recurrent Complex populationRandom cache hit for chunk({},{})",
                        chunkPos.x, chunkPos.z);
            }
        } catch (Exception e) {
            SimpleStructureScanner.LOGGER.warn("Failed to replace populationRandom for chunk({},{})",
                    chunkPos.x, chunkPos.z, e);
        }
    }

    private static Field getCachedSeedField() {
        if (seedFieldReady) return cachedSeedField;
        synchronized (MixinStructureLocator.class) {
            if (seedFieldReady) return cachedSeedField;
            seedFieldReady = true;
            try {
                cachedSeedField = Random.class.getDeclaredField("seed");
                cachedSeedField.setAccessible(true);
            } catch (Exception e) {
                SimpleStructureScanner.LOGGER.warn("Failed to get Random.seed field for StructureLocator mixin", e);
            }
            return cachedSeedField;
        }
    }
}
