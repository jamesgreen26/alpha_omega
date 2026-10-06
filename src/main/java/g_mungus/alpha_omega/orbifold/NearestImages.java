package g_mungus.alpha_omega.orbifold;

/**
 * The image of a place nearest to another, over the whole group {@code Γ} rather than the few elements near the tile:
 * what a compass, a map or a distance needs when the two can be anywhere in storage, a lap or a seam apart. Pure.
 *
 * <p>{@code Γ} is the lattice {@code L = m·(a, 0) + n·(a/2, b)} of translations, and the half turns
 * {@code p ↦ (0, 2zN) + l − p} for {@code l ∈ L} (the north fold {@code R_N} is {@code l = 0}, the south fold
 * {@code R_S} is {@code l = (a/2, b)}). For each kind, the nearest image lies within one lattice step of the rounded
 * solution, so a 3 × 5 search of each is exact.
 */
public final class NearestImages {

    private NearestImages() {
    }

    /**
     * The element {@code h} of {@code Γ} for which {@code h(t)} is the image of the point {@code t} nearest to the
     * point {@code p}. The identity on a tie with it, and translations before half turns.
     */
    public static Motion toward(OrbifoldGeometry geometry, double tx, double tz, double px, double pz) {
        double a = geometry.a, b = geometry.b;
        int halfA = geometry.a / 2;
        Motion best = Motion.IDENTITY;
        double bestDistance = distanceSqr(tx - px, tz - pz);
        // Translations: t + m·(a, 0) + n·(a/2, b).
        long n0 = Math.round((pz - tz) / b);
        for (long n = n0 - 1; n <= n0 + 1; n++) {
            long m0 = Math.round((px - tx - n * a / 2.0) / a);
            for (long m = m0 - 2; m <= m0 + 2; m++) {
                int lx = (int) (m * geometry.a + n * halfA), lz = (int) (n * geometry.b);
                double d = distanceSqr(tx + lx - px, tz + lz - pz);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = Motion.translation(lx, lz);
                }
            }
        }
        // Half turns: (0, 2zN) + l − t.
        double cz = 2.0 * geometry.northRow;
        n0 = Math.round((pz - (cz - tz)) / b);
        for (long n = n0 - 1; n <= n0 + 1; n++) {
            long m0 = Math.round((px + tx - n * a / 2.0) / a);
            for (long m = m0 - 2; m <= m0 + 2; m++) {
                int lx = (int) (m * geometry.a + n * halfA), lz = (int) (n * geometry.b);
                double d = distanceSqr(lx - tx - px, cz + lz - tz - pz);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = Motion.halfTurn(lx, 2 * geometry.northRow + lz);
                }
            }
        }
        return best;
    }

    /** {@link #toward} for a block cell, measured from its centre; the result maps the cell with {@link Motion#cellX}. */
    public static Motion towardCell(OrbifoldGeometry geometry, int x, int z, double px, double pz) {
        return toward(geometry, x + 0.5, z + 0.5, px, pz);
    }

    /** The image of the point {@code t} nearest to {@code p}, as {@code {x, z}}. */
    public static double[] nearest(OrbifoldGeometry geometry, double tx, double tz, double px, double pz) {
        Motion h = toward(geometry, tx, tz, px, pz);
        return new double[] {h.pointX(tx), h.pointZ(tz)};
    }

    /** The image of the cell {@code (x, z)} nearest to {@code p}, as {@code {x, z}}. */
    public static int[] nearestCell(OrbifoldGeometry geometry, int x, int z, double px, double pz) {
        Motion h = towardCell(geometry, x, z, px, pz);
        return new int[] {h.cellX(x), h.cellZ(z)};
    }

    /** The distance between two points in the world itself: from {@code p} to the nearest image of {@code t}. */
    public static double distance(OrbifoldGeometry geometry, double tx, double tz, double px, double pz) {
        double[] q = nearest(geometry, tx, tz, px, pz);
        return Math.sqrt(distanceSqr(q[0] - px, q[1] - pz));
    }

    private static double distanceSqr(double dx, double dz) {
        return dx * dx + dz * dz;
    }
}
