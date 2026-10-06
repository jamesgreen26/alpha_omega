package g_mungus.alpha_omega.sky;

import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.command.OrbifoldTeleport;
import g_mungus.alpha_omega.orbifold.NearestImages;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link ProjectionInverse} and {@code /orbifold tp <lat> <lon>}: a tile position's latitude and longitude lead back
 * to it within a block, at every latitude, by the poles and by the cone points; and a latitude and longitude typed in
 * lands where the projection says it is.
 */
class ProjectionInverseTest {

    private static List<OrbifoldGeometry> sizes() {
        return List.of(new OrbifoldGeometry(OrbifoldSize.SMALL, 4), new OrbifoldGeometry(OrbifoldSize.NORMAL, 4));
    }

    /** How far the inverse lands from where it should, in blocks, over every image (a seam's two sides are one place). */
    private static double roundTrip(OrbifoldGeometry g, double x, double z) {
        HexOrbifoldProjection projection = HexOrbifoldProjection.of(g);
        Position p = projection.project(x, z);
        double[] found = OrbifoldTeleport.latLonPlace(g, Math.toDegrees(p.latitude()), 360.0 * p.longitude());
        assertTrue(g.isTile((int) Math.floor(found[0]), (int) Math.floor(found[1])), "lands in the tile: " + found[0] + ", " + found[1]);
        return NearestImages.distance(g, found[0], found[1], x, z);
    }

    @Test
    void roundTripsAcrossTheTile() {
        Random random = new Random(3);
        for (OrbifoldGeometry g : sizes()) {
            double worst = 0;
            for (int i = 0; i < 25; i++) {
                double x = g.minX + 1 + random.nextDouble() * (g.a - 2), z = g.northRow + 1 + random.nextDouble() * (g.b / 2.0 - 2);
                worst = Math.max(worst, roundTrip(g, x, z));
            }
            assertTrue(worst < 1.0, g.size + ": worst " + worst);
        }
    }

    /** Near the north pole (cone point N), the other three cone points, the south pole and spawn. */
    @Test
    void roundTripsAtSpecialPlaces() {
        for (OrbifoldGeometry g : sizes()) {
            List<double[]> places = new ArrayList<>();
            places.add(new double[] {g.spawnX + 0.5, g.spawnZ + 0.5});
            places.add(new double[] {3.5, g.northRow + 6.5});
            places.add(new double[] {-20.5, g.northRow + 40.5});
            places.add(new double[] {g.minX + 5.5, g.northRow + 5.5});
            places.add(new double[] {g.a / 4.0 + 3.5, g.southRow - 7.5});
            places.add(new double[] {-g.a / 4.0 - 9.5, g.southRow - 2.5});
            // The south pole: where the projection's latitude is lowest on a fine scan.
            places.add(southPole(g));
            for (double[] p : places) {
                double d = roundTrip(g, p[0], p[1]);
                assertTrue(d < 1.0, g.size + " at " + p[0] + ", " + p[1] + ": off by " + d);
            }
        }
    }

    /** Typed latitudes and longitudes, from pole to pole: the place found projects back to them within a block's worth of arc. */
    @Test
    void typedPlacesProjectBack() {
        for (OrbifoldGeometry g : sizes()) {
            HexOrbifoldProjection projection = HexOrbifoldProjection.of(g);
            double[][] targets = {{0, 0}, {45, 90}, {-45, -120}, {89.9, 10}, {-89.9, 170}, {-19.47, 60}, {63.1, 0}, {10, 179.9}, {-60, -179.9}};
            for (double[] t : targets) {
                double[] found = OrbifoldTeleport.latLonPlace(g, t[0], t[1]);
                Position p = projection.project(found[0], found[1]);
                double[] u = ProjectionInverse.unit(p.latitude(), p.longitude());
                double[] v = ProjectionInverse.unit(Math.toRadians(t[0]), t[1] / 360.0);
                double chord = Math.sqrt(Math.pow(u[0] - v[0], 2) + Math.pow(u[1] - v[1], 2) + Math.pow(u[2] - v[2], 2));
                // A block's worth of arc there (the sky speed), with a floor where the sky stalls at a cone point.
                double perBlock = Math.max(projection.skySpeed(found[0], found[1]), 1e-6);
                assertTrue(chord / perBlock < 1.5, g.size + " at " + t[0] + ", " + t[1] + ": " + chord / perBlock + " blocks of arc off");
            }
            // The north pole itself: next to cone point N.
            double[] pole = OrbifoldTeleport.latLonPlace(g, 90, 0);
            assertTrue(Math.hypot(pole[0], pole[1] - g.northRow) < 1.0, g.size + ": pole at " + pole[0] + ", " + pole[1]);
        }
    }

    private static double[] southPole(OrbifoldGeometry g) {
        HexOrbifoldProjection projection = HexOrbifoldProjection.of(g);
        double[] found = OrbifoldTeleport.latLonPlace(g, -90, 0);
        // Check it is the lowest latitude round it.
        double lat = projection.project(found[0], found[1]).latitude();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) assertTrue(projection.project(found[0] + 8 * dx, found[1] + 8 * dz).latitude() >= lat - 1e-12);
        }
        return found;
    }
}
