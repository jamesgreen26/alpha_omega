package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pure math of {@link LocalSky}. Phase 2 adds the projection's tests. */
class LocalSkyTest {

    @Test
    void timeOfDayMatchesVanilla() {
        // DimensionType.timeOfDay: noon at 6000, sunset just after 12000, midnight at 18000.
        assertEquals(0.0, LocalSky.timeOfDay(6000), 1e-12);
        assertEquals(0.5, LocalSky.timeOfDay(18000), 1e-12);
        assertEquals(LocalSky.timeOfDay(1000), LocalSky.timeOfDay(25000), 1e-12);
    }

    /** Vanilla consumers that read cos(2π·timeOfDay) get the sun's height; the morning half is mirrored. */
    @Test
    void equivalentTimeOfDayGivesTheSunHeight() {
        for (int t = 0; t < 24000; t += 300) {
            LocalSky.Sample sample = LocalSky.vanilla(t);
            double equivalent = LocalSky.equivalentTimeOfDay(sample.timeOfDay() > 0.5, sample.sunY());
            assertEquals(sample.sunY(), Math.cos(2 * Math.PI * equivalent), 1e-9);
            assertEquals(sample.equivalentTimeOfDay(), equivalent, 1e-9);
        }
    }

    @Test
    void skyDarkenMatchesVanillaThresholds() {
        assertEquals(0, LocalSky.skyDarken(1.0, 0.0F, 0.0F));
        assertEquals(11, LocalSky.skyDarken(-1.0, 0.0F, 0.0F));
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT + 1e-6, 0.0F, 0.0F) < 4);
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT - 1e-3, 0.0F, 0.0F) >= 4);
    }

    /** Easing between two samples: the ends are the samples themselves, and the way between is smooth. */
    @Test
    void blendEasesBetweenSamples() {
        LocalSky.Sample from = LocalSky.vanilla(23000);
        LocalSky.Sample to = LocalSky.vanilla(5000);
        assertEquals(to, LocalSky.blend(from, to, 0.0));
        assertEquals(from, LocalSky.blend(from, to, 1.0));
        LocalSky.Sample previous = to;
        for (int i = 1; i <= 20; i++) {
            LocalSky.Sample step = LocalSky.blend(from, to, i / 20.0);
            assertEquals(1.0, Math.sqrt(step.sunX() * step.sunX() + step.sunY() * step.sunY() + step.sunZ() * step.sunZ()), 1e-9);
            double turn = Math.acos(Math.min(1.0, step.sunX() * previous.sunX() + step.sunY() * previous.sunY() + step.sunZ() * previous.sunZ()));
            assertTrue(turn < Math.PI / 8, "the sun jumped " + Math.toDegrees(turn) + " degrees in one step");
            // The clock goes the short way round (6000 ticks back, in 20 steps), not 18000 forward.
            double clockStep = Math.IEEEremainder(step.clock() - previous.clock(), 24000.0);
            assertTrue(clockStep < 0 && clockStep >= -301.0, "clock went the long way: " + step.clock() + " after " + previous.clock());
            previous = step;
        }
    }
}
