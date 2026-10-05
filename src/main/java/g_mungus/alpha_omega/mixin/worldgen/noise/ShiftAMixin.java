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
 * Samples {@code offsetNoise(x / 4, 0, z / 4) * 4}: {@code shift_x}, a displacement along x, which a half turn negates.
 * So its noise is odd about the cone points.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
abstract class ShiftAMixin implements InvariantNoiseUser {

    @Shadow
    @Final
    @Mutable
    private DensityFunction.NoiseHolder offsetNoise;

    @Override
    public void alpha_omega$makeInvariant(InvariantNoiseSource source) {
        this.offsetNoise = source.alpha_omega$invariant(this.offsetNoise,
            new NoiseSymmetry(0.25, NoiseSymmetry.Axes.XZ, NoiseSymmetry.Parity.ODD));
    }
}
