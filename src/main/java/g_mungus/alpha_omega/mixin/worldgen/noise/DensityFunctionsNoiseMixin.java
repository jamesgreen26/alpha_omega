package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseUser;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Samples {@code noise(x * xzScale, y * yScale, z * xzScale)}. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
abstract class DensityFunctionsNoiseMixin implements PeriodicNoiseUser {

    @Shadow
    @Final
    @Mutable
    private DensityFunction.NoiseHolder noise;

    @Shadow
    @Final
    private double xzScale;
    @Override
    public void alpha_omega$makePeriodic(PeriodicNoiseSource source) {
        this.noise = source.alpha_omega$periodic(this.noise, Wrap.PERIOD * this.xzScale, 0, Wrap.PERIOD * this.xzScale);
    }
}
