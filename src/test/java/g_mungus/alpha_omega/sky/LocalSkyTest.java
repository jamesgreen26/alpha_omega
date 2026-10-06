package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import java.util.Random;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** The pure math of {@link LocalSky}, on its own and over the default orbifold world's projection. */
class LocalSkyTest {

    private static final OrbifoldGeometry WORLD = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
    private static final PlanetProjection.Projection PROJECTION = PlanetProjection.of(WORLD);

    /** Vanilla {@code DimensionType.timeOfDay}, copied verbatim. */
    private static float vanillaTimeOfDay(long dayTime) {
        double d0 = net.minecraft.util.Mth.frac((double) dayTime / 24000.0 - 0.25);
        double d1 = 0.5 - Math.cos(d0 * Math.PI) / 2.0;
        return (float) (d0 * 2.0 + d1) / 3.0F;
    }

    private static LocalSky.Sample at(long dayTime, double x, double z) {
        return LocalSky.sample(dayTime, PROJECTION.project(x, z));
    }

    /** A direction on the celestial sphere as the sky renderer draws it, in world axes. */
    private static Vector3f rendered(long dayTime, double x, double z, Vector3f direction) {
        Quaternionf rotation = LocalSky.celestialRotation(dayTime, PROJECTION.project(x, z));
        return new Quaternionf().rotateY((float) Math.toRadians(-90)).mul(rotation).transform(direction);
    }

    private static double distance(LocalSky.Sample a, LocalSky.Sample b) {
        return Math.sqrt(Math.pow(a.sunX() - b.sunX(), 2) + Math.pow(a.sunY() - b.sunY(), 2) + Math.pow(a.sunZ() - b.sunZ(), 2));
    }

