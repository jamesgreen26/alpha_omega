package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * A Perlin octave invariant under {@code Γ} ({@code alpha-omega-best-wrapping-plan.md} §8): vanilla's improved noise
 * with its gradients hashed by lattice corner reduced modulo {@code L1 = (a, 0)} and {@code L2 = (a/2, b)}, and the
 * corner at {@code 2N − q} given the half-turned gradient of {@code q}. The noise is then periodic on the lattice and
 * even (or, for {@link NoiseSymmetry.Parity#ODD}, odd) about the cone point {@code N}, so about every cone point.
 *
 * <p><b>Cells.</b> The lattice needs a whole number of cells along {@code a/2} and along {@code b}. Each horizontal axis
 * gets its own cell size, {@code (a/2)/mx} and {@code b/mz} blocks, the nearest to the octave's own; the input is
 * stretched by the ratio, at most {@link #MAX_STRETCH} either way, or the octave is left to {@link SpectralNoise}. The
 * lattice is placed relative to {@code N} rather than the world origin, so its cells need not divide {@code 2N}.
 *
 * <p><b>Placement.</b> Vanilla's random offsets of the two horizontal axes are replaced by {@code N} sitting at
 * {@code (ox, oz)} in lattice units, each 0 or ½ (the half turn must map corners to corners), chosen so no corner is
 * the centre of a half turn in {@code Γ}: such a corner would need a gradient its own half turn, which the gradient set
 * lacks. The free axis (y, or z for {@code shift_b}) keeps vanilla's offset and its smear.
 *
 * <p>The gradient set ({@code SimplexNoise.GRADIENT}) is closed under the half turn {@code (gx, gy, gz) ↦ (−gx, gy, −gz)}
 * and under its negation {@code (gx, −gy, gz)}, so the turned gradients are vanilla gradients.
 */
public final class OrbifoldLattice implements InvariantOctave {

    /** The most an octave's input is stretched (or squeezed) along either axis to fit the lattice: 5%. */
    public static final double MAX_STRETCH = 0.05;

    /** Vanilla's {@code SimplexNoise.GRADIENT}. */
    static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}};

    private static final long LOW = (1L << 31) - 1;
    private static final long FLIP = 1L << 62;

    private final byte[] p;
    private final NoiseSymmetry symmetry;
    private final boolean swapped;
    private final boolean odd;
    private final double freeOffset;
    /** Cells along {@code a/2} and along {@code b}. The x period is {@code 2·mx} cells. */
    private final int mx;
    private final int mz;
    /** Lattice units per input unit along world x and z: the stretch. */
    private final double stretchX;
    private final double stretchZ;
    /** Lattice coordinate = input · stretch − origin; {@code N} is at {@code (twoCx/2, twoCz/2)}. */
    private final double originX;
    private final double originZ;
    private final int twoCx;
    private final int twoCz;

    private OrbifoldLattice(byte[] p, NoiseSymmetry symmetry, double freeOffset, OrbifoldGeometry geometry, int mx, int mz, int twoCx, int twoCz) {
        this.p = p;
        this.symmetry = symmetry;
        this.swapped = symmetry.axes() == NoiseSymmetry.Axes.SHIFT_B;
        this.odd = symmetry.parity() == NoiseSymmetry.Parity.ODD;
        this.freeOffset = freeOffset;
        this.mx = mx;
        this.mz = mz;
        double cellX = geometry.a / 2.0 / mx, cellZ = (double) geometry.b / mz;
        this.stretchX = 1.0 / (symmetry.scale() * cellX);
        this.stretchZ = 1.0 / (symmetry.scale() * cellZ);
        this.twoCx = twoCx;
        this.twoCz = twoCz;
        OrbifoldGeometry.ConePoint n = geometry.conePoints().get(0);
        this.originX = n.x() / cellX - twoCx / 2.0;
        this.originZ = n.z() / cellZ - twoCz / 2.0;
    }

    /**
     * The lattice octave for a symmetry, or null if its cells would need stretching by more than {@link #MAX_STRETCH}
     * (or are coarser than {@code b}).
     *
     * @param p          the octave's permutation (shared, not copied)
     * @param freeOffset vanilla's offset along the free axis
     * @param salt       picks among the valid placements of {@code N}
     */
    @Nullable
    public static OrbifoldLattice create(OrbifoldGeometry geometry, NoiseSymmetry symmetry, byte[] p, double freeOffset, long salt) {
        double cellsX = geometry.a / 2.0 * symmetry.scale(), cellsZ = geometry.b * symmetry.scale();
        if (!(cellsX >= 0.5) || !(cellsZ >= 0.5) || cellsX > Integer.MAX_VALUE / 4.0 || cellsZ > Integer.MAX_VALUE / 4.0) return null;
        int mx = (int) Math.round(cellsX), mz = (int) Math.round(cellsZ);
        if (Math.abs(mx / cellsX - 1.0) > MAX_STRETCH || Math.abs(mz / cellsZ - 1.0) > MAX_STRETCH) return null;
        // Placements of N, (2ox, 2oz), in an order picked by the salt; the first with no fixed corner.
        int[][] placements = {{1, 1}, {0, 1}, {1, 0}};
        int start = (int) Math.floorMod(salt, 3L);
        for (int n = 0; n < 3; n++) {
            int[] c = placements[(start + n) % 3];
            if (!hasFixedCorner(mx, mz, c[0], c[1])) return new OrbifoldLattice(p, symmetry, freeOffset, geometry, mx, mz, c[0], c[1]);
        }
        throw new AssertionError("no placement without a fixed corner for " + mx + " x " + mz + " cells");
    }

    /**
     * Whether some corner is a centre of a half turn: {@code 2q = 2c + λ} for a corner {@code q} and a lattice vector
     * {@code λ = n·(2mx, 0) + m·(mx, mz)}. Only parities matter.
     */
    static boolean hasFixedCorner(int mx, int mz, int twoCx, int twoCz) {
        for (int m = 0; m <= 1; m++) {
            if ((m * mz + twoCz) % 2 == 0 && (m * mx + twoCx) % 2 == 0) return true;
        }
        return false;
    }

    @Override
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double inU = this.swapped ? y : x, inW = this.swapped ? x : z, v = (this.swapped ? z : y) + this.freeOffset;
        double u = inU * this.stretchX - this.originX, w = inW * this.stretchZ - this.originZ;
        int i = floor(u), j = floor(v), k = floor(w);
        double du = u - i, dv = v - j, dw = w - k;
        double smear = 0.0;
        if (yScale != 0.0 && !this.swapped) {
            double limit = yMax >= 0.0 && yMax < dv ? yMax : dv;
            smear = floor(limit / yScale + 1.0E-7F) * yScale;
        }
        double dvw = dv - smear;
        long c00 = this.canon(i, k), c10 = this.canon(i + 1, k), c01 = this.canon(i, k + 1), c11 = this.canon(i + 1, k + 1);
        double d000 = this.gradDot(c00, j, du, dvw, dw);
        double d100 = this.gradDot(c10, j, du - 1.0, dvw, dw);
        double d010 = this.gradDot(c00, j + 1, du, dvw - 1.0, dw);
        double d110 = this.gradDot(c10, j + 1, du - 1.0, dvw - 1.0, dw);
        double d001 = this.gradDot(c01, j, du, dvw, dw - 1.0);
        double d101 = this.gradDot(c11, j, du - 1.0, dvw, dw - 1.0);
        double d011 = this.gradDot(c01, j + 1, du, dvw - 1.0, dw - 1.0);
        double d111 = this.gradDot(c11, j + 1, du - 1.0, dvw - 1.0, dw - 1.0);
        return lerp3(smoothstep(du), smoothstep(dv), smoothstep(dw), d000, d100, d010, d110, d001, d101, d011, d111);
    }

    /**
     * A corner's representative: the smaller (by row, then column) of the reductions of {@code q} and {@code 2c − q}
     * into the period cell {@code [0, 2mx) × [0, mz)}, packed as {@code row << 31 | column}, with {@link #FLIP} set if
     * it came from {@code 2c − q} (the corner takes the representative's gradient half-turned).
     */
    long canon(int i, int k) {
        long direct = this.reduce(i, k), turned = this.reduce((long) this.twoCx - i, (long) this.twoCz - k);
        return direct <= turned ? direct : turned | FLIP;
    }

    private long reduce(long i, long k) {
        long q = Math.floorDiv(k, this.mz);
        long row = k - q * this.mz;
        long column = Math.floorMod(i - q * this.mx, 2L * this.mx);
        return row << 31 | column;
    }

    private double gradDot(long corner, int j, double du, double dv, double dw) {
        int column = (int) (corner & LOW), row = (int) (corner >>> 31 & LOW);
        int hash = this.swapped ? this.p(this.p(this.p(row) + column) + j) : this.p(this.p(this.p(column) + j) + row);
        int[] g = GRADIENT[hash & 15];
        int gu, gv, gw;
        if (this.swapped) {
            gw = g[0];
            gu = g[1];
            gv = g[2];
        } else {
            gu = g[0];
            gv = g[1];
            gw = g[2];
        }
        if ((corner & FLIP) != 0) {
            if (this.odd) {
                gv = -gv;
            } else {
                gu = -gu;
                gw = -gw;
            }
        }
        return gu * du + gv * dv + gw * dw;
    }

    private int p(int index) {
        return this.p[index & 0xFF] & 0xFF;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double smoothstep(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double lerp3(double tx, double ty, double tz, double v000, double v100, double v010, double v110, double v001,
                                double v101, double v011, double v111) {
        return lerp(tz, lerp(ty, lerp(tx, v000, v100), lerp(tx, v010, v110)), lerp(ty, lerp(tx, v001, v101), lerp(tx, v011, v111)));
    }

    /** How much the octave's input is stretched along world x: lattice cells per vanilla cell. */
    public double stretchX() {
        return this.stretchX;
    }

    public double stretchZ() {
        return this.stretchZ;
    }

    public int cellsAlongHalfA() {
        return this.mx;
    }

    public int cellsAlongB() {
        return this.mz;
    }

    @Override
    public NoiseSymmetry symmetry() {
        return this.symmetry;
    }

    @Override
    public String describe() {
        return String.format(Locale.ROOT, "lattice %s %s, cell %.3f blocks -> %.3f x %.3f, stretch %.4f x %.4f",
            this.symmetry.axes(), this.symmetry.parity(), 1.0 / this.symmetry.scale(), 1.0 / (this.symmetry.scale() * this.stretchX),
            1.0 / (this.symmetry.scale() * this.stretchZ), this.stretchX, this.stretchZ);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrbifoldLattice that && this.symmetry.equals(that.symmetry) && this.freeOffset == that.freeOffset
            && this.mx == that.mx && this.mz == that.mz && this.stretchX == that.stretchX && this.stretchZ == that.stretchZ
            && this.originX == that.originX && this.originZ == that.originZ;
    }

    @Override
    public int hashCode() {
        return (this.symmetry.hashCode() * 31 + this.mx) * 31 + this.mz;
    }
}
