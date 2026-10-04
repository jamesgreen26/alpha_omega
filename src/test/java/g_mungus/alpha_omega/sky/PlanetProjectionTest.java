package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import org.junit.jupiter.api.Test;

class PlanetProjectionTest {

    private static final int W = 12288;
    private static final double EPS = 1e-9;

    @Test
    void spawnIsTheOriginAndUnwrappedWorldsStayThere() {
        assertEquals(Position.ORIGIN, project(0, 0, W));
        assertEquals(Position.ORIGIN, project(12345, -6789, 0));
    }

    @Test
    void walkingNorthGoesOverThePoleToTheFarSide() {
        assertEquals(Math.PI / 2, project(0, -W / 4.0, W).latitude(), EPS);
        assertEquals(-Math.PI / 2, project(0, W / 4.0, W).latitude(), EPS);
        assertEquals(Math.PI / 4, project(0, -W / 8.0, W).latitude(), EPS);
        Position beyond = project(0, -3.0 * W / 8.0, W);
        assertEquals(Math.PI / 4, beyond.latitude(), EPS);
        assertEquals(0.5, Math.abs(beyond.longitude()), EPS);
        assertEquals(Math.PI, beyond.heading(), EPS);
        assertEquals(0.0, project(0, -W / 8.0, W).heading(), EPS);
    }

    @Test
    void longitudeIsPeriodicAndCentred() {
        assertEquals(0.25, project(W / 4.0, 0, W).longitude(), EPS);
        assertEquals(-0.25, project(-W / 4.0, 0, W).longitude(), EPS);
        for (double x = -3.0 * W; x <= 3.0 * W; x += 101.7) {
            double lon = project(x, 0, W).longitude();
            assertTrue(lon >= -0.5 && lon < 0.5);
            assertEquals(lon, project(x + W, 0, W).longitude(), 1e-9);
        }
    }

    @Test
    void positionsArePeriodicAndMoveSmoothlyOnTheSphere() {
        double step = 0.25;
        for (double z = -2.0 * W; z <= 2.0 * W; z += 7.3) {
            for (double x = -W; x <= W; x += 1531.1) {
                double[] here = up(project(x, z, W));
                double[] lap = up(project(x + W, z - 3.0 * W, W));
                double[] north = up(project(x, z - step, W));
                assertTrue(distance(here, lap) < 1e-9);
                assertEquals(2.0 * Math.PI * step / W, distance(here, north), 1e-9);
            }
        }
    }

    @Test
    void eachPointOfThePlanetAppearsTwicePerLapFacingOppositeWays() {
        for (double z = -W / 2.0; z < W / 2.0; z += 111) {
            for (double x = -W / 2.0; x < W / 2.0; x += 333) {
                Position a = project(x, z, W);
                Position b = project(x + W / 2.0, -W / 2.0 - z, W);
                assertTrue(distance(up(a), up(b)) < 1e-9);
                assertEquals(-1.0, Math.cos(a.heading() - b.heading()), 1e-9);
            }
        }
    }

    /** The unit vector from the planet's centre through a position. */
    private static double[] up(Position position) {
        double lon = 2.0 * Math.PI * position.longitude();
        double lat = position.latitude();
        return new double[] {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    /** The projection at a world position, as LocalSky feeds it. */
    private static Position project(double x, double z, int period) {
        if (period <= 0) return Position.ORIGIN;
        return PlanetProjection.project(LocalSky.lapFraction(x, period), LocalSky.lapFraction(z, period));
    }
}
