package g_mungus.alpha_omega.wrap.noise;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;

/**
 * Simplex noise sits on a skewed lattice that cannot be wrapped, so it is made periodic by cross-fading
 * between the images of the sample point instead. Used only for small cosmetic noises (snow lines, grass
 * color, flower density); it costs four samples and slightly flattens the noise mid-period.
 */
public final class PeriodicSimplex {

    private PeriodicSimplex() {
    }

    /** {@code noise} sampled at {@code (u, v)}, made periodic with period {@code period} on both axes. */
    public static double sample(PerlinSimplexNoise noise, double u, double v, boolean useNoiseOffsets, double period) {
        double a = mod(u, period);
        double b = mod(v, period);
        double t = Mth.smoothstep(a / period);
        double s = Mth.smoothstep(b / period);
        double f00 = noise.getValue(a, b, useNoiseOffsets);
        double f10 = noise.getValue(a - period, b, useNoiseOffsets);
        double f01 = noise.getValue(a, b - period, useNoiseOffsets);
        double f11 = noise.getValue(a - period, b - period, useNoiseOffsets);
        return Mth.lerp(s, Mth.lerp(t, f00, f10), Mth.lerp(t, f01, f11));
    }

    private static double mod(double x, double period) {
        double m = x % period;
        return m < 0 ? m + period : m;
    }
}
