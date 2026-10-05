package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.Map;
import java.util.TreeMap;

/** Makes one octave invariant: a {@link OrbifoldLattice} where its cells fit the lattice, else a {@link SpectralNoise}. */
public final class InvariantOctaves {

    /** Every kind of octave made so far (by description), with a count, for reports. */
    private static final Map<String, Integer> MADE = new TreeMap<>();

    private InvariantOctaves() {
    }

    /**
     * @param p          the octave's permutation; also seeds the choices made for it
     * @param freeOffset vanilla's offset along the octave's free axis
     */
    public static InvariantOctave create(OrbifoldGeometry geometry, NoiseSymmetry symmetry, byte[] p, double freeOffset) {
        long seed = seed(p, symmetry);
        InvariantOctave octave = OrbifoldLattice.create(geometry, symmetry, p, freeOffset, seed);
        if (octave == null) octave = SpectralNoise.create(geometry, symmetry, freeOffset, seed);
        synchronized (MADE) {
            MADE.merge(octave.describe(), 1, Integer::sum);
        }
        return octave;
    }

    /** A seed from the octave's permutation and what it is used for, so equal noises make equal octaves. */
    static long seed(byte[] p, NoiseSymmetry symmetry) {
        long h = 0x9E3779B97F4A7C15L;
        for (byte b : p) h = (h ^ (b & 0xFF)) * 0x100000001B3L;
        h ^= Double.doubleToLongBits(symmetry.scale()) * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) symmetry.axes().ordinal() << 40 ^ (long) symmetry.parity().ordinal() << 48;
        return h ^ h >>> 29;
    }

    /** The octaves made so far, one line per kind with how many: for logs and the terrain report. */
    public static String summary() {
        StringBuilder out = new StringBuilder();
        synchronized (MADE) {
            MADE.forEach((kind, count) -> out.append(String.format("%5d x %s%n", count, kind)));
        }
        return out.toString();
    }
}
