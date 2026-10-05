package g_mungus.alpha_omega.worldgen.noise;

import org.jetbrains.annotations.Nullable;

/** Duck interface on {@code NormalNoise}: the symmetry its octaves were made invariant for, if any. */
public interface InvariantNormalNoise {

    @Nullable
    NoiseSymmetry alpha_omega$symmetry();

    void alpha_omega$setSymmetry(@Nullable NoiseSymmetry symmetry);
}
