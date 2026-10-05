package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link HexOrbifoldProjection} against {@code orbifold-implementation.md} phase 2 "Tests" and the wrapping plan's §4–6. */
class HexOrbifoldProjectionTest {

    private static final OrbifoldGeometry DEFAULT = new OrbifoldGeometry(4, 4);
    private static final double TETRAHEDRAL_ANGLE = Math.acos(-1.0 / 3.0);

    private static List<OrbifoldGeometry> sizes() {
        return List.of(new OrbifoldGeometry(2, 4), DEFAULT, new OrbifoldGeometry(8, 4));
    }

    private static HexOrbifoldProjection projection(OrbifoldGeometry geometry) {
        return HexOrbifoldProjection.of(geometry);
    }

    /** A point on the planet as a unit vector (x toward 0° 0°, y toward 90° E, z the north pole). */
    private static double[] unit(Position position) {
        double lon = 2.0 * Math.PI * position.longitude();
        return new double[] {Math.cos(position.latitude()) * Math.cos(lon), Math.cos(position.latitude()) * Math.sin(lon), Math.sin(position.latitude())};
    }

    private static double angle(Position a, Position b) {
        double[] u = unit(a), v = unit(b);
        return Math.acos(Math.max(-1.0, Math.min(1.0, u[0] * v[0] + u[1] * v[1] + u[2] * v[2])));
    }

    /** Straight-line distance between two points on the unit sphere: exact for nearby points, unlike the angle. */
    private static double chord(Position a, Position b) {
        double[] u = unit(a), v = unit(b);
        return Math.sqrt(Math.pow(u[0] - v[0], 2) + Math.pow(u[1] - v[1], 2) + Math.pow(u[2] - v[2], 2));
    }

    /** {@code a − b} wrapped into [−π, π). */
    private static double angleDifference(double a, double b) {
        double d = a - b;
        return d - 2.0 * Math.PI * Math.floor(d / (2.0 * Math.PI) + 0.5);
    }

    /** Two projections of the same place: same point on the planet, heading turned by {@code turn} radians. */
    private static void assertSamePlace(Position expected, Position actual, double turn, String where) {
        assertTrue(chord(expected, actual) < 1e-9, where + ": " + expected + " vs " + actual);
        if (Math.abs(expected.latitude()) < Math.PI / 2 - 1e-6) {
            assertEquals(0.0, angleDifference(actual.heading(), expected.heading() + turn), 1e-7, where + ": heading");
        }
    }

    @Test
    void spawnIsTheOriginFacingNorth() {
        for (OrbifoldGeometry g : sizes()) {
            Position spawn = projection(g).project(g.spawnX, g.spawnZ);
            // Spawn is rounded to a whole block, a fraction of a block from the exact equator.
            assertEquals(0.0, Math.toDegrees(spawn.latitude()), 0.01, "k=" + g.sizeFactor);
            assertEquals(0.0, spawn.longitude(), 1e-12, "k=" + g.sizeFactor);
            assertEquals(0.0, angleDifference(spawn.heading(), 0.0), 1e-9, "k=" + g.sizeFactor);
        }
    }

