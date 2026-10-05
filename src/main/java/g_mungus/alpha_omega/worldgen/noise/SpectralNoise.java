package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * A Perlin octave too coarse for {@link OrbifoldLattice}, replaced by a random sum of plane waves on the dual lattice
 * about the cone point {@code N} ({@code alpha-omega-best-wrapping-plan.md} §8):
 *
 * <pre>f(p, v) = Σ A_k · cos(2π k·(p − N)) · Y_j(k)(v)</pre>
 *
 * over dual vectors {@code k} of the lattice ({@code k·L1} and {@code k·L2} whole), one of each {@code ±k} pair, so
 * {@code f} is periodic on the lattice and even about {@code N}; with {@code sin} in place of {@code cos} it is odd, for
 * {@link NoiseSymmetry.Parity#ODD}. {@code p} is the octave's input divided by its scale (so a shifted input moves the
 * point, as it moves a vanilla octave's).
 *
 * <p><b>Matching the octave.</b> The waves are those whose frequency is in the octave's band, {@code [½/√2, ½·√2)}
 * cycles per vanilla cell: adjacent octaves of one noise get disjoint bands that together cover its spectrum. The
 * amplitudes are Gaussian, scaled so the sum has the standard deviation of a vanilla octave ({@link #PERLIN_STD}). The
 * free axis {@code v} (noise y, or noise z for {@code shift_b}, with vanilla's offset) varies through
 * {@link #Y_WAVES} random waves of one cycle per one to two noise units, as an octave varies along it. If no dual vector
 * falls in the band (an octave coarser than the world), an even octave is one random constant and an odd one is zero.
 */
public final class SpectralNoise implements InvariantOctave {

    /** The standard deviation of one vanilla Perlin octave at a random point (measured, see the unit test). */
    public static final double PERLIN_STD = 0.270;
    public static final double BAND_LOW = 0.5 / Math.sqrt(2.0);
    public static final double BAND_HIGH = 0.5 * Math.sqrt(2.0);
    public static final int Y_WAVES = 4;

    private final NoiseSymmetry symmetry;
    private final boolean swapped;
    private final boolean odd;
    private final double freeOffset;
    private final double nx;
    private final double nz;
    /** Base angular frequencies per block: x waves are multiples of {@code 2π/a}, z waves of {@code 2π/(2b)}. */
    private final double alpha;
    private final double beta;
    private final int[] xIndex;
    private final int[] zIndex;
    private final double[] amplitude;
    private final int[] yWave;
    private final double[] yFrequency;
    private final double[] yPhase;
    private final int maxX;
    private final int maxZ;
    /** For an octave with no wave in its band: its constant (even), or 0 (odd). */
    private final double constant;
    private final ThreadLocal<double[][]> scratch;

    private SpectralNoise(NoiseSymmetry symmetry, double freeOffset, OrbifoldGeometry geometry, int[] xIndex, int[] zIndex,
                          double[] amplitude, int[] yWave, double[] yFrequency, double[] yPhase, double constant) {
        this.symmetry = symmetry;
        this.swapped = symmetry.axes() == NoiseSymmetry.Axes.SHIFT_B;
        this.odd = symmetry.parity() == NoiseSymmetry.Parity.ODD;
        this.freeOffset = freeOffset;
        OrbifoldGeometry.ConePoint n = geometry.conePoints().get(0);
        this.nx = n.x();
        this.nz = n.z();
        this.alpha = 2.0 * Math.PI / geometry.a;
        this.beta = 2.0 * Math.PI / (2.0 * geometry.b);
        this.xIndex = xIndex;
        this.zIndex = zIndex;
        this.amplitude = amplitude;
        this.yWave = yWave;
        this.yFrequency = yFrequency;
        this.yPhase = yPhase;
        this.maxX = Arrays.stream(xIndex).map(Math::abs).max().orElse(0);
        this.maxZ = Arrays.stream(zIndex).map(Math::abs).max().orElse(0);
        this.constant = constant;
        int sizeX = this.maxX + 1, sizeZ = this.maxZ + 1;
        this.scratch = ThreadLocal.withInitial(() -> new double[][] {new double[sizeX], new double[sizeX], new double[sizeZ], new double[sizeZ], new double[Y_WAVES]});
    }

    /** The spectral octave for a symmetry, seeded by {@code seed}. */
    public static SpectralNoise create(OrbifoldGeometry geometry, NoiseSymmetry symmetry, double freeOffset, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        double cell = 1.0 / symmetry.scale();
        double low = BAND_LOW / cell, high = BAND_HIGH / cell;
        // Dual vectors k = (n1 / a, m / (2b)) with m = 2·n2 − n1, so m ≡ n1 (mod 2).
        int limitX = (int) Math.floor(high * geometry.a), limitZ = (int) Math.floor(high * 2.0 * geometry.b);
        List<int[]> waves = new ArrayList<>();
        for (int n1 = 0; n1 <= limitX; n1++) {
            for (int m = -limitZ; m <= limitZ; m++) {
                if (Math.floorMod(m - n1, 2) != 0) continue;
                if (n1 == 0 && m <= 0) continue;
                double kx = (double) n1 / geometry.a, kz = (double) m / (2.0 * geometry.b);
                double frequency = Math.hypot(kx, kz);
                if (frequency >= low && frequency < high) waves.add(new int[] {n1, m});
            }
        }
        double[] yFrequency = new double[Y_WAVES], yPhase = new double[Y_WAVES];
        for (int j = 0; j < Y_WAVES; j++) {
            yFrequency[j] = 2.0 * Math.PI * (BAND_LOW + random.nextDouble() * (BAND_HIGH - BAND_LOW));
            yPhase[j] = 2.0 * Math.PI * random.nextDouble();
        }
        int count = waves.size();
        int[] xIndex = new int[count], zIndex = new int[count], yWave = new int[count];
        double[] amplitude = new double[count];
        // cos² averages ½ in the plane and ½ along the free axis.
        double std = count == 0 ? 0.0 : PERLIN_STD * Math.sqrt(4.0 / count);
        for (int t = 0; t < count; t++) {
            xIndex[t] = waves.get(t)[0];
            zIndex[t] = waves.get(t)[1];
            amplitude[t] = gaussian(random) * std;
            yWave[t] = random.nextInt(Y_WAVES);
        }
        double constant = count == 0 && symmetry.parity() == NoiseSymmetry.Parity.EVEN ? gaussian(random) * PERLIN_STD * Math.sqrt(2.0) : 0.0;
        return new SpectralNoise(symmetry, freeOffset, geometry, xIndex, zIndex, amplitude, yWave, yFrequency, yPhase, constant);
    }

    private static double gaussian(SplittableRandom random) {
        double u = 1.0 - random.nextDouble(), v = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
    }

    @Override
    public double noise(double x, double y, double z, double yScale, double yMax) {
        double inU = this.swapped ? y : x, inW = this.swapped ? x : z, v = (this.swapped ? z : y) + this.freeOffset;
        double ys0 = Math.cos(this.yFrequency[0] * v + this.yPhase[0]);
        if (this.amplitude.length == 0) return this.constant * ys0;
        double[][] s = this.scratch.get();
        double[] cx = s[0], sx = s[1], cz = s[2], sz = s[3], ys = s[4];
        ys[0] = ys0;
        for (int j = 1; j < Y_WAVES; j++) ys[j] = Math.cos(this.yFrequency[j] * v + this.yPhase[j]);
        double px = inU / this.symmetry.scale() - this.nx, pz = inW / this.symmetry.scale() - this.nz;
        harmonics(this.alpha * px, this.maxX, cx, sx);
        harmonics(this.beta * pz, this.maxZ, cz, sz);
        double total = 0.0;
        for (int t = 0; t < this.amplitude.length; t++) {
            int n1 = this.xIndex[t], m = this.zIndex[t];
            double cosX = cx[n1], sinX = sx[n1];
            double cosZ = cz[Math.abs(m)], sinZ = m < 0 ? -sz[-m] : sz[m];
            double wave = this.odd ? sinX * cosZ + cosX * sinZ : cosX * cosZ - sinX * sinZ;
            total += this.amplitude[t] * wave * ys[this.yWave[t]];
        }
        return total;
    }

    /** {@code cos(nθ)} and {@code sin(nθ)} for {@code n = 0..max}, by the angle-addition recurrence. */
    private static void harmonics(double theta, int max, double[] cos, double[] sin) {
        double c1 = Math.cos(theta), s1 = Math.sin(theta);
        cos[0] = 1.0;
        sin[0] = 0.0;
        for (int n = 1; n <= max; n++) {
            cos[n] = cos[n - 1] * c1 - sin[n - 1] * s1;
            sin[n] = sin[n - 1] * c1 + cos[n - 1] * s1;
        }
    }

    public int terms() {
        return this.amplitude.length;
    }

    @Override
    public NoiseSymmetry symmetry() {
        return this.symmetry;
    }

    @Override
    public String describe() {
        return String.format(Locale.ROOT, "spectral %s %s, cell %.1f blocks, %d waves%s", this.symmetry.axes(), this.symmetry.parity(),
            1.0 / this.symmetry.scale(), this.terms(), this.terms() == 0 ? (this.odd ? " (zero)" : " (constant)") : "");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SpectralNoise that && this.symmetry.equals(that.symmetry) && this.freeOffset == that.freeOffset
            && this.constant == that.constant && Arrays.equals(this.amplitude, that.amplitude) && Arrays.equals(this.xIndex, that.xIndex)
            && Arrays.equals(this.zIndex, that.zIndex) && Arrays.equals(this.yFrequency, that.yFrequency) && Arrays.equals(this.yPhase, that.yPhase);
    }

    @Override
    public int hashCode() {
        return this.symmetry.hashCode() * 31 + Arrays.hashCode(this.amplitude);
    }
}
