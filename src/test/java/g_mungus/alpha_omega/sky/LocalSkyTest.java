package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeSettings.SunAxis;
import java.util.HashSet;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class LocalSkyTest {

    private static final CubeSun DIAGONAL = new CubeSun(SunAxis.DIAGONAL);
    private static final CubeSun POLAR = new CubeSun(SunAxis.POLAR);

    @Test
    void timeOfDayMatchesVanilla() {
        // DimensionType.timeOfDay: noon at 6000, sunset just after 12000, midnight at 18000.
        assertEquals(0.0, LocalSky.timeOfDay(6000), 1e-12);
        assertEquals(0.5, LocalSky.timeOfDay(18000), 1e-12);
        assertEquals(LocalSky.timeOfDay(1000), LocalSky.timeOfDay(25000), 1e-12);
    }

    @Test
    void dayTimeOfTimeOfDayInvertsIt() {
        for (int t = 0; t < 24000; t += 37) {
            assertEquals(t, CubeSun.dayTimeOfTimeOfDay(LocalSky.timeOfDay(t)), 1e-6);
        }
    }

    /** With the polar axis, UP sees vanilla's sun and sky rotation exactly. */
    @Test
    void polarUpIsVanilla() {
        for (int t = 0; t < 24000; t += 250) {
            double h = CubeSun.hourAngle(t);
            double[] sun = POLAR.direction(CubeFace.UP, h);
            LocalSky.Sample vanilla = LocalSky.vanilla(t);
            assertEquals(vanilla.sunX(), sun[0], 1e-9);
            assertEquals(vanilla.sunY(), sun[1], 1e-9);
            assertEquals(vanilla.sunZ(), sun[2], 1e-9);
            assertEquals(t, POLAR.localClock(CubeFace.UP, t), 1e-6);
            Quaternionf q = POLAR.celestialRotation(CubeFace.UP, h);
            Quaternionf rx = new Quaternionf().rotateX((float) h);
            assertTrue(Math.abs(q.dot(rx)) > 1 - 1e-6, "UP's celestial rotation should be vanilla's XP");
        }
    }

    /** The renderer's frame puts the sun where gameplay sees it, and keeps the pole fixed: stars turn about it. */
    @Test
    void celestialRotationMatchesTheSunAndPole() {
        for (CubeSun sun : new CubeSun[] {DIAGONAL, POLAR}) {
            for (CubeFace face : CubeFace.values()) {
                Vector3f pole = null;
                for (int t = 0; t < 24000; t += 700) {
                    double h = CubeSun.hourAngle(t);
                    Quaternionf frame = new Quaternionf().rotateY((float) (-Math.PI / 2)).mul(sun.celestialRotation(face, h));
                    Vector3f drawn = frame.transform(new Vector3f(0, 1, 0));
                    double[] expected = sun.direction(face, h);
                    assertEquals(expected[0], drawn.x, 1e-5);
                    assertEquals(expected[1], drawn.y, 1e-5);
                    assertEquals(expected[2], drawn.z, 1e-5);
                    Vector3f p = frame.transform(new Vector3f(1, 0, 0));
                    if (pole == null) pole = p;
                    else assertTrue(pole.distance(p) < 1e-5, face + ": the celestial pole should not move");
                }
            }
        }
    }

    /** Diagonal axis: every face has the same 54.7° noon, a day and a night, and the six noons are 4 hours apart. */
    @Test
    void diagonalGivesEveryFaceADayFourHoursApart() {
        Set<Long> zones = new HashSet<>();
        for (CubeFace face : CubeFace.values()) {
            double highest = -1, lowest = 1;
            for (int t = 0; t < 24000; t += 20) {
                double y = DIAGONAL.direction(face, CubeSun.hourAngle(t))[1];
                highest = Math.max(highest, y);
                lowest = Math.min(lowest, y);
            }
            assertEquals(Math.sqrt(2.0 / 3.0), highest, 1e-3, face + " noon height");
            assertEquals(-Math.sqrt(2.0 / 3.0), lowest, 1e-3, face + " midnight depth");
            assertEquals(Math.toDegrees(Math.asin(1 / Math.sqrt(3))), Math.abs(Math.toDegrees(DIAGONAL.latitude(face))), 1e-9);
            double hours = -24.0 * DIAGONAL.timeZone(face);
            assertEquals(0.0, Math.IEEEremainder(hours, 4.0), 1e-9, face + " zone " + hours);
            zones.add(Math.round(hours));
            assertEquals(Math.abs(DIAGONAL.timeZone(face) - DIAGONAL.timeZone(face.opposite())), 0.5, 1e-9, "opposite faces are 12 hours apart");
        }
        assertEquals(6, zones.size(), "six distinct time zones");
        assertEquals(0.0, DIAGONAL.timeZone(CubeFace.UP), 1e-12);
    }

    /** Polar axis: four equatorial faces 6 hours apart with the sun overhead at noon; the poles in endless twilight. */
    @Test
    void polarGivesFourEquatorialFacesAndTwoInTwilight() {
        for (CubeFace face : CubeFace.values()) {
            boolean pole = face == CubeFace.NORTH || face == CubeFace.SOUTH;
            for (int t = 0; t < 24000; t += 100) {
                double y = POLAR.direction(face, CubeSun.hourAngle(t))[1];
                if (pole) assertEquals(0.0, y, 1e-9);
            }
            if (!pole) {
                assertEquals(0.0, POLAR.latitude(face), 1e-12);
                assertEquals(0.0, Math.IEEEremainder(24.0 * POLAR.timeZone(face), 6.0), 1e-9);
            }
        }
    }

    /** At each face's noon its local clock reads 6000. */
    @Test
    void localClockReadsNoonAtLocalNoon() {
        for (CubeSun sun : new CubeSun[] {DIAGONAL, POLAR}) {
            for (CubeFace face : CubeFace.values()) {
                if (sun == POLAR && (face == CubeFace.NORTH || face == CubeFace.SOUTH)) continue;
                double best = -2;
                int noon = 0;
                for (int t = 100000; t < 124000; t++) {
                    double y = sun.direction(face, CubeSun.hourAngle(t))[1];
                    if (y > best) {
                        best = y;
                        noon = t;
                    }
                }
                assertEquals(6000.0, Math.floorMod(Math.round(sun.localClock(face, noon)), 24000L), 2.0, face + " clock at noon");
                // Vanilla's eased sun meets each zone at a different phase, so the rate varies, but it never runs
                // backwards and gains exactly a day per day.
                assertEquals(24000.0, sun.localClock(face, 224000) - sun.localClock(face, 200000), 1e-6, face + " clock per day");
                for (int t = 200000; t < 224000; t += 50) {
                    assertTrue(sun.localClock(face, t + 50) > sun.localClock(face, t), face + " clock runs forward");
                }
            }
        }
    }

    /** Vanilla consumers that read cos(2π·timeOfDay) get the sun's height; the morning half is mirrored. */
    @Test
    void equivalentTimeOfDayGivesTheSunHeight() {
        for (CubeFace face : CubeFace.values()) {
            for (int t = 0; t < 24000; t += 300) {
                LocalSky.Sample sample = LocalSky.sample(DIAGONAL, face, t);
                assertEquals(sample.sunY(), Math.cos(2 * Math.PI * sample.equivalentTimeOfDay()), 1e-9);
                boolean rising = DIAGONAL.rising(face, CubeSun.hourAngle(t));
                assertTrue(rising == sample.equivalentTimeOfDay() > 0.5 || Math.abs(sample.sunY()) > 0.999);
            }
        }
    }

    @Test
    void skyDarkenMatchesVanillaThresholds() {
        assertEquals(0, LocalSky.skyDarken(1.0, 0.0F, 0.0F));
        assertEquals(11, LocalSky.skyDarken(-1.0, 0.0F, 0.0F));
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT + 1e-6, 0.0F, 0.0F) < 4);
        assertTrue(LocalSky.skyDarken(LocalSky.DAY_SUN_HEIGHT - 1e-3, 0.0F, 0.0F) >= 4);
    }

    /** Sleeping lands in the face's next morning, within a day, in daylight with the night just over. */
    @Test
    void nextMorningIsTheFirstDaylight() {
        for (CubeSun sun : new CubeSun[] {DIAGONAL, POLAR}) {
            for (CubeFace face : CubeFace.values()) {
                for (long now = 0; now < 48000; now += 1777) {
                    long morning = sun.nextMorning(face, now);
                    assertTrue(morning > now && morning <= now + 24000);
                    double y = sun.direction(face, CubeSun.hourAngle(morning))[1];
                    boolean twilight = sun == POLAR && (face == CubeFace.NORTH || face == CubeFace.SOUTH);
                    if (!twilight) {
                        assertTrue(y > LocalSky.DAY_SUN_HEIGHT, face + " should wake in daylight");
                        assertTrue(sun.direction(face, CubeSun.hourAngle(morning - 10))[1] <= LocalSky.DAY_SUN_HEIGHT, face + " should wake at first light");
                    }
                }
            }
        }
    }
}
