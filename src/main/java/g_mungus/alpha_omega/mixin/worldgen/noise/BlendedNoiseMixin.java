package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseUser;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * The base 3D terrain noise drives its octaves directly, at {@code x * xzMultiplier / xzFactor / 2^i} for the
 * main noise and {@code x * xzMultiplier / 2^j} for the limit noises. It is instantiated per {@code RandomState},
 * so its octaves are configured in place.
 */
@Mixin(BlendedNoise.class)
abstract class BlendedNoiseMixin implements PeriodicNoiseUser {

    @Shadow
    @Final
    private PerlinNoise minLimitNoise;
    @Shadow
    @Final
    private PerlinNoise maxLimitNoise;
    @Shadow
    @Final
    private PerlinNoise mainNoise;
    @Shadow
    @Final
    private double xzMultiplier;
    @Shadow
    @Final
    private double xzFactor;

    @Override
    public void alpha_omega$makePeriodic(PeriodicNoiseSource source) {
        int period = source.alpha_omega$wrap().period;
        double scale = 1.0;
        for (int i = 0; i < 8; i++) {
            alpha_omega$configure(this.mainNoise.getOctaveNoise(i), period * this.xzMultiplier / this.xzFactor * scale);
            scale /= 2.0;
        }
        scale = 1.0;
        for (int j = 0; j < 16; j++) {
            double limitPeriod = period * this.xzMultiplier * scale;
            alpha_omega$configure(this.minLimitNoise.getOctaveNoise(j), limitPeriod);
            alpha_omega$configure(this.maxLimitNoise.getOctaveNoise(j), limitPeriod);
            scale /= 2.0;
        }
    }

    @Unique
    private static void alpha_omega$configure(ImprovedNoise octave, double period) {
        if (octave != null) NoisePeriods.configure(octave, period, 0, period);
    }
}
