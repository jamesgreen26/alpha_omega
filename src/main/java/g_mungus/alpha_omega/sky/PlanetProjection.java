package g_mungus.alpha_omega.sky;

/**
 * Where a world position lies on the planet: the one place that maps the torus onto the sphere. The local sky, the
 * local clock and every gameplay check go through {@link #project}, which delegates to {@link #CURRENT}; to try
 * another projection, implement {@link Projection} and point {@code CURRENT} at it.
 *
 * <p>Positions come in as fractions of a lap, each in [0, 1). A projection must join up across the edges (0 and 1 are
 * the same place), and should return longitude 0, latitude 0 and heading 0 at (0, 0) so the world looks vanilla at
 * spawn. The sky is continuous wherever the projection is (a pole is one point, so longitude and heading may jump
 * there as long as the sky they describe does not).
 */
public final class PlanetProjection {

    /** The projection in use. */
    public static final Projection CURRENT = new MeridianLoopProjection();

    private PlanetProjection() {
    }

    /** A way of placing the world on the planet. */
    @FunctionalInterface
    public interface Projection {

        /** The point on the planet at x and z, each a fraction of a lap in [0, 1). */
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

    /** Where x and z, each a fraction of a lap in [0, 1), lie on the planet under the current projection. */
    public static Position project(double x, double z) {
        return CURRENT.project(x, z);
    }

    /** Turns wrapped into [-0.5, 0.5). */
    public static double wrapTurns(double turns) {
        double shifted = turns + 0.5;
        return shifted - Math.floor(shifted) - 0.5;
    }
}
