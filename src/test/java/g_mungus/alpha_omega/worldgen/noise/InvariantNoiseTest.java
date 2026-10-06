package g_mungus.alpha_omega.worldgen.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Lattice-hashed and spectral octaves are invariant under {@code Γ}: periodic on the lattice and even (or odd) about every cone point. */
class InvariantNoiseTest {

    private static final double EPS = 1e-9;
    /** Octave scales (noise units per block) from vanilla's overworld, fine to coarse, including off-power-of-two ones. */
    private static final double[] LATTICE_SCALES = {1.0 / 0.4676, 1.0 / 3.74, 0.25, 1.0 / 59.8, 1.0 / 191.5, 1.0 / 256.0, 1.0 / 503.0, 171.103 / 1024.0};
    private static final double[] SPECTRAL_SCALES = {1.0 / 1024.0, 1.0 / 2048.0, 1.0 / 4096.0, 1.0 / 8192.0, 1.0 / 40000.0};

    private static byte[] permutation(long seed) {
        Random random = new Random(seed);
        byte[] p = new byte[256];
        for (int i = 0; i < 256; i++) p[i] = (byte) i;
        for (int k = 0; k < 256; k++) {
            int j = random.nextInt(256 - k);
            byte b = p[k];
            p[k] = p[k + j];
            p[k + j] = b;
        }
        return p;
    }

    /** The noise at a world point, sampled the way its axes say (with an input scale). */
    private static double sample(InvariantOctave octave, double x, double free, double z) {
        double s = octave.symmetry().scale();
        return octave.symmetry().axes() == NoiseSymmetry.Axes.SHIFT_B ? octave.noise(z * s, x * s, free, 0, 0) : octave.noise(x * s, free, z * s, 0, 0);
    }

    private static List<InvariantOctave> octaves(OrbifoldGeometry g, double[] scales, boolean lattice) {
        List<InvariantOctave> octaves = new ArrayList<>();
        long seed = 1;
        for (double scale : scales) {
            for (NoiseSymmetry.Axes axes : NoiseSymmetry.Axes.values()) {
                for (NoiseSymmetry.Parity parity : NoiseSymmetry.Parity.values()) {
                    NoiseSymmetry symmetry = new NoiseSymmetry(scale, axes, parity);
                    byte[] p = permutation(seed++);
                    InvariantOctave octave = lattice ? OrbifoldLattice.create(g, symmetry, p, 17.3, seed) : SpectralNoise.create(g, symmetry, 17.3, seed);
                    assertNotNull(octave, "no lattice for " + symmetry);
                    octaves.add(octave);
                }
            }
        }
        return octaves;
    }

    private static void assertInvariant(OrbifoldGeometry g, InvariantOctave octave) {
        Random random = new Random(42);
        double sign = octave.symmetry().parity() == NoiseSymmetry.Parity.ODD ? -1.0 : 1.0;
        double largest = 0.0;
        for (int n = 0; n < 400; n++) {
            double x = g.minX + random.nextDouble() * g.a, z = g.northRow + (random.nextDouble() - 0.5) * g.b, y = random.nextDouble() * 40.0;
            double f = sample(octave, x, y, z);
            largest = Math.max(largest, Math.abs(f));
            assertEquals(f, sample(octave, x + g.a, y, z), EPS, () -> octave.describe() + ": not periodic along L1");
            assertEquals(f, sample(octave, x - g.a / 2.0, y, z - g.b), EPS, () -> octave.describe() + ": not periodic along L2");
            for (OrbifoldGeometry.ConePoint c : g.conePoints()) {
                assertEquals(sign * f, sample(octave, 2.0 * c.x() - x, y, 2.0 * c.z() - z), EPS,
                    () -> octave.describe() + ": not " + (sign > 0 ? "even" : "odd") + " about " + c.name());
            }
        }
        assertTrue(largest > 0.05, octave.describe() + " is flat: " + largest);
    }

    /** The lattice scales, less those that go spectral at a size (at the small size, the 503-block cell). */
    private static double[] latticeScales(OrbifoldGeometry g) {
        return Arrays.stream(LATTICE_SCALES).filter(s -> OrbifoldLattice.create(g, NoiseSymmetry.even(s), permutation(0), 0.0, 0) != null).toArray();
    }

    @Test
    void latticeOctavesAreInvariant() {
        for (OrbifoldSize k : OrbifoldSize.PRESETS) {
            OrbifoldGeometry g = new OrbifoldGeometry(k, 4);
            for (InvariantOctave octave : octaves(g, latticeScales(g), true)) assertInvariant(g, octave);
        }
    }

