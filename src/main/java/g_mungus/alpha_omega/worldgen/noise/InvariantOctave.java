package g_mungus.alpha_omega.worldgen.noise;

/**
 * One Perlin octave ({@code ImprovedNoise}) made invariant under {@code Γ}: it replaces the octave's sampling and keeps
 * its signature, so callers pass the same input (already scaled to the octave, plus any shift) as to vanilla.
 * {@link OrbifoldLattice} for octaves whose cells can tile the lattice; {@link SpectralNoise} for coarser ones.
 *
 * <p>Implementations are immutable and compare by value (excluding the octave's own permutation), so a noise's
 * equality can include them (C2ME's density compiler deduplicates noises by value).
 */
public sealed interface InvariantOctave permits OrbifoldLattice, SpectralNoise {

    /** The octave's value, as {@code ImprovedNoise.noise(x, y, z, yScale, yMax)}. */
    double noise(double x, double y, double z, double yScale, double yMax);

    /** The symmetry this octave was made for, with its scale in the octave's own input units. */
    NoiseSymmetry symmetry();

    /** A line for logs and reports: kind, cell size and stretch or term count. */
    String describe();
}
