package g_mungus.alpha_omega.worldgen.noise;

import org.jetbrains.annotations.Nullable;

/** Duck interface on {@code ImprovedNoise}: the invariant octave that samples in its place, if any. */
public interface InvariantOctaveHolder {

    @Nullable
    InvariantOctave alpha_omega$invariant();

    void alpha_omega$setInvariant(@Nullable InvariantOctave octave);

    /** The octave's permutation table (vanilla's {@code p}), not copied. */
    byte[] alpha_omega$permutation();
}