    @Test
    void spectralOctavesAreInvariant() {
        for (OrbifoldSize k : OrbifoldSize.PRESETS) {
            OrbifoldGeometry g = new OrbifoldGeometry(k, 4);
            double[] scales = k == OrbifoldSize.SMALL ? new double[] {1.0 / 503.0, 1.0 / 1024.0, 1.0 / 2048.0, 1.0 / 4096.0} : SPECTRAL_SCALES;
            for (InvariantOctave octave : octaves(g, scales, false)) {
                if (((SpectralNoise) octave).terms() == 0) continue;
                assertInvariant(g, octave);
            }
        }
    }

    /**
     * Which of vanilla's octave cells are lattice and which spectral, per size. The k sizes all agree on cells up to 503
     * blocks; the small size's {@code a/2 = 1792 = 7·256} needs 12% stretch for 503-block cells, so they go spectral
     * there, and its 191.5-block cells are stretched 3.8% along x (0.3% at the k sizes). Cells of 1024 blocks are
     * lattice only at the large size, as before.
     */
    @Test
    void octaveKindsPerSize() {
        double[] cells = {0.4676, 3.74, 4.0, 1024.0 / 171.103, 59.8, 191.5, 256.0, 503.0, 1024.0, 2048.0};
        StringBuilder table = new StringBuilder();
        for (OrbifoldSize k : OrbifoldSize.PRESETS) {
            OrbifoldGeometry g = new OrbifoldGeometry(k, 4);
            StringBuilder spectral = new StringBuilder();
            for (double cell : cells) {
                OrbifoldLattice lattice = OrbifoldLattice.create(g, NoiseSymmetry.even(1.0 / cell), permutation(0), 0.0, 0);
                InvariantOctave octave = InvariantOctaves.create(g, NoiseSymmetry.even(1.0 / cell), permutation(0), 0.0);
                table.append(String.format(java.util.Locale.ROOT, "%-7s %8.2f: %s%n", k.id(), cell, octave.describe()));
                if (lattice == null) spectral.append(cell >= 1000 ? String.valueOf((int) cell) : String.valueOf(cell)).append(' ');
                assertEquals(lattice != null, octave instanceof OrbifoldLattice, octave.describe());
            }
            String expected = switch (k.id()) {
                case "small" -> "503.0 1024 2048 ";
                case "large" -> "2048 ";
                default -> "1024 2048 ";
            };
            assertEquals(expected, spectral.toString(), k.id());
        }
        System.out.print(table);
    }

    /** At a cone point an even octave has zero gradient: its values a step either side agree. */
    @Test
    void gradientIsFlatAtConePoints() {
        OrbifoldGeometry g = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
        List<InvariantOctave> octaves = new ArrayList<>(octaves(g, LATTICE_SCALES, true));
        octaves.addAll(octaves(g, SPECTRAL_SCALES, false));
        for (InvariantOctave octave : octaves) {
            if (octave.symmetry().parity() != NoiseSymmetry.Parity.EVEN) continue;
            for (OrbifoldGeometry.ConePoint c : g.conePoints()) {
                for (double h : new double[] {0.25, 1.0, 3.0}) {
                    assertEquals(sample(octave, c.x() + h, 5.0, c.z()), sample(octave, c.x() - h, 5.0, c.z()), EPS, octave.describe() + " at " + c.name());
                    assertEquals(sample(octave, c.x(), 5.0, c.z() + h), sample(octave, c.x(), 5.0, c.z() - h), EPS, octave.describe() + " at " + c.name());
                }
            }
        }
    }

    @Test
    void gradientSetIsClosedUnderTheTurn() {
        Set<List<Integer>> set = new HashSet<>();
        for (int[] g : OrbifoldLattice.GRADIENT) set.add(List.of(g[0], g[1], g[2]));
        for (int[] g : OrbifoldLattice.GRADIENT) {
            assertTrue(set.contains(List.of(-g[0], g[1], -g[2])), "half turn of " + Arrays.toString(g));
            assertTrue(set.contains(List.of(g[0], -g[1], g[2])), "negated half turn of " + Arrays.toString(g));
        }
    }

    /** No placement chosen leaves a corner fixed by a half turn; checked by brute force on small lattices. */
    @Test
    void noCornerIsFixed() {
        for (int mx = 1; mx <= 6; mx++) {
            for (int mz = 1; mz <= 6; mz++) {
                for (int cx = 0; cx <= 1; cx++) {
                    for (int cz = 0; cz <= 1; cz++) {
                        boolean fixed = false;
                        // 2q = 2c + n·(2mx, 0) + m·(mx, mz), for some corner q.
                        for (int n = -2; n <= 2 && !fixed; n++) {
                            for (int m = -2; m <= 2 && !fixed; m++) {
                                int twoQx = cx + 2 * n * mx + m * mx, twoQz = cz + m * mz;
                                fixed = twoQx % 2 == 0 && twoQz % 2 == 0;
                            }
                        }
                        assertEquals(fixed, OrbifoldLattice.hasFixedCorner(mx, mz, cx, cz), mx + "x" + mz + " at " + cx + "," + cz);
                    }
                }
                final int fmx = mx, fmz = mz;
                assertTrue(Arrays.stream(new int[][] {{1, 1}, {0, 1}, {1, 0}}).anyMatch(c -> !OrbifoldLattice.hasFixedCorner(fmx, fmz, c[0], c[1])),
                    "no placement for " + mx + "x" + mz);
            }
        }
    }

