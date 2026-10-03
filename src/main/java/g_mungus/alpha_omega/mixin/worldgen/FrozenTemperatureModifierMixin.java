package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import g_mungus.alpha_omega.wrap.noise.PeriodicSimplex;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Frozen ocean temperature patches: three simplex samples at scales 0.05, 0.2 and 0.09, in that order. */
@Mixin(targets = "net.minecraft.world.level.biome.Biome$TemperatureModifier$2")
abstract class FrozenTemperatureModifierMixin {

    @Unique
    private static final String GET_VALUE = "Lnet/minecraft/world/level/levelgen/synth/PerlinSimplexNoise;getValue(DDZ)D";

    @WrapOperation(method = "modifyTemperature", at = @At(value = "INVOKE", target = GET_VALUE, ordinal = 0))
    private double alpha_omega$periodicFrozen(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        Wrap wrap = Wraps.overworld();
        return wrap.enabled() ? PeriodicSimplex.sample(noise, u, v, offsets, wrap.period * 0.05) : original.call(noise, u, v, offsets);
    }

    @WrapOperation(method = "modifyTemperature", at = @At(value = "INVOKE", target = GET_VALUE, ordinal = 1))
    private double alpha_omega$periodicInfo(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        Wrap wrap = Wraps.overworld();
        return wrap.enabled() ? PeriodicSimplex.sample(noise, u, v, offsets, wrap.period * 0.2) : original.call(noise, u, v, offsets);
    }

    @WrapOperation(method = "modifyTemperature", at = @At(value = "INVOKE", target = GET_VALUE, ordinal = 2))
    private double alpha_omega$periodicInfoFine(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        Wrap wrap = Wraps.overworld();
        return wrap.enabled() ? PeriodicSimplex.sample(noise, u, v, offsets, wrap.period * 0.09) : original.call(noise, u, v, offsets);
    }
}
