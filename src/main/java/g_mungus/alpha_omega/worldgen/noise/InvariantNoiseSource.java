package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface on {@code RandomState}, which owns the shared noise instances. While it is built, a noise is made
 * invariant in place; if it is already invariant for another symmetry (one noise sampled two ways), an identically
 * seeded copy is made for the new use. After that, shared noises are never changed: a later request gets a copy.
 */
public interface InvariantNoiseSource {

    /** The orbifold this noise generates, or null outside an orbifold world (then nothing is changed). */
    @Nullable
    OrbifoldGeometry alpha_omega$geometry();

    /** {@code holder}'s noise made invariant for {@code symmetry}, or a holder of a copy that is. */
    DensityFunction.NoiseHolder alpha_omega$invariant(DensityFunction.NoiseHolder holder, NoiseSymmetry symmetry);

    /** {@code noise} (the shared instance for {@code key}) if invariant for {@code symmetry}, else a copy that is. */
    NormalNoise alpha_omega$invariant(ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, NoiseSymmetry symmetry);
}
