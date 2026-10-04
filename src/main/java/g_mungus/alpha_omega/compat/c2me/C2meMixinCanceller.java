package g_mungus.alpha_omega.compat.c2me;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import java.util.List;

/**
 * Cancels the C2ME optimizations that reimplement worldgen math Alpha Omega makes periodic. They replace the
 * method bodies the periodic noise and aquifer mixins hook, so terrain would stop tiling. C2ME's threading, IO and
 * chunk system modules are kept.
 */
public final class C2meMixinCanceller implements MixinCanceller {

    private static final List<String> CANCELLED = List.of(
        // Overwrites ImprovedNoise.noise / sampleAndLerp (lattice wrapping) and PerlinNoise.getValue(DDD) (octave
        // input reduction by period).
        "com.ishland.c2me.opts.math.mixin.",
        // Overwrites Aquifer$NoiseBasedAquifer.computeSubstance, where aquifer cells are canonicalized.
        "com.ishland.c2me.opts.worldgen.vanilla.mixin.aquifer."
    );

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        for (String prefix : CANCELLED) {
            if (mixinClassName.startsWith(prefix)) return true;
        }
        return false;
    }
}
