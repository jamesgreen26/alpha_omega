package g_mungus.alpha_omega.wrap.noise;

import g_mungus.alpha_omega.mixin.worldgen.noise.NormalNoiseAccessor;
import g_mungus.alpha_omega.mixin.worldgen.noise.PerlinNoiseAccessor;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * Configures vanilla lattice noise to repeat with a given period (design doc §12). Periods are in the units of
 * the noise's own input: a density function sampling {@code noise(x * s)} needs period {@code W * s}.
 */
public final class NoisePeriods {

    /** {@code NormalNoise} samples its second octave set at this multiple of the input. */
    private static final double NORMAL_NOISE_INPUT_FACTOR = 1.0181268882175227;
    private static final double EPSILON = 1e-9;

    private NoisePeriods() {
    }

    public static void configure(ImprovedNoise noise, double px, double py, double pz) {
        ((PeriodicLattice) (Object) noise).alpha_omega$setPeriod(px, py, pz);
    }

    public static void configure(PerlinNoise noise, double px, double py, double pz) {
        PerlinNoiseAccessor accessor = (PerlinNoiseAccessor) noise;
        ImprovedNoise[] octaves = accessor.alpha_omega$getNoiseLevels();
        double frequency = accessor.alpha_omega$getLowestFreqInputFactor();
        for (ImprovedNoise octave : octaves) {
            if (octave != null) configure(octave, px * frequency, py * frequency, pz * frequency);
            frequency *= 2.0;
        }
    }

    /**
     * Makes {@code noise} periodic, merging with any periods it already has on other axes.
     *
     * @return false if it is already periodic with a different period on some axis (it is then unchanged)
     */
    public static boolean configure(NormalNoise noise, double px, double py, double pz) {
        PeriodicNormalNoise state = (PeriodicNormalNoise) noise;
        double[] current = state.alpha_omega$getPeriods();
        double[] merged = {px, py, pz};
        if (current != null) {
            for (int i = 0; i < 3; i++) {
                if (merged[i] == 0) {
                    merged[i] = current[i];
                } else if (current[i] != 0 && Math.abs(current[i] - merged[i]) > EPSILON * merged[i]) {
                    return false;
                }
            }
        }
        NormalNoiseAccessor accessor = (NormalNoiseAccessor) noise;
        configure(accessor.alpha_omega$getFirst(), merged[0], merged[1], merged[2]);
        double f = NORMAL_NOISE_INPUT_FACTOR;
        configure(accessor.alpha_omega$getSecond(), merged[0] * f, merged[1] * f, merged[2] * f);
        state.alpha_omega$setPeriods(merged);
        return true;
    }
}
