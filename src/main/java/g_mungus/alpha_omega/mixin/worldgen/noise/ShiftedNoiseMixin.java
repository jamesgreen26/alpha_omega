package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseUser;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Samples {@code noise(x * xzScale + shiftX, y * yScale + shiftY, z * xzScale + shiftZ)}. The shifts are odd about the
 * cone points ({@code ShiftAMixin}, {@code ShiftBMixin}), so a half turn of the position half-turns the shifted input
 * about the cone point too, and an even noise of it stays even.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
abstract class ShiftedNoiseMixin implements InvariantNoiseUser {

    @Shadow
    @Final
    @Mutable
    private DensityFunction.NoiseHolder noise;

    @Shadow
    @Final
    private double xzScale;

    @Override
    public void alpha_omega$makeInvariant(InvariantNoiseSource source) {
        if (this.xzScale != 0.0) this.noise = source.alpha_omega$invariant(this.noise, NoiseSymmetry.even(this.xzScale));
    }
}
