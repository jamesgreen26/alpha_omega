package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import g_mungus.alpha_omega.wrap.noise.PeriodicSimplex;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Swamp grass color patches, sampled at {@code pos * 0.0225}. Client side, in lifted coordinates. */
@Mixin(targets = "net.minecraft.world.level.biome.BiomeSpecialEffects$GrassColorModifier$3")
abstract class SwampGrassColorModifierMixin {

    @WrapOperation(method = "modifyColor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/PerlinSimplexNoise;getValue(DDZ)D"))
    private double alpha_omega$periodic(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        Wrap wrap = Wraps.overworld();
        return wrap.enabled() ? PeriodicSimplex.sample(noise, u, v, offsets, wrap.period * 0.0225) : original.call(noise, u, v, offsets);
    }
}
