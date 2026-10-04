package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LocalSkyTest {

    private static final int W = 12288;
    private static final double EPS = 1e-9;

    /** Vanilla {@code DimensionType.timeOfDay}, copied verbatim. */
    private static float vanillaTimeOfDay(long dayTime) {
        double d0 = net.minecraft.util.Mth.frac((double) dayTime / 24000.0 - 0.25);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (float) (d0 * 2.0 + d1) / 3.0F;
    }

    @Test
    void latitudeIsATriangleWave() {
        assertEquals(0.0, LocalSky.latitude(0, W), EPS);
        assertEquals(Math.PI / 2, LocalSky.latitude(-W / 4.0, W), EPS);
        assertEquals(0.0, Math.abs(LocalSky.latitude(-W / 2.0, W)), EPS);
        assertEquals(0.0, Math.abs(LocalSky.latitude(W / 2.0, W)), EPS);
        assertEquals(-Math.PI / 2, LocalSky.latitude(W / 4.0, W), EPS);
        assertEquals(Math.PI / 4, LocalSky.latitude(-W / 8.0, W), EPS);
    }

    @Test
    void latitudeIsContinuousAndPeriodic() {
        double step = 0.25;
        double maxSlope = 2.0 * Math.PI / W;
        for (double z = -2.0 * W; z <= 2.0 * W; z += 7.3) {
            double lat = LocalSky.latitude(z, W);
            assertEquals(lat, LocalSky.latitude(z + W, W), 1e-9);
            assertEquals(lat, LocalSky.latitude(z - 3.0 * W, W), 1e-9);
            assertTrue(Math.abs(LocalSky.latitude(z + step, W) - lat) <= maxSlope * step + 1e-12);
            assertEquals(Math.asin(Math.sin(-2.0 * Math.PI * z / W)), lat, 1e-6);
        }
    }

    @Test
    void longitudeIsPeriodicAndCentred() {
        assertEquals(0.0, LocalSky.longitude(0, W), EPS);
        assertEquals(0.25, LocalSky.longitude(W / 4.0, W), EPS);
        assertEquals(-0.25, LocalSky.longitude(-W / 4.0, W), EPS);
        for (double x = -3.0 * W; x <= 3.0 * W; x += 101.7) {
            double lon = LocalSky.longitude(x, W);
            assertTrue(lon >= -0.5 && lon < 0.5);
            assertEquals(lon, LocalSky.longitude(x + W, W), 1e-9);
        }
    }

    @Test
    void unwrappedWorldsAreVanilla() {
        assertEquals(0.0, LocalSky.longitude(12345, 0), EPS);
        assertEquals(0.0, LocalSky.latitude(-6789, 0), EPS);
    }

    @Test
    void timeOfDayMatchesVanilla() {
        for (long t = -48000; t <= 48000; t += 37) {
            assertEquals(vanillaTimeOfDay(t), LocalSky.timeOfDay(t), 1e-6);
        }
    }

    @Test
    void equatorMatchesVanillaSkyRotation() {
        for (long t = 0; t < 24000; t += 24) {
            double tod = LocalSky.timeOfDay(t);
            double[] sun = LocalSky.sunDirection(tod, 0.0);
            Vector3f vanilla = new Quaternionf().rotateY((float) Math.toRadians(-90)).rotateX((float) (2 * Math.PI * tod))
                .transform(new Vector3f(0, 1, 0));
            assertEquals(vanilla.x, sun[0], 1e-5);
            assertEquals(vanilla.y, sun[1], 1e-5);
            assertEquals(vanilla.z, sun[2], 1e-5);
            assertEquals(tod, LocalSky.equivalentTimeOfDay(sun[0], sun[1]), 1e-6);
        }
    }

    @Test
    void celestialRotationMatchesSunDirection() {
        for (double lat = -Math.PI / 2; lat <= Math.PI / 2; lat += 0.1) {
            for (double tod = 0; tod < 1; tod += 0.013) {
                double[] sun = LocalSky.sunDirection(tod, lat);
                Vector3f rendered = new Quaternionf().rotateY((float) Math.toRadians(-90)).mul(LocalSky.celestialRotation(tod, lat))
                    .transform(new Vector3f(0, 1, 0));
                assertEquals(sun[0], rendered.x, 1e-5);
                assertEquals(sun[1], rendered.y, 1e-5);
                assertEquals(sun[2], rendered.z, 1e-5);
            }
        }
    }

    @Test
    void sunRisesInTheEastAndStandsSouthAtNoonInTheNorth() {
        double lat = Math.toRadians(45);
        LocalSky.Sample sunrise = LocalSky.sample(0, 0.0, lat);
        assertTrue(sunrise.sunX() > 0.9 && sunrise.sunY() > 0.0);
        LocalSky.Sample noon = LocalSky.sample(6000, 0.0, lat);
        assertEquals(0.0, noon.sunX(), 1e-9);
        assertTrue(noon.sunZ() > 0.0);
        assertEquals(Math.toRadians(45), noon.altitude(), 1e-9);
        assertEquals(Math.PI, noon.azimuth(), 1e-9);
        LocalSky.Sample southNoon = LocalSky.sample(6000, 0.0, -lat);
        assertTrue(southNoon.sunZ() < 0.0);
    }

    @Test
    void easternTimeZonesAreAhead() {
        LocalSky.Sample east = LocalSky.sample(0, 0.25, 0.0);
        assertEquals(6000.0, east.clock(), EPS);
        assertEquals(1.0, east.sunY(), 1e-9);
    }

    @Test
    void polesHaveTheSunOnTheHorizon() {
        for (long t = 0; t < 24000; t += 500) {
            assertEquals(0.0, LocalSky.sample(t, 0.0, Math.PI / 2).sunY(), 1e-9);
        }
    }

    @Test
    void skyDarkenMatchesVanillaThresholds() {
        assertEquals(0, LocalSky.skyDarken(1.0, 0, 0));
        assertEquals(11, LocalSky.skyDarken(-1.0, 0, 0));
        assertEquals(5, LocalSky.skyDarken(0.0, 0, 0));
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT + 1e-6, 0, 0) < 4);
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT - 1e-6, 0, 0) >= 4);
    }

    @Test
    void meanLongitudeWrapsAcrossTheDateLine() {
        assertEquals(-0.5, Math.abs(LocalSky.meanLongitude(new double[] {0.45, -0.45})) * -1, 1e-9);
        assertEquals(0.0, LocalSky.meanLongitude(new double[] {0.05, -0.05}), 1e-9);
        assertEquals(0.1, LocalSky.meanLongitude(new double[] {0.1}), 1e-9);
        assertTrue(Double.isNaN(LocalSky.meanLongitude(new double[] {0.0, 0.5})));
    }

    @Test
    void sleepSkipsToVanillaMorningAtTheEquator() {
        for (long t = 12000; t < 24000; t += 100) {
            long add = LocalSky.sleepTimeAddition(t, 0.0, 0.0);
            assertEquals(0L, (t + add) % 24000L);
        }
        assertEquals(24000L, LocalSky.sleepTimeAddition(48000L, 0.0, 0.0));
    }

    @Test
    void sleepEndsInDaylightEverywhere() {
        for (double lat = -Math.PI / 2; lat <= Math.PI / 2; lat += 0.05) {
            for (double lon = -0.5; lon < 0.5; lon += 0.07) {
                for (long t = 0; t < 48000; t += 1700) {
                    long add = LocalSky.sleepTimeAddition(t, lon, lat);
                    assertTrue(add > 0 && add <= 24000, () -> "addition " + add);
                    LocalSky.Sample woken = LocalSky.sample(t + add, lon, lat);
                    if (Math.abs(lat) < Math.toRadians(85)) {
                        assertTrue(woken.sunY() > LocalSky.DAY_SUN_HEIGHT);
                    }
                }
            }
        }
    }
}
