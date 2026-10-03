package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.noise.PeriodicLattice;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Vanilla keeps octave inputs small by subtracting multiples of 2^25 once they pass 2^24. That is not a multiple
 * of a periodic octave's period, so it would break periodicity for high-frequency octaves (e.g. the jagged
 * mountain peaks noise, sampled at 1500x). Reduce those inputs by their own period instead, which is exact.
 */
@Mixin(PerlinNoise.class)
abstract class PerlinNoiseMixin {

    @Shadow @Final private ImprovedNoise[] noiseLevels;
    @Shadow @Final private DoubleList amplitudes;
    @Shadow @Final private double lowestFreqValueFactor;
    @Shadow @Final private double lowestFreqInputFactor;

    @Shadow
    public static double wrap(double input) {
        throw new AssertionError();
    }

    /**
     * @author alpha_omega
     * @reason The reduction depends on the octave being sampled, which an injector can only reach through the
     * local variable table, and production Minecraft's local names are obfuscated. Identical to vanilla for
     * octaves that are not periodic.
     */
    @Overwrite
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useFixedY) {
        double total = 0.0;
        double frequency = this.lowestFreqInputFactor;
        double amplitude = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise octave = this.noiseLevels[i];
            if (octave != null) {
                PeriodicLattice lattice = (PeriodicLattice) (Object) octave;
                double value = octave.noise(
                    alpha_omega$reduce(x * frequency, lattice.alpha_omega$inputPeriodX()),
                    useFixedY ? -octave.yo : wrap(y * frequency),
                    alpha_omega$reduce(z * frequency, lattice.alpha_omega$inputPeriodZ()),
                    yScale * frequency,
                    yMax * frequency
                );
                total += this.amplitudes.getDouble(i) * value * amplitude;
            }

            frequency *= 2.0;
            amplitude /= 2.0;
        }

        return total;
    }

    @Unique
    private static double alpha_omega$reduce(double input, double period) {
        if (period == 0) return wrap(input);
        double reduced = input % period;
        return reduced < 0 ? reduced + period : reduced;
    }
}
