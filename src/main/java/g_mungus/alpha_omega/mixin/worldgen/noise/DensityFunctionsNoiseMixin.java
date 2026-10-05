package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseUser;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Samples {@code noise(x * xzScale, y * yScale, z * xzScale)}: a scalar, even about the cone points. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
abstract class DensityFunctionsNoiseMixin implements InvariantNoiseUser {

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