    /** A lattice octave is stretched at most {@link OrbifoldLattice#MAX_STRETCH}; past that the octave goes spectral. */
    @Test
    void stretchStaysWithinTheBound() {
        OrbifoldGeometry g = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
        int lattice = 0;
        for (double cell = 0.3; cell < 3000.0; cell *= 1.013) {
            OrbifoldLattice octave = OrbifoldLattice.create(g, NoiseSymmetry.even(1.0 / cell), permutation(3), 0.0, 7);
            if (octave == null) {
                assertTrue(cell > 300.0, "an octave of " + cell + " blocks should fit the lattice");
                continue;
            }
            lattice++;
            assertTrue(Math.abs(octave.stretchX() - 1.0) <= OrbifoldLattice.MAX_STRETCH + 1e-12, octave.describe());
            assertTrue(Math.abs(octave.stretchZ() - 1.0) <= OrbifoldLattice.MAX_STRETCH + 1e-12, octave.describe());
        }
        assertTrue(lattice > 500, "only " + lattice + " lattice octaves");
        assertNull(OrbifoldLattice.create(g, NoiseSymmetry.even(1.0 / 20000.0), permutation(3), 0.0, 7), "a world-sized cell cannot be a lattice");
        // Power-of-two cells up to 256 blocks fit exactly at every size.
        for (OrbifoldSize k : OrbifoldSize.PRESETS) {
            for (int cell = 1; cell <= 256; cell *= 2) {
                OrbifoldLattice octave = OrbifoldLattice.create(new OrbifoldGeometry(k, 4), NoiseSymmetry.even(1.0 / cell), permutation(3), 0.0, 7);
                assertNotNull(octave);
                assertEquals(1.0, octave.stretchX(), 1e-12, octave.describe());
                assertEquals(1.0, octave.stretchZ(), 1e-12, octave.describe());
            }
        }
    }

    /** {@link SpectralNoise#PERLIN_STD} is a Perlin octave's spread; a spectral octave has about the same. */
    @Test
    void spectralOctavesMatchPerlinSpread() {
        OrbifoldGeometry g = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
        Random random = new Random(5);
        double sum = 0, squares = 0;
        int samples = 0;
        for (int seed = 0; seed < 40; seed++) {
            OrbifoldLattice octave = OrbifoldLattice.create(g, NoiseSymmetry.even(1.0), permutation(seed), 0.0, seed);
            for (int n = 0; n < 2000; n++) {
                double f = octave.noise(random.nextDouble() * 1000, random.nextDouble() * 1000, random.nextDouble() * 1000, 0, 0);
                sum += f;
                squares += f * f;
                samples++;
            }
        }
        double perlin = Math.sqrt(squares / samples - (sum / samples) * (sum / samples));
        assertEquals(SpectralNoise.PERLIN_STD, perlin, 0.005, "measured Perlin octave spread");

        sum = 0;
        squares = 0;
        samples = 0;
        for (int seed = 0; seed < 40; seed++) {
            SpectralNoise octave = SpectralNoise.create(g, NoiseSymmetry.even(1.0 / 1024.0), 0.0, seed);
            assertTrue(octave.terms() > 50 && octave.terms() < 200, octave.describe());
            for (int n = 0; n < 500; n++) {
                double f = sample(octave, g.minX + random.nextDouble() * g.a, random.nextDouble() * 100, g.northRow + random.nextDouble() * g.b / 2.0);
                sum += f;
                squares += f * f;
                samples++;
            }
        }
        double spectral = Math.sqrt(squares / samples);
        assertEquals(SpectralNoise.PERLIN_STD, spectral, 0.04, "spectral octave spread");
    }

    /** An odd octave (a shift component) is zero at every cone point. */
    @Test
    void oddOctavesVanishAtConePoints() {
        OrbifoldGeometry g = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
        List<InvariantOctave> octaves = new ArrayList<>(octaves(g, LATTICE_SCALES, true));
        octaves.addAll(octaves(g, SPECTRAL_SCALES, false));
        for (InvariantOctave octave : octaves) {
            if (octave.symmetry().parity() != NoiseSymmetry.Parity.ODD) continue;
            for (OrbifoldGeometry.ConePoint c : g.conePoints()) assertEquals(0.0, sample(octave, c.x(), 3.0, c.z()), EPS, octave.describe());
        }
        assertFalse(octaves.isEmpty());
    }
}
