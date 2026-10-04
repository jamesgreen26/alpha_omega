package g_mungus.alpha_omega.sky;

/**
 * Where a world position lies on the planet: the one place that maps the torus onto the sphere. The local sky, the
 * local clock and every gameplay check go through {@link #project}, so trying another projection means changing only
 * that method.
 *
 * <p>Positions come in as fractions of a lap, each in [0, 1). A projection must join up across the edges (0 and 1 are
 * the same place), and should return longitude 0, latitude 0 and heading 0 at (0, 0) so the world looks vanilla at
 * spawn. The sky is continuous wherever the projection is (a pole is one point, so longitude and heading may jump
 * there as long as the sky they describe does not).
 */
public final class PlanetProjection {

    private PlanetProjection() {
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

    /**
     * The current projection, for x and z as fractions of a lap in [0, 1). X is longitude, one turn per lap. Z is the
     * angle round a meridian: walking toward -Z goes over the north pole (a quarter lap), down the far side of the
     * planet (half a lap away, 12 hours off and facing south) and back up over the south pole, so walking either axis
     * turns the sky steadily one way.
     */
    public static Position project(double x, double z) {
        double longitude = wrapTurns(x);
        double meridian = 2.0 * Math.PI * wrapTurns(-z);
        double latitude = Math.atan2(Math.sin(meridian), Math.abs(Math.cos(meridian)));
        if (Math.cos(meridian) >= 0.0) return new Position(longitude, latitude, 0.0);
        return new Position(wrapTurns(longitude + 0.5), latitude, Math.PI);
    }

    /** Turns wrapped into [-0.5, 0.5). */
    public static double wrapTurns(double turns) {
        double shifted = turns + 0.5;
        return shifted - Math.floor(shifted) - 0.5;
    }
}