    // ---- The sun at a point on the planet ----

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
            assertEquals(tod, LocalSky.equivalentTimeOfDay(sun[0] > 0.0, sun[1]), 1e-6);
        }
    }

    /** Vanilla consumers that read cos(2π·timeOfDay) get the sun's height; the morning half is mirrored. */
    @Test
    void equivalentTimeOfDayGivesTheSunHeight() {
        for (long t = 0; t < 24000; t += 300) {
            LocalSky.Sample sample = LocalSky.sample(t, new Position(0.1, 0.7, 0.4));
            assertEquals(sample.sunY(), Math.cos(2 * Math.PI * sample.equivalentTimeOfDay()), 1e-9);
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
        assertEquals(6000.0, east.clock(), 1e-9);
        assertEquals(-0.25, east.timeZone(), 1e-12);
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
            double clockStep = Math.IEEEremainder(step.clock() - previous.clock(), 24000.0);
            assertTrue(clockStep < 0 && clockStep >= -301.0, "clock went the long way: " + step.clock() + " after " + previous.clock());
            previous = step;
        }
    }

    // ---- Sleep ----

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

    /** At the north pole cone point the sun never rises far enough: sleep lasts until local noon. */
    @Test
    void sleepAtTheNorthPoleLastsUntilNoon() {
        Position pole = PROJECTION.project(0.0, WORLD.northRow);
        for (long t = 0; t < 24000; t += 700) {
            long add = LocalSky.sleepTimeAddition(t, pole);
            assertEquals(6000L, Math.floorMod(t + add + Math.round(24000.0 * pole.longitude()), 24000L));
        }
    }

    // ---- Over the orbifold ----

    /** At spawn the sun is vanilla's, at every time of day. */
    @Test
    void spawnHasVanillasSun() {
        for (long t = 0; t < 48000; t += 37) {
            LocalSky.Sample sun = at(t, WORLD.spawnX + 0.5, WORLD.spawnZ + 0.5);
            LocalSky.Sample vanilla = LocalSky.vanilla(t);
            // The middle of the spawn block is half a block east of the meridian: under a tick ahead.
            assertEquals(t, sun.clock(), 1.0);
            assertTrue(distance(sun, vanilla) < 1e-3, "sun at spawn off vanilla's by " + distance(sun, vanilla) + " at " + t);
            assertEquals(0.0, Math.IEEEremainder(vanilla.equivalentTimeOfDay() - sun.equivalentTimeOfDay(), 1.0), 1e-3);
            Vector3f rendered = rendered(t, WORLD.spawnX + 0.5, WORLD.spawnZ + 0.5, new Vector3f(0.3F, 0.5F, 0.81F));
            Vector3f vanillaRendered = new Quaternionf().rotateY((float) Math.toRadians(-90)).rotateX((float) (2 * Math.PI * vanillaTimeOfDay(t)))
                .transform(new Vector3f(0.3F, 0.5F, 0.81F));
            assertTrue(rendered.distance(vanillaRendered) < 1e-3, "stars at spawn off vanilla's at " + t);
        }
    }

    /** A band position has the same sun as its source, turned with its frame: the same sky over the same blocks. */
    @Test
    void bandPositionsHaveTheSunOfTheirSource() {
        Random random = new Random(4);
        for (int i = 0; i < 500; i++) {
            double x = WORLD.minX - WORLD.reach + random.nextDouble() * (WORLD.a + 2.0 * WORLD.reach);
            double z = WORLD.northRow - WORLD.reach + random.nextDouble() * (WORLD.b / 2.0 + 2.0 * WORLD.reach);
            if (WORLD.seamDepth(x, z) <= 0) continue;
            Motion frame = WORLD.frame(x, z);
            long t = random.nextInt(48000);
            LocalSky.Sample band = at(t, x, z);
            LocalSky.Sample source = at(t, frame.pointX(x), frame.pointZ(z));
            assertEquals(source.clock(), band.clock(), 1e-6);
            assertEquals(source.sunY(), band.sunY(), 1e-9);
            assertEquals(source.sunX(), frame.vectorX(band.sunX()), 1e-9);
            assertEquals(source.sunZ(), frame.vectorZ(band.sunZ()), 1e-9);
            Vector3f star = new Vector3f(0.3F, 0.5F, 0.81F).normalize();
            Vector3f bandStar = rendered(t, x, z, new Vector3f(star));
            Vector3f sourceStar = rendered(t, frame.pointX(x), frame.pointZ(z), new Vector3f(star));
            assertEquals(sourceStar.x, (float) frame.vectorX(bandStar.x), 1e-4);
            assertEquals(sourceStar.z, (float) frame.vectorZ(bandStar.z), 1e-4);
        }
    }

    @Test
    void theRenderedSkyMatchesTheSunAnywhere() {
        for (double z = WORLD.northRow; z < WORLD.southRow; z += 333) {
            for (double x = WORLD.minX; x < WORLD.maxX; x += 777) {
                for (long t = 0; t < 24000; t += 1100) {
                    LocalSky.Sample sun = at(t, x, z);
                    Vector3f rendered = rendered(t, x, z, new Vector3f(0, 1, 0));
                    String where = x + ", " + z + " at " + t + ": " + PROJECTION.project(x, z);
                    assertEquals(sun.sunX(), rendered.x, 1e-4, where);
                    assertEquals(sun.sunY(), rendered.y, 1e-4, where);
                    assertEquals(sun.sunZ(), rendered.z, 1e-4, where);
                }
            }
        }
    }

    /**
     * Walking a straight line past the north pole, the sun and stars move without jumping. Near a cone point the sky
     * swings round the zenith fast (it turns twice on a walk round the point), so the step allowed grows as the line
     * passes closer.
     */
    @Test
    void theSkyMovesSmoothlyPastTheNorthPole() {
        for (double offset : new double[] {600, 150, 40}) {
            for (long t = 0; t < 24000; t += 1000) {
                LocalSky.Sample previous = null;
                Vector3f previousStar = null;
                for (double z = WORLD.northRow - 1500; z <= WORLD.northRow + 1500; z += 1) {
                    LocalSky.Sample sun = at(t, offset, z);
                    Vector3f star = rendered(t, offset, z, new Vector3f(0.3F, 0.5F, 0.81F).normalize());
                    if (previous != null) {
                        double allowed = 2.0 / offset;
                        assertTrue(distance(previous, sun) < allowed, "sun jumped by " + distance(previous, sun) + " at z " + z + ", t " + t);
                        assertTrue(previousStar.distance(star) < allowed, "stars jumped at z " + z + ", t " + t);
                    }
                    previous = sun;
                    previousStar = star;
                }
            }
        }
    }

    /** Walking the meridian north from spawn the day shortens: at 63° N the noon sun stands 27° up, due south. */
    @Test
    void theNoonSunSinksTowardThePole() {
        LocalSky.Sample noon = at(6000, WORLD.spawnX, WORLD.spawnZ - 2600);
        assertEquals(90.0 - 63.06, Math.toDegrees(noon.altitude()), 0.05);
        assertEquals(Math.PI, noon.azimuth(), 1e-6);
        LocalSky.Sample pole = at(6000, 0.5, WORLD.northRow + 0.5);
        assertTrue(Math.abs(pole.sunY()) < 1e-4, "sun at the pole should be on the horizon: " + pole.sunY());
    }
}
