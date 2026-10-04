package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.sky.PlanetProjection.Position;
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
                Vector3f rendered = new Quaternionf().rotateY((float) Math.toRadians(-90)).mul(LocalSky.celestialRotation(tod, lat, 0.0))
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
        LocalSky.Sample sunrise = LocalSky.sample(0, new Position(0.0, lat, 0.0));
        assertTrue(sunrise.sunX() > 0.9 && sunrise.sunY() > 0.0);
        LocalSky.Sample noon = LocalSky.sample(6000, new Position(0.0, lat, 0.0));
        assertEquals(0.0, noon.sunX(), 1e-9);
        assertTrue(noon.sunZ() > 0.0);
        assertEquals(Math.toRadians(45), noon.altitude(), 1e-9);
        assertEquals(Math.PI, noon.azimuth(), 1e-9);
        LocalSky.Sample southNoon = LocalSky.sample(6000, new Position(0.0, -lat, 0.0));
        assertTrue(southNoon.sunZ() < 0.0);
    }

    @Test
    void easternTimeZonesAreAhead() {
        LocalSky.Sample east = LocalSky.sample(0, new Position(0.25, 0.0, 0.0));
        assertEquals(6000.0, east.clock(), EPS);
        assertEquals(1.0, east.sunY(), 1e-9);
    }

    @Test
    void polesHaveTheSunOnTheHorizon() {
        for (long t = 0; t < 24000; t += 500) {
            assertEquals(0.0, LocalSky.sample(t, new Position(0.0, Math.PI / 2, 0.0)).sunY(), 1e-9);
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
    void meanPositionWrapsAcrossTheDateLineAndThePoles() {
        assertEquals(0.5, Math.abs(mean(0.45, 0.0, -0.45, 0.0).longitude()), 1e-9);
        assertEquals(0.0, mean(0.05, 0.0, -0.05, 0.0).longitude(), 1e-9);
        assertEquals(0.1, mean(0.1, 0.3).longitude(), 1e-9);
        assertEquals(0.3, mean(0.1, 0.3).latitude(), 1e-9);
        assertNull(mean(0.0, 0.0, 0.5, 0.0));
        // Either side of the north pole: the mean is the pole.
        assertEquals(Math.PI / 2, mean(0.1, 1.4, -0.4, 1.4).latitude(), 1e-9);
    }

    /** The mean of (longitude, latitude) pairs. */
    private static Position mean(double... pairs) {
        Position[] positions = new Position[pairs.length / 2];
        for (int i = 0; i < positions.length; i++) positions[i] = new Position(pairs[2 * i], pairs[2 * i + 1], 0.0);
        return LocalSky.meanPosition(positions);
    }

    @Test
    void walkingNorthTiltsTheSkyOneWayRoundTheWholePlanet() {
        double previous = Double.NaN;
        for (double z = 0; z >= -W; z -= 16) {
            Vector3f pole = rendered(at(1000, 0, z), new Vector3f(1, 0, 0));
            double elevation = Math.atan2(pole.y, -pole.z);
            if (!Double.isNaN(previous)) {
                double change = elevation - previous;
                change -= 2.0 * Math.PI * Math.floor(change / (2.0 * Math.PI) + 0.5);
                assertEquals(2.0 * Math.PI * 16 / W, change, 1e-4);
            }
            previous = elevation;
        }
    }

    @Test
    void walkingEastTurnsTheSkyOneWayEverywhere() {
        for (double z = -W / 2.0; z < W / 2.0; z += 97) {
            for (double x = 0; x < W; x += 13) {
                double now = at(1000, x, z).celestialTime();
                double next = at(1000, x + 13, z).celestialTime();
                double change = next - now;
                change -= Math.floor(change + 0.5);
                assertTrue(change > 0 && change < 0.01, "celestial time moved by " + change);
            }
        }
    }

    @Test
    void theSkyMovesSmoothlyOverThePoles() {
        for (long t = 0; t < 24000; t += 250) {
            for (double z = -W; z <= W; z += 4) {
                LocalSky.Sample a = at(t, 0.13 * W, z);
                LocalSky.Sample b = at(t, 0.13 * W, z + 4);
                double moved = Math.sqrt(Math.pow(a.sunX() - b.sunX(), 2) + Math.pow(a.sunY() - b.sunY(), 2) + Math.pow(a.sunZ() - b.sunZ(), 2));
                assertTrue(moved < 0.02, "sun jumped by " + moved + " at z " + z + ", t " + t);
                Vector3f star = new Vector3f(0.3F, 0.5F, 0.81F).normalize();
                assertTrue(rendered(a, new Vector3f(star)).distance(rendered(b, new Vector3f(star))) < 0.02, "stars jumped at z " + z);
            }
        }
    }

    @Test
    void theFarSideIsTwelveHoursOffWithVanillaDays() {
        for (long t = 0; t < 24000; t += 37) {
            LocalSky.Sample far = at(t, 0, W / 2.0);
            assertEquals(Math.floorMod(t + 12000, 24000), Math.floorMod((long) Math.floor(far.clock() + 1e-6), 24000L));
            assertEquals(Math.cos(2.0 * Math.PI * vanillaTimeOfDay(t + 12000)), far.sunY(), 1e-5);
            assertEquals(vanillaTimeOfDay(t + 12000), far.timeOfDay(), 1e-6);
            assertEquals(far.timeOfDay(), far.equivalentTimeOfDay(), 1e-5);
        }
        LocalSky.Sample sunrise = at(12000, 0, W / 2.0);
        // Having walked over the pole the traveller faces geographic south, so the sun rises toward -X.
        assertTrue(sunrise.sunX() < -0.9 && sunrise.sunY() > 0.0);
    }

    @Test
    void theRenderedSkyMatchesTheSunAnywhere() {
        for (double z = -W; z < W; z += 333) {
            for (double x = 0; x < W; x += 777) {
                for (long t = 0; t < 24000; t += 1100) {
                    LocalSky.Sample sun = at(t, x, z);
                    Vector3f rendered = rendered(sun, new Vector3f(0, 1, 0));
                    assertEquals(sun.sunX(), rendered.x, 1e-4);
                    assertEquals(sun.sunY(), rendered.y, 1e-4);
                    assertEquals(sun.sunZ(), rendered.z, 1e-4);
                }
            }
        }
    }

    private static LocalSky.Sample at(long dayTime, double x, double z) {
        return LocalSky.sample(dayTime, project(x, z, W));
    }

    /** A direction on the celestial sphere as the sky renderer draws it, in world axes. */
    private static Vector3f rendered(LocalSky.Sample sun, Vector3f direction) {
        return new Quaternionf().rotateY((float) Math.toRadians(-90)).mul(LocalSky.celestialRotation(sun)).transform(direction);
    }

    @Test
    void sleepSkipsToVanillaMorningAtTheEquator() {
        for (long t = 12000; t < 24000; t += 100) {
            long add = LocalSky.sleepTimeAddition(t, Position.ORIGIN);
            assertEquals(0L, (t + add) % 24000L);
        }
        assertEquals(24000L, LocalSky.sleepTimeAddition(48000L, Position.ORIGIN));
    }

    @Test
    void sleepEndsInDaylightEverywhere() {
        for (double lat = -Math.PI / 2; lat <= Math.PI / 2; lat += 0.05) {
            for (double lon = -0.5; lon < 0.5; lon += 0.07) {
                for (long t = 0; t < 48000; t += 1700) {
                    long add = LocalSky.sleepTimeAddition(t, new Position(lon, lat, 0.0));
                    assertTrue(add > 0 && add <= 24000, () -> "addition " + add);
                    LocalSky.Sample woken = LocalSky.sample(t + add, new Position(lon, lat, 0.0));
                    if (Math.abs(lat) < Math.toRadians(85)) {
                        assertTrue(woken.sunY() > LocalSky.DAY_SUN_HEIGHT);
                    }
                }
            }
        }
    }

    /** The projection at a world position, as LocalSky feeds it. */
    private static Position project(double x, double z, int period) {
        if (period <= 0) return Position.ORIGIN;
        return PlanetProjection.project(LocalSky.lapFraction(x, period), LocalSky.lapFraction(z, period));
    }
}
