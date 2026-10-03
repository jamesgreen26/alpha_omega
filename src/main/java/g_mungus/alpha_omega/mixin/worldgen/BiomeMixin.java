package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.PeriodicSimplex;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Height-adjusted temperature (snow line on mountains) samples simplex noise at {@code pos / 8}. */
@Mixin(Biome.class)
abstract class BiomeMixin {

    @WrapOperation(method = "getHeightAdjustedTemperature",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/PerlinSimplexNoise;getValue(DDZ)D"))
    private double alpha_omega$periodicTemperature(PerlinSimplexNoise noise, double u, double v, boolean offsets, Operation<Double> original) {
        return PeriodicSimplex.sample(noise, u, v, offsets, Wrap.PERIOD / 8.0);
    }
}
