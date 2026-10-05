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
 * Samples {@code offsetNoise(z / 4, x / 4, 0) * 4}: {@code shift_z}, odd about the cone points like {@code shift_x}, but
 * with world z on the noise's x axis and world x on its y axis ({@link NoiseSymmetry.Axes#SHIFT_B}). It shares its noise
 * with {@code shift_a}, so it gets a copy made for this use.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB")
abstract class ShiftBMixin implements InvariantNoiseUser {

    @Shadow
    @Final
    @Mutable
    private DensityFunction.NoiseHolder offsetNoise;

    @Override
    public void alpha_omega$makeInvariant(InvariantNoiseSource source) {
        this.offsetNoise = source.alpha_omega$invariant(this.offsetNoise,
            new NoiseSymmetry(0.25, NoiseSymmetry.Axes.SHIFT_B, NoiseSymmetry.Parity.ODD));
    }
}
