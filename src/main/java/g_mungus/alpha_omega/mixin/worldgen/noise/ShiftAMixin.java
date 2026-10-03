package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseUser;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Samples {@code offsetNoise(x / 4, 0, z / 4)}. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
abstract class ShiftAMixin implements PeriodicNoiseUser {

    @Shadow
    @Final
    @Mutable
    private DensityFunction.NoiseHolder offsetNoise;

    @Override
    public void alpha_omega$makePeriodic(PeriodicNoiseSource source) {
        this.offsetNoise = source.alpha_omega$periodic(this.offsetNoise, Wrap.PERIOD / 4.0, 0, Wrap.PERIOD / 4.0);
    }
}
