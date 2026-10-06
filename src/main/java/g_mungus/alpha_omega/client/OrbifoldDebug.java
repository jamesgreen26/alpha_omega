package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection;
import java.util.List;
import java.util.Locale;

/**
 * The orbifold lines of F3, after vanilla's coordinates and the {@code Sun:} line:
 * <pre>
 * Orbifold: band, frame turn(0, -10496), 3.2 past the seam; canon 12.5 70.0 -5240.8
 * Planet: 63.1°N 0.0°E, -Z at 0.0°, facing 182.3°
 * </pre>
 * The region (tile, band, skirt), the camera's frame, its depth past the nearest seam (or how far short of it), and its
 * source position; then where it is on the planet, the compass bearing of world −Z there (the projection's heading)
 * and the bearing the camera faces. Pure.
 */
public final class OrbifoldDebug {

    private OrbifoldDebug() {
    }

    public static List<String> lines(OrbifoldGeometry geometry, double x, double y, double z, float yaw) {
        return lines(geometry, x, y, z, yaw, true);
    }

    /**
     * The {@code Orbifold:} line, and the {@code Planet:} line where {@code withPlanet} (the level has a local sky: the
     * overworld; the Nether's geometry has no projection).
     */
    public static List<String> lines(OrbifoldGeometry geometry, double x, double y, double z, float yaw, boolean withPlanet) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        String region = geometry.isTile(bx, bz) ? "tile" : geometry.isBand(bx, bz) ? "band" : geometry.isSkirt(bx, bz) ? "skirt" : "outside";
        double depth = geometry.seamDepth(x, z);
        String seam = depth > 0 ? String.format(Locale.ROOT, "%.1f past the seam", depth) : String.format(Locale.ROOT, "%.1f short of the seam", -depth);
        Motion frame = geometry.frame(x, z);
        String orbifold = String.format(Locale.ROOT, "Orbifold: %s, frame %s, %s; canon %.1f %.1f %.1f", region, frame, seam,
            frame.pointX(x), y, frame.pointZ(z));
        if (!withPlanet) return List.of(orbifold);
        PlanetProjection.Position position = PlanetProjection.of(geometry).project(x, z);
        double latitude = Math.toDegrees(position.latitude());
        double longitude = 360.0 * position.longitude();
        double heading = Math.toDegrees(position.heading());
        String planet = String.format(Locale.ROOT, "Planet: %.1f°%s %.1f°%s, -Z at %.1f°, facing %.1f°", Math.abs(latitude), latitude >= 0 ? "N" : "S",
            Math.abs(longitude), longitude >= 0 ? "E" : "W", heading, facing(position.heading(), yaw));
        return List.of(orbifold, planet);
    }

    /**
     * The compass bearing (degrees, clockwise from geographic north) of a look with this yaw, where world −Z has the
     * bearing {@code heading} (radians). Yaw 180 faces world −Z, and yaw grows clockwise seen from above.
     */
    public static double facing(double heading, float yaw) {
        double bearing = Math.toDegrees(heading) + yaw - 180.0;
        return bearing - 360.0 * Math.floor(bearing / 360.0);
    }
}
