package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.mixin.worldgen.noise.NormalNoiseAccessor;
import g_mungus.alpha_omega.mixin.worldgen.noise.PerlinNoiseAccessor;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.jetbrains.annotations.Nullable;

/**
 * Makes vanilla noise invariant under an orbifold's {@code Γ} ({@code alpha-omega-best-wrapping-plan.md} §8,
 * {@code orbifold-implementation.md} phase 9): each octave of a noise gets an {@link InvariantOctave} for the scale it
 * is sampled at. Scales are in the noise's own input units per block: a density function sampling
 * {@code noise(x * s, y, z * s)} needs scale {@code s}.
 */
public final class InvariantNoises {

    /** The orbifold whose {@code RandomState} is being built on this thread. */
    private static final ThreadLocal<OrbifoldGeometry> CREATING = new ThreadLocal<>();

    private InvariantNoises() {
    }

    /** Runs {@code create} (which builds a {@code RandomState}) for an orbifold's noise. */
    public static <T> T creating(OrbifoldGeometry geometry, Supplier<T> create) {
        OrbifoldGeometry previous = CREATING.get();
        CREATING.set(geometry);
        try {
            return create.get();
        } finally {
            CREATING.set(previous);
        }
    }

    /** The orbifold whose noise is being built on this thread, if any. */
    @Nullable
    public static OrbifoldGeometry creating() {
        return CREATING.get();
    }

    public static void configure(ImprovedNoise octave, OrbifoldGeometry geometry, NoiseSymmetry symmetry) {
        InvariantOctaveHolder holder = (InvariantOctaveHolder) (Object) octave;
        double free = symmetry.axes() == NoiseSymmetry.Axes.SHIFT_B ? octave.zo : octave.yo;
        holder.alpha_omega$setInvariant(InvariantOctaves.create(geometry, symmetry, holder.alpha_omega$permutation(), free));
    }

    /** Every octave of {@code noise}, octave {@code i} sampled at {@code lowestFreqInputFactor · 2^i} times the input. */
    public static void configure(PerlinNoise noise, OrbifoldGeometry geometry, NoiseSymmetry symmetry) {
        PerlinNoiseAccessor accessor = (PerlinNoiseAccessor) noise;
        double frequency = accessor.alpha_omega$getLowestFreqInputFactor();
        for (ImprovedNoise octave : accessor.alpha_omega$getNoiseLevels()) {
            if (octave != null) configure(octave, geometry, symmetry.withScale(symmetry.scale() * frequency));
            frequency *= 2.0;
        }
    }

    /**
     * Makes {@code noise} invariant for {@code symmetry}, unless it already is for another.
     *
     * @return whether {@code noise} is now invariant for {@code symmetry} (false: unchanged, made for another)
     */
    public static boolean configure(NormalNoise noise, OrbifoldGeometry geometry, NoiseSymmetry symmetry) {
        InvariantNormalNoise state = (InvariantNormalNoise) noise;
        NoiseSymmetry current = state.alpha_omega$symmetry();
        if (current != null) return current.equals(symmetry);
        NormalNoiseAccessor accessor = (NormalNoiseAccessor) noise;
        configure(accessor.alpha_omega$getFirst(), geometry, symmetry);
        configure(accessor.alpha_omega$getSecond(), geometry, symmetry.withScale(symmetry.scale() * NoiseSymmetry.NORMAL_NOISE_SECOND_FACTOR));
        state.alpha_omega$setSymmetry(symmetry);
        return true;
    }

    /** Whether {@code noise} is invariant for {@code symmetry} already. */
    public static boolean invariantFor(NormalNoise noise, NoiseSymmetry symmetry) {
        return symmetry.equals(((InvariantNormalNoise) noise).alpha_omega$symmetry());
    }
}
