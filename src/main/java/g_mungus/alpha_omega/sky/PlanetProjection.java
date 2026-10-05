package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;

/**
 * Where a world position lies on the planet: the one place that maps the orbifold onto the sphere. The local sky, the
 * local clock and every gameplay check go through {@link #of}, which gives the projection for a world's geometry
 * ({@link HexOrbifoldProjection}); to try another projection, implement {@link Projection} and return it there.
 *
 * <p>Positions come in as world block coordinates {@code x} (east) and {@code z} (south), anywhere on the plane. A
 * projection must be invariant under the world's group: every image of a position (in the band, or a lap away) is the
 * same place, with the heading turned by the element's turn. It should return longitude 0, latitude 0 and heading 0 at
 * spawn so the world looks vanilla there. The sky is continuous wherever the projection is (a pole is one point, so
 * longitude and heading may jump there as long as the sky they describe does not).
 */
public final class PlanetProjection {

    private PlanetProjection() {
    }

    /** A way of placing the world on the planet. */
    @FunctionalInterface
    public interface Projection {

        /** The point on the planet at world block coordinates x and z. */
        Position project(double x, double z);
    }

    /**
     * A point on the planet and how the world's axes lie there.
     *
     * @param longitude in turns, in [-0.5, 0.5); east positive, so the local clock runs ahead
     * @param latitude  in radians, in [-π/2, π/2]; north positive
     * @param heading   the compass bearing of world -Z, in radians (0 when -Z is geographic north, π when it is south)
     */
    public record Position(double longitude, double latitude, double heading) {

        public static final Position ORIGIN = new Position(0.0, 0.0, 0.0);
    }

    /** The projection of a world with this geometry. */
    public static Projection of(OrbifoldGeometry geometry) {
        return HexOrbifoldProjection.of(geometry);
    }

    /** Turns wrapped into [-0.5, 0.5). */
    public static double wrapTurns(double turns) {
        double shifted = turns + 0.5;
        return shifted - Math.floor(shifted) - 0.5;
    }
}
