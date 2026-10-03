package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import g_mungus.alpha_omega.wrap.noise.PeriodicSimplex;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Feature density varying with simplex noise at {@code pos / noiseFactor}. */
@Mixin(NoiseBasedCountPlacement.class)
abstract class NoiseBasedCountPlacementMixin {

    @Shadow
    @Final
    private double noiseFactor;

    @WrapOperation(method = "count",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/PerlinSimplexNoise;getValue(DDZ)D"))
    private double alpha_omega$periodic(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        Wrap wrap = Wraps.overworld();
        return wrap.enabled() ? PeriodicSimplex.sample(noise, u, v, offsets, wrap.period / this.noiseFactor) : original.call(noise, u, v, offsets);
    }
}
