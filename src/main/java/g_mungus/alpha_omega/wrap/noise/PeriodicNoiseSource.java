package g_mungus.alpha_omega.wrap.noise;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Duck interface on {@code RandomState}, which owns the shared noise instances. Noise is made periodic in place;
 * if the same instance is already needed at a different period (one noise sampled at two scales), a fresh,
 * identically seeded instance is created for the new use.
 */
public interface PeriodicNoiseSource {

    NormalNoise alpha_omega$periodic(ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, double px, double py, double pz);

    DensityFunction.NoiseHolder alpha_omega$periodic(DensityFunction.NoiseHolder holder, double px, double py, double pz);
}
