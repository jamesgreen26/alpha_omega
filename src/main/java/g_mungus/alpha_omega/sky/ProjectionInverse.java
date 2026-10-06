package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;

/**
 * Where in the tile a point of the planet is: the projection run backwards, numerically. Pure.
 *
 * <p>A coarse grid over the tile finds the few samples nearest the target on the sphere (by chord length, which is
 * exact for nearby points), and a pattern search from each walks down to a sixty-fourth of a block. The projection is
 * conformal away from the cone points, so the distance has a single smooth minimum near each sample; at a cone point
 * it grows with the square of the distance, which the search handles the same way. Each place of the world is in the
 * tile once, so the answer is unique up to the tile's own edges (both sides of a seam are the same place).
 */
public final class ProjectionInverse {

    /** Grid samples across the tile's width; rows follow from its aspect. */
    private static final int COLUMNS = 160;
    /** How many of the best grid samples to refine: more than one, so a sample near a seam cannot hide the answer. */
    private static final int SEEDS = 6;
    private static final double FINEST_STEP = 1.0 / 64.0;

    private ProjectionInverse() {
    }

    /** A point of the tile and how far its projection is from the target, as a chord of the unit sphere. */
    public record Result(double x, double z, double chord) {
    }

    /** The tile point at a latitude (radians, north positive) and longitude (turns, east positive). */
    public static Result find(OrbifoldGeometry geometry, PlanetProjection.Projection projection, double latitude, double longitude) {
        double[] target = unit(latitude, longitude);
        double width = geometry.maxX - geometry.minX, height = geometry.southRow - geometry.northRow;
        int rows = Math.max(8, (int) Math.round(COLUMNS * height / width));
        double stepX = width / COLUMNS, stepZ = height / rows;
        double[] seedX = new double[SEEDS], seedZ = new double[SEEDS], seedError = new double[SEEDS];
        java.util.Arrays.fill(seedError, Double.MAX_VALUE);
        for (int i = 0; i < COLUMNS; i++) {
            for (int j = 0; j < rows; j++) {
                double x = geometry.minX + (i + 0.5) * stepX, z = geometry.northRow + (j + 0.5) * stepZ;
                double e = error(projection, x, z, target);
                // Keep the SEEDS best, replacing the worst kept.
                int worst = 0;
                for (int k = 1; k < SEEDS; k++) if (seedError[k] > seedError[worst]) worst = k;
                if (e < seedError[worst]) {
                    seedError[worst] = e;
                    seedX[worst] = x;
                    seedZ[worst] = z;
                }
            }
        }
        double bestX = 0, bestZ = 0, best = Double.MAX_VALUE;
        for (int k = 0; k < SEEDS; k++) {
            if (seedError[k] == Double.MAX_VALUE) continue;
            double[] refined = refine(projection, seedX[k], seedZ[k], Math.max(stepX, stepZ), target);
            if (refined[2] < best) {
                best = refined[2];
                bestX = refined[0];
                bestZ = refined[1];
            }
        }
        // The search may step a little past a seam: the same place, back in the tile.
        Motion g = geometry.frame(bestX, bestZ);
        return new Result(g.pointX(bestX), g.pointZ(bestZ), Math.sqrt(best));
    }

    /** Pattern search: try the eight neighbours a step away, move to the best if it is better, else halve the step. */
    private static double[] refine(PlanetProjection.Projection projection, double x, double z, double step, double[] target) {
        double e = error(projection, x, z, target);
        while (step > FINEST_STEP) {
            double bx = x, bz = z, be = e;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    double nx = x + dx * step, nz = z + dz * step;
                    double ne = error(projection, nx, nz, target);
                    if (ne < be) {
                        bx = nx;
                        bz = nz;
                        be = ne;
                    }
                }
            }
            if (be < e) {
                x = bx;
                z = bz;
                e = be;
            } else {
                step /= 2.0;
            }
        }
        return new double[] {x, z, e};
    }

    /** Squared chord between the projection of {@code (x, z)} and the target. */
    private static double error(PlanetProjection.Projection projection, double x, double z, double[] target) {
        Position p = projection.project(x, z);
        double[] u = unit(p.latitude(), p.longitude());
        double dx = u[0] - target[0], dy = u[1] - target[1], dz = u[2] - target[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** A point of the planet as a unit vector: x toward 0° 0°, y toward 90° E, z the north pole. */
    public static double[] unit(double latitude, double longitude) {
        double lon = 2.0 * Math.PI * longitude;
        double c = Math.cos(latitude);
        return new double[] {c * Math.cos(lon), c * Math.sin(lon), Math.sin(latitude)};
    }
}
