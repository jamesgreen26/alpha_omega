package g_mungus.alpha_omega.wrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.wrap.noise.PeriodicSimplex;
import java.util.List;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.Test;

class PeriodicSimplexTest {

    private static final PerlinSimplexNoise NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), List.of(0));

    @Test
    void isPeriodic() {
        double period = 384.0;
        for (double u = -500; u < 500; u += 13.7) {
            for (double v = -500; v < 500; v += 29.3) {
                double base = PeriodicSimplex.sample(NOISE, u, v, false, period);
                assertEquals(base, PeriodicSimplex.sample(NOISE, u + period, v, false, period), 1e-9);
                assertEquals(base, PeriodicSimplex.sample(NOISE, u, v - 2 * period, false, period), 1e-9);
            }
        }
    }

    @Test
    void isContinuousAcrossThePeriodBoundary() {
        double period = 153.6;
        for (double v = 0; v < period; v += 7.1) {
            double before = PeriodicSimplex.sample(NOISE, period - 1e-6, v, false, period);
            double after = PeriodicSimplex.sample(NOISE, period + 1e-6, v, false, period);
            assertTrue(Math.abs(before - after) < 1e-4, "jump at boundary: " + before + " vs " + after);
        }
    }
}