    @Test
    void conePointsFormARegularTetrahedron() {
        for (OrbifoldGeometry g : sizes()) {
            List<Position> cones = new ArrayList<>();
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) cones.add(projection(g).project(cone.x(), cone.z()));
            assertEquals(Math.PI / 2, cones.get(0).latitude(), 1e-12, "N is the north pole");
            for (int i = 1; i < 4; i++) assertEquals(-19.4712, Math.toDegrees(cones.get(i).latitude()), 1e-4);
            for (int i = 0; i < 4; i++) {
                for (int j = i + 1; j < 4; j++) {
                    assertEquals(TETRAHEDRAL_ANGLE, angle(cones.get(i), cones.get(j)), 1e-9, "cone points " + i + " and " + j);
                }
            }
            // F at 180°, E at 60° E, W at 60° W (W §4).
            assertEquals(0.5, Math.abs(cones.get(1).longitude()), 1e-9);
            assertEquals(1.0 / 6.0, cones.get(2).longitude(), 1e-9);
            assertEquals(-1.0 / 6.0, cones.get(3).longitude(), 1e-9);
        }
    }

    /** Every element of {@code Γ} near the tile: the generators and their products of up to three. */
    private static List<Motion> elements(OrbifoldGeometry g) {
        List<Motion> elements = new ArrayList<>(g.generators());
        for (Motion first : g.generators()) {
            for (Motion second : g.generators()) {
                elements.add(first.then(second));
                for (Motion third : g.generators()) elements.add(first.then(second).then(third));
            }
        }
        return elements;
    }

    @Test
    void identificationsGiveTheSamePlace() {
        Random random = new Random(1);
        for (OrbifoldGeometry g : sizes()) {
            HexOrbifoldProjection projection = projection(g);
            List<Motion> elements = elements(g);
            for (int i = 0; i < 200; i++) {
                double x = g.minX + random.nextDouble() * g.a;
                double z = g.northRow + random.nextDouble() * g.b / 2.0;
                Position here = projection.project(x, z);
                for (Motion element : elements) {
                    Position there = projection.project(element.pointX(x), element.pointZ(z));
                    assertSamePlace(here, there, element.turned() ? Math.PI : 0.0, "k=" + g.sizeFactor + " (" + x + ", " + z + ") by " + element);
                }
                // W §4's list, written out: a lap, the diagonal lattice vectors, and the half turn about N.
                double[][] images = {{x + g.a, z}, {x - g.a, z}, {x + g.a / 2.0, z + g.b}, {x - g.a / 2.0, z - g.b},
                    {x - g.a / 2.0, z + g.b}, {x + g.a / 2.0, z - g.b}, {-x, 2.0 * g.northRow - z}};
                for (int j = 0; j < images.length; j++) {
                    assertSamePlace(here, projection.project(images[j][0], images[j][1]), j == images.length - 1 ? Math.PI : 0.0,
                        "k=" + g.sizeFactor + " identification " + j);
                }
            }
        }
    }

    @Test
    void bandPositionsHaveTheSkyOfTheirSource() {
        Random random = new Random(2);
        OrbifoldGeometry g = DEFAULT;
        HexOrbifoldProjection projection = projection(g);
        for (int i = 0; i < 2000; i++) {
            // Anywhere in the footprint outside the tile.
            double x = g.minX - g.reach + random.nextDouble() * (g.a + 2.0 * g.reach);
            double z = g.northRow - g.reach + random.nextDouble() * (g.b / 2.0 + 2.0 * g.reach);
            if (g.seamDepth(x, z) <= 0) continue;
            Motion frame = g.frame(x, z);
            double sx = frame.pointX(x), sz = frame.pointZ(z);
            assertTrue(g.seamDepth(sx, sz) <= 0, "source in the tile");
            assertSamePlace(projection.project(sx, sz), projection.project(x, z), frame.turned() ? Math.PI : 0.0, "band (" + x + ", " + z + ")");
        }
    }

    @Test
    void skySpeedVanishesAtTheConePoints() {
        for (OrbifoldGeometry g : sizes()) {
            HexOrbifoldProjection projection = projection(g);
            double mean = meanSkySpeed(g);
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
                assertEquals(0.0, projection.skySpeed(cone.x(), cone.z()), 1e-6 * mean, cone.name());
                // It grows linearly with distance: double the distance, double the speed.
                double near = projection.skySpeed(cone.x() + 10, cone.z() + 5);
                double far = projection.skySpeed(cone.x() + 20, cone.z() + 10);
                assertEquals(2.0, far / near, 0.01, cone.name());
            }
        }
    }

    /**
     * The wrapping plan's §4 "Sizes" table: mean sky speed 3.9°, 1.9° and 1.0° per 100 blocks, and 5.0°, 2.5° and
     * 1.25° at spawn. The means are given to two figures, so they are checked to that rounding, and the mean scales
     * exactly with the world; the spawn speeds are checked to 1%. §3's share of the world below half the mean speed,
     * 8.8%, is checked too.
     */
    @Test
    void skySpeedMatchesTheSizesTable() {
        double[] tableMean = {3.9, 1.9, 1.0};
        double[] tableSpawn = {5.0, 2.5, 1.25};
        List<OrbifoldGeometry> sizes = sizes();
        double reference = meanSkySpeed(DEFAULT) * DEFAULT.a;
        for (int i = 0; i < sizes.size(); i++) {
            OrbifoldGeometry g = sizes.get(i);
            double mean = meanSkySpeed(g);
            assertEquals(tableMean[i], Math.toDegrees(mean) * 100.0, 0.05, "k=" + g.sizeFactor + " mean");
            assertEquals(reference, mean * g.a, 1e-3 * reference, "k=" + g.sizeFactor + " mean scales with the world");
            double spawn = Math.toDegrees(projection(g).skySpeed(g.spawnX, g.spawnZ)) * 100.0;
            assertEquals(tableSpawn[i], spawn, 0.01 * tableSpawn[i], "k=" + g.sizeFactor + " at spawn");
        }
        assertEquals(0.088, shareBelowHalf(DEFAULT), 0.003);
    }

    private static final int GRID = 256;

    /** Mean sky speed over the tile, in radians per block, from a {@code 2·GRID × GRID} grid of cell centres. */
    private static double meanSkySpeed(OrbifoldGeometry g) {
        double sum = 0.0;
        for (double[] p : grid(g)) sum += projection(g).skySpeed(p[0], p[1]);
        return sum / (2 * GRID * GRID);
    }

    private static double shareBelowHalf(OrbifoldGeometry g) {
        double half = meanSkySpeed(g) / 2.0;
        int below = 0;
        for (double[] p : grid(g)) {
            if (projection(g).skySpeed(p[0], p[1]) < half) below++;
        }
        return below / (2.0 * GRID * GRID);
    }

    private static List<double[]> grid(OrbifoldGeometry g) {
        List<double[]> points = new ArrayList<>(2 * GRID * GRID);
        for (int i = 0; i < 2 * GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                points.add(new double[] {g.minX + (i + 0.5) * g.a / (2.0 * GRID), g.northRow + (j + 0.5) * g.b / (2.0 * GRID)});
            }
        }
        return points;
    }

    /** The wrapping plan's §5 landmarks at k = 4. */
    @Test
    void landmarksFromSpawn() {
        HexOrbifoldProjection projection = projection(DEFAULT);
        double x = DEFAULT.spawnX, z = DEFAULT.spawnZ;
        Position north = projection.project(x, z - 2600);
        assertEquals(63.1, Math.toDegrees(north.latitude()), 0.05);
        assertEquals(0.0, north.longitude(), 1e-12);
        Position pole = projection.project(x, z - 5204);
        assertEquals(90.0, Math.toDegrees(pole.latitude()), 1e-9);
        Position south = projection.project(x, z + 5000);
        assertEquals(-57.2, Math.toDegrees(south.latitude()), 0.05);
        assertEquals(0.5, Math.abs(south.longitude()), 1e-9);
        Position east = projection.project(x + 3840, z);
        assertEquals(-15.1, Math.toDegrees(east.latitude()), 0.05);
        assertEquals(67.6, 360.0 * east.longitude(), 0.05);
        assertEquals(57.7, Math.toDegrees(east.heading()), 0.05);
        // Through the pole a straight line comes back down the same meridian: N is a half turn, so 10,408 blocks north
        // is spawn again, turned round.
        Position back = projection.project(x, z - 10408);
        assertSamePlace(projection.project(x, z), back, Math.PI, "10,408 north");
    }

    /** The map keeps angles and handedness: walking east turns geographic bearings the same way as walking north. */
    @Test
    void theMapIsConformalAndOrientationPreserving() {
        Random random = new Random(3);
        HexOrbifoldProjection projection = projection(DEFAULT);
        for (int i = 0; i < 500; i++) {
            double x = DEFAULT.minX + random.nextDouble() * DEFAULT.a;
            double z = DEFAULT.northRow + random.nextDouble() * DEFAULT.b / 2.0;
            Position here = projection.project(x, z);
            if (Math.abs(here.latitude()) > 1.4 || projection.skySpeed(x, z) < 1e-5) continue;
            double step = 0.5;
            double[] east = bearing(projection.project(x - step, z), projection.project(x + step, z));
            double[] north = bearing(projection.project(x, z + step), projection.project(x, z - step));
            // Bearing of world +X is the heading plus 90°, of world −Z the heading; both steps the same length.
            assertEquals(0.0, angleDifference(north[0], here.heading()), 1e-3, "north bearing at " + x + ", " + z);
            assertEquals(0.0, angleDifference(east[0], here.heading() + Math.PI / 2), 1e-3, "east bearing at " + x + ", " + z);
            assertEquals(1.0, east[1] / north[1], 1e-3, "scale at " + x + ", " + z);
        }
    }

    /** Compass bearing and angular distance from one point on the planet to a nearby one. */
    private static double[] bearing(Position from, Position to) {
        double dLon = 2.0 * Math.PI * (to.longitude() - from.longitude());
        double y = Math.sin(dLon) * Math.cos(to.latitude());
        double x = Math.cos(from.latitude()) * Math.sin(to.latitude()) - Math.sin(from.latitude()) * Math.cos(to.latitude()) * Math.cos(dLon);
        return new double[] {Math.atan2(y, x), chord(from, to)};
    }
}
