package g_mungus.alpha_omega.transfer;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where a thing can be in storage, and which frame a group shares (RS §4). Pure: points are {@code (x, z)} pairs and
 * elements are {@link Motion}s, so the rules are unit-tested without a level.
 *
 * <p>A point of the world has one place in the tile and up to a few more in the band: its <b>expressions</b>. Each is
 * reached from where a thing is stored by an element of {@code Γ}; the identity reaches where it is. A thing may be
 * stored at an expression that is <b>valid</b> for it: within a depth limit past the seams ({@code H} for most
 * things, {@code C} for players kept in a group).
 */
public final class Frames {

    private Frames() {
    }

    /** One place a stored point can be expressed: the element taking it there, and the place. */
    public record Expression(Motion motion, double x, double z) {

        public double distanceSqr(double px, double pz) {
            double dx = this.x - px, dz = this.z - pz;
            return dx * dx + dz * dz;
        }
    }

    /** The other elements that can take a point of the tile into the footprint: the frames' inverses, as {@code copies}. */
    private static List<Motion> elements(OrbifoldGeometry geometry) {
        Elements last = lastElements;
        if (last != null && last.geometry == geometry) return last.elements;
        List<Motion> elements = new ArrayList<>(8);
        for (Motion g : List.of(geometry.east, geometry.west, geometry.northFold, geometry.southFold,
            geometry.northFold.then(geometry.east), geometry.northFold.then(geometry.west),
            geometry.southFold.then(geometry.east), geometry.southFold.then(geometry.west))) {
            elements.add(g.inverse());
        }
        lastElements = new Elements(geometry, List.copyOf(elements));
        return lastElements.elements;
    }

    private record Elements(OrbifoldGeometry geometry, List<Motion> elements) {
    }

    @Nullable
    private static volatile Elements lastElements;

    /** Whether a point is valid storage within {@code limit} blocks past the seams (the tile is always valid). */
    public static boolean valid(OrbifoldGeometry geometry, double x, double z, double limit) {
        return geometry.seamDepth(x, z) <= limit;
    }

    /**
     * Every expression of the stored point {@code (x, z)} within {@code limit} past the seams: the identity first
     * (always, wherever the point is), then its source in the tile, then its other copies in the band. Each element
     * and each place appears once.
     */
    public static List<Expression> expressions(OrbifoldGeometry geometry, double x, double z, double limit) {
        List<Expression> expressions = new ArrayList<>(4);
        expressions.add(new Expression(Motion.IDENTITY, x, z));
        if (!geometry.inFootprint((int) Math.floor(x), (int) Math.floor(z))) return expressions;
        Motion f = geometry.frame(x, z);
        double sx = f.pointX(x), sz = f.pointZ(z);
        if (!f.isIdentity()) expressions.add(new Expression(f, sx, sz));
        for (Motion back : elements(geometry)) {
            double qx = back.pointX(sx), qz = back.pointZ(sz);
            if (!valid(geometry, qx, qz, limit)) continue;
            Motion k = f.then(back);
            if (k.isIdentity()) continue;
            boolean seen = false;
            for (Expression e : expressions) {
                if (e.motion.equals(k)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) expressions.add(new Expression(k, qx, qz));
        }
        return expressions;
    }

    /** The expression of {@code (x, z)} within {@code limit} nearest to {@code (px, pz)}; the identity on a tie. */
    public static Expression nearest(OrbifoldGeometry geometry, double x, double z, double limit, double px, double pz) {
        Expression best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Expression e : expressions(geometry, x, z, limit)) {
            double d = e.distanceSqr(px, pz);
            if (d < bestDistance) {
                best = e;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * The horizontal distance between two stored points in the world itself: from {@code (px, pz)} to the nearest
     * expression of {@code (x, z)} anywhere in the footprint. Exact for distances well under the tile's size.
     */
    public static double distance(OrbifoldGeometry geometry, double x, double z, double px, double pz) {
        return Math.sqrt(nearest(geometry, x, z, geometry.reach, px, pz).distanceSqr(px, pz));
    }

    /**
     * The element a thing moved by between two stored positions, if it jumped between expressions of nearly one place:
     * the two are further apart than {@code slack} but some expression of {@code from} is within {@code slack} of
     * {@code to}. Null if it did not jump.
     */
    @Nullable
    public static Motion crossing(OrbifoldGeometry geometry, double fromX, double fromZ, double toX, double toZ, double slack) {
        double dx = toX - fromX, dz = toZ - fromZ;
        if (dx * dx + dz * dz <= slack * slack) return null;
        Expression best = nearest(geometry, fromX, fromZ, geometry.reach, toX, toZ);
        return best.motion.isIdentity() || best.distanceSqr(toX, toZ) > slack * slack ? null : best.motion;
    }

    /**
     * One frame for a group of things that are near each other in the world: for each member (by stored position), the
     * element to move it by so that every member is stored near the others. Each member moves to one of its
     * expressions within {@code limit}; neighbours (in the order found from the first member) must end within
     * {@code link} of each other. Of the ways to do that, the one that moves the fewest members wins, then the one
     * whose members are least deep past the seams in total, then the first found. Null if there is none: then nobody
     * should move.
     */
    @Nullable
    public static Motion[] groupFrame(OrbifoldGeometry geometry, double[][] positions, double limit, double link) {
        int n = positions.length;
        if (n == 0) return new Motion[0];
        List<List<Expression>> options = new ArrayList<>(n);
        for (double[] p : positions) options.add(expressions(geometry, p[0], p[1], limit));
        Motion[] best = null;
        int bestMoved = Integer.MAX_VALUE;
        double bestDepth = Double.MAX_VALUE;
        for (Expression anchor : options.get(0)) {
            Expression[] chosen = new Expression[n];
            chosen[0] = anchor;
            boolean whole = true;
            // Grow the group nearest first, each member at its expression nearest one already placed.
            for (int placed = 1; placed < n && whole; placed++) {
                int pick = -1;
                Expression pickAt = null;
                double pickDistance = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    if (chosen[j] != null) continue;
                    for (Expression e : options.get(j)) {
                        for (Expression q : chosen) {
                            if (q == null) continue;
                            double d = e.distanceSqr(q.x, q.z);
                            if (d < pickDistance) {
                                pick = j;
                                pickAt = e;
                                pickDistance = d;
                            }
                        }
                    }
                }
                if (pick < 0 || pickDistance > link * link) whole = false;
                else chosen[pick] = pickAt;
            }
            if (!whole) continue;
            int moved = 0;
            double depth = 0.0;
            for (Expression e : chosen) {
                if (!e.motion.isIdentity()) moved++;
                depth += geometry.seamDepth(e.x, e.z);
            }
            if (moved < bestMoved || moved == bestMoved && depth < bestDepth - 1e-9) {
                bestMoved = moved;
                bestDepth = depth;
                best = new Motion[n];
                for (int i = 0; i < n; i++) best[i] = chosen[i].motion;
            }
        }
        return best;
    }
}
