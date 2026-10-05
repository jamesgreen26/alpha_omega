package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseUser;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoises;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * The base 3D terrain noise drives its octaves directly: {@code getOctaveNoise(i)} (highest frequency first) at
 * {@code x * xzMultiplier / xzFactor / 2^i} for the main noise and {@code x * xzMultiplier / 2^j} for the limit noises.
 * It is made per {@code RandomState}, so its octaves are made invariant in place, once.
 */
@Mixin(BlendedNoise.class)
abstract class BlendedNoiseMixin implements InvariantNoiseUser {

    @Shadow
    @Final
    private PerlinNoise minLimitNoise;
    @Shadow
    @Final
    private PerlinNoise maxLimitNoise;
    @Shadow
    @Final
    private PerlinNoise mainNoise;
    @Shadow
    @Final
    private double xzMultiplier;
    @Shadow
    @Final
    private double xzFactor;

    @Unique
    private boolean alpha_omega$invariant;

    @Override
    public void alpha_omega$makeInvariant(InvariantNoiseSource source) {
        OrbifoldGeometry geometry = source.alpha_omega$geometry();
        if (geometry == null || this.alpha_omega$invariant) return;
        this.alpha_omega$invariant = true;
        double scale = 1.0;
        for (int i = 0; i < 8; i++) {
            alpha_omega$configure(this.mainNoise.getOctaveNoise(i), geometry, this.xzMultiplier / this.xzFactor * scale);
            scale /= 2.0;
        }
        scale = 1.0;
        for (int j = 0; j < 16; j++) {
            alpha_omega$configure(this.minLimitNoise.getOctaveNoise(j), geometry, this.xzMultiplier * scale);
            alpha_omega$configure(this.maxLimitNoise.getOctaveNoise(j), geometry, this.xzMultiplier * scale);
            scale /= 2.0;
        }
    }

    @Unique
    private static void alpha_omega$configure(ImprovedNoise octave, OrbifoldGeometry geometry, double scale) {
        if (octave != null) InvariantNoises.configure(octave, geometry, NoiseSymmetry.even(scale));
    }
}
