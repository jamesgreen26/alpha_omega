package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.noise.PeriodicLattice;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla keeps octave inputs small by subtracting multiples of 2^25 once they pass 2^24. That is not a multiple
 * of a periodic octave's period, so it would break periodicity for high-frequency octaves (e.g. the jagged
 * mountain peaks noise, sampled at 1500x). Reduce those inputs by their own period instead, which is exact.
 */
@Mixin(PerlinNoise.class)
abstract class PerlinNoiseMixin {

    @Unique
    private static final String WRAP = "Lnet/minecraft/world/level/levelgen/synth/PerlinNoise;wrap(D)D";

    @WrapOperation(method = "getValue(DDDDDZ)D", at = @At(value = "INVOKE", target = WRAP, ordinal = 0))
    private double alpha_omega$wrapX(double input, Operation<Double> original, @Local ImprovedNoise octave) {
        return alpha_omega$reduce(input, ((PeriodicLattice) (Object) octave).alpha_omega$inputPeriodX(), original);
    }

    @WrapOperation(method = "getValue(DDDDDZ)D", at = @At(value = "INVOKE", target = WRAP, ordinal = 2))
    private double alpha_omega$wrapZ(double input, Operation<Double> original, @Local ImprovedNoise octave) {
        return alpha_omega$reduce(input, ((PeriodicLattice) (Object) octave).alpha_omega$inputPeriodZ(), original);
    }

    @Unique
    private static double alpha_omega$reduce(double input, double period, Operation<Double> original) {
        if (period == 0) return original.call(input);
        double reduced = input % period;
        return reduced < 0 ? reduced + period : reduced;
    }
}
