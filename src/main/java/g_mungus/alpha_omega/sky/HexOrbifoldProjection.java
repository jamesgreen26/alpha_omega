package g_mungus.alpha_omega.sky;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.PlanetProjection.Position;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hexagonal p2 orbifold on the sphere ({@code alpha-omega-best-wrapping-plan.md} §5–6): the plane, scaled so the
 * lattice is exactly hexagonal, goes through the Weierstrass {@code ℘} function of the equianharmonic lattice and a
 * stereographic projection. The four cone points land on a regular tetrahedron, with {@code N} at the north pole and
 * spawn at 0° 0° facing north. The map is conformal everywhere except at the cone points.
 *
 * <p>Every image of a position under the world's group is the same place: a lattice translation leaves everything
 * unchanged, and a half turn (a fold) turns the heading by π. So positions in the band, or a lap away, need no
 * canonicalising first.
 *
 * <p>Inputs:
 * <pre>
 *   s = (√3/2)·a/b                          makes the lattice exactly hexagonal
 *   ζ = ((x − X₀) − i·s·(z − Z₀)) / a       real east, imaginary north; (X₀, Z₀) is N
 *   ζ lives on the lattice Z + τZ, τ = e^{iπ/3}
 * </pre>
 * Outputs, with {@code W = −℘(ζ)/K} and {@code K = √2·℘(1/2)}:
 * <pre>
 *   latitude  = 2·atan|W| − π/2
 *   longitude = −arg W                       (east positive; here in turns)
 *   heading   = arg ℘(ζ) − arg ℘′(ζ) − π/2    (compass bearing of world −Z)
 *   sky speed = 2|℘′(ζ)| / (K·(1 + |W|²)) / a   radians per block
 * </pre>
 * At {@code N} itself latitude is 90° and longitude and heading are 0 (undefined); at the other three cone points the
 * heading is undefined and the sky speed is 0.
 */
public final class HexOrbifoldProjection implements PlanetProjection.Projection {

    private static final double TR = 0.5;
    private static final double TI = Math.sqrt(3.0) / 2.0;
    private static final double PI = Math.PI;
    /** Terms of the series each side of 0: enough for double precision once {@code ζ} is reduced. */
    private static final int TERMS = 6;
    /** {@code Σ_{n≠0} csc²(πnτ)}, which makes {@code ℘} vanish to second order at 0 (real part; the imaginary part cancels). */
    private static final double[] LATTICE_SUM = latticeSum();
    /** {@code e₁ = ℘(1/2)} ≈ 5.898344. */
    private static final double E1 = weierstrass(0.5, 0.0)[0];
    /** {@code K = √2·e₁} ≈ 8.341518: puts the finite branch values at the tetrahedral latitude. */
    private static final double K = Math.sqrt(2.0) * E1;

    private record Key(int a, int b, int northRow) {
    }

    private static final Map<Key, HexOrbifoldProjection> CACHE = new ConcurrentHashMap<>();

    /** Lattice width {@code a}, in blocks. */
    private final double a;
    /** The {@code z} scale that makes the lattice exactly hexagonal. */
    private final double s;
    /** Cone point {@code N}: the north pole. */
    private final double x0;
    private final double z0;

    public HexOrbifoldProjection(int a, int b, int northRow) {
        this.a = a;
        this.s = Math.sqrt(3.0) / 2.0 * a / b;
        this.x0 = 0.0;
        this.z0 = northRow;
    }

    /** The projection for a world's geometry: its lattice, with {@code N (0, zN)} at the north pole. */
    public static HexOrbifoldProjection of(OrbifoldGeometry geometry) {
        return CACHE.computeIfAbsent(new Key(geometry.a, geometry.b, geometry.northRow), key -> new HexOrbifoldProjection(key.a, key.b, key.northRow));
    }

    @Override
    public Position project(double x, double z) {
        double[] values = this.values(x, z);
        if (values == null) return new Position(0.0, PI / 2.0, 0.0);
        double wr = -values[0] / K;
        double wi = -values[1] / K;
        double latitude = 2.0 * Math.atan(Math.hypot(wr, wi)) - PI / 2.0;
        double longitude = PlanetProjection.wrapTurns(-Math.atan2(wi, wr) / (2.0 * PI));
        double heading = Math.atan2(values[1], values[0]) - Math.atan2(values[3], values[2]) - PI / 2.0;
        heading -= 2.0 * PI * Math.floor(heading / (2.0 * PI));
        return new Position(longitude, latitude, heading);
    }

