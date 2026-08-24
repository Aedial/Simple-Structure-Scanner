package com.simplestructurescanner.mixin.rcv;

import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simplestructurescanner.rcv.RCVPredictionContext;


/**
 * Skips Recurrent Complex's {@code MapGenStructureHook.generate()} during
 * prediction / validation populate.
 * <p>
 * MapGenStructureHook wraps every vanilla structure generator (mineshafts,
 * villages, strongholds, etc.) and casts the world to {@code WorldServer}.
 * The validation world extends {@code World} (not WorldServer), so this cast
 * throws {@code ClassCastException} whenever the validation generator runs
 * structure generation during prediction.
 * <p>
 * This is not a Forge event handler — it is a direct method call inside the
 * chunk generator, so event-based suppression cannot reach it.
 * <p>
 * Cancelling this method during prediction means RC's structure hook is
 * skipped, but:
 * <ul>
 *   <li>Vanilla structure generators (MapGenStructure subclasses) still run
 *       normally — they are separate instances, not wrapped by
 *       MapGenStructureHook during our populate</li>
 *   <li>Terrain generation (stone, dirt, grass) is unaffected</li>
 *   <li>Biome decoration (trees, flowers, lakes, ores) in populate() is
 *       unaffected — it doesn't go through MapGenStructureHook</li>
 * </ul>
 */
@Mixin(targets = "ivorius.reccomplex.world.gen.feature.structure.MapGenStructureHook", remap = false)
public class MixinMapGenStructureHook {

    @Inject(method = {"func_186125_a", "generate"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void simplestructurescanner$skipDuringPrediction(World world, int x, int z, ChunkPrimer primer, CallbackInfo ci) {
        if (RCVPredictionContext.isPredicting()) ci.cancel();
    }
}