    /** How fast the sky turns per block walked at a position, in radians per block: the same in every direction. */
    public double skySpeed(double x, double z) {
        double[] values = this.values(x, z);
        if (values == null) return 0.0;
        double w2 = (values[0] * values[0] + values[1] * values[1]) / (K * K);
        return 2.0 * Math.hypot(values[2], values[3]) / K / (1.0 + w2) / this.a;
    }

    /** {@code {Re ℘, Im ℘, Re ℘′, Im ℘′}} at a world position, or null at an image of {@code N} (a pole of {@code ℘}). */
    private double[] values(double x, double z) {
        double re = (x - this.x0) / this.a;
        double im = -this.s * (z - this.z0) / this.a;
        return weierstrassBoth(re, im);
    }

    // ---- Weierstrass ℘ of the lattice Z + τZ ----

    /** {@code ζ} moved by the lattice to near the origin: a whole number of {@code τ}, then of 1. */
    private static double[] reduce(double re, double im) {
        long n = Math.round(im / TI);
        re -= n * TR;
        im -= n * TI;
        re -= Math.round(re);
        return new double[] {re, im};
    }

    /** {@code ℘(ζ)} alone, for the constants. */
    private static double[] weierstrass(double re, double im) {
        double[] both = weierstrassBoth(re, im);
        return new double[] {both[0], both[1]};
    }

    /**
     * {@code ℘} and {@code ℘′} together, as {@code {Re ℘, Im ℘, Re ℘′, Im ℘′}}, or null at a lattice point:
     * <pre>
     *   ℘(ζ)  = π² [ Σ csc²(π(ζ + nτ)) − Σ_{n≠0} csc²(πnτ) − 1/3 ]
     *   ℘′(ζ) = −2π³ Σ csc²(π(ζ + nτ))·cot(π(ζ + nτ))
     * </pre>
     */
    private static double[] weierstrassBoth(double re, double im) {
        double[] reduced = reduce(re, im);
        re = reduced[0];
        im = reduced[1];
        if (re * re + im * im < 1e-24) return null;
        double pr = 0.0, pim = 0.0, dr = 0.0, di = 0.0;
        for (int n = -TERMS; n <= TERMS; n++) {
            double u = PI * (re + n * TR);
            double v = PI * (im + n * TI);
            // sin and cos of u + iv, from one exp for cosh and sinh.
            double sinU = Math.sin(u), cosU = Math.cos(u), ev = Math.exp(v);
            double cosh = 0.5 * (ev + 1.0 / ev), sinh = 0.5 * (ev - 1.0 / ev);
            double sr = sinU * cosh, si = cosU * sinh;
            double cr = cosU * cosh, ci = -sinU * sinh;
            // csc² = 1 / sin².
            double s2r = sr * sr - si * si, s2i = 2.0 * sr * si, s2 = s2r * s2r + s2i * s2i;
            double qr = s2r / s2, qi = -s2i / s2;
            // cot = cos / sin.
            double sn = sr * sr + si * si;
            double tr = (cr * sr + ci * si) / sn, ti = (ci * sr - cr * si) / sn;
            pr += qr;
            pim += qi;
            dr += qr * tr - qi * ti;
            di += qr * ti + qi * tr;
        }
        double p2 = PI * PI, p3 = -2.0 * PI * PI * PI;
        return new double[] {p2 * (pr - LATTICE_SUM[0] - 1.0 / 3.0), p2 * (pim - LATTICE_SUM[1]), p3 * dr, p3 * di};
    }

    private static double[] latticeSum() {
        double sr = 0.0, si = 0.0;
        for (int n = -TERMS; n <= TERMS; n++) {
            if (n == 0) continue;
            double u = PI * n * TR, v = PI * n * TI;
            double s1 = Math.sin(u) * Math.cosh(v), s2 = Math.cos(u) * Math.sinh(v);
            double r = s1 * s1 - s2 * s2, i = 2.0 * s1 * s2, d = r * r + i * i;
            sr += r / d;
            si -= i / d;
        }
        return new double[] {sr, si};
    }
}
