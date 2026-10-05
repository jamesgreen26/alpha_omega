package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.worldgen.noise.InvariantOctave;
import g_mungus.alpha_omega.worldgen.noise.InvariantOctaveHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * An octave with an {@link InvariantOctave} samples through it instead ({@code worldgen.noise.OrbifoldLattice},
 * {@code SpectralNoise}); every other octave is vanilla's.
 */
@Mixin(ImprovedNoise.class)
abstract class ImprovedNoiseMixin implements InvariantOctaveHolder {

    @Shadow
    @Final
    private byte[] p;
    @Shadow
    @Final
    public double xo;
    @Shadow
    @Final
    public double yo;
    @Shadow
    @Final
    public double zo;

    @Unique
    private InvariantOctave alpha_omega$invariant;

    @Shadow
    private double sampleAndLerp(int gridX, int gridY, int gridZ, double deltaX, double weirdDeltaY, double deltaZ, double deltaY) {
        throw new AssertionError();
    }

    @Override
    public InvariantOctave alpha_omega$invariant() {
        return this.alpha_omega$invariant;
    }

    @Override
    public void alpha_omega$setInvariant(InvariantOctave octave) {
        this.alpha_omega$invariant = octave;
    }

    @Override
    public byte[] alpha_omega$permutation() {
        return this.p;
    }

    /**
     * @author alpha_omega
     * @reason The innermost worldgen loop: an invariant octave replaces the whole sample, so this is overwritten rather
     * than injected into, to avoid a callback allocation per sample. Identical to vanilla for octaves without one.
     */
    @Overwrite
    @Deprecated
    public double noise(double x, double y, double z, double yScale, double yMax) {
        InvariantOctave invariant = this.alpha_omega$invariant;
        if (invariant != null) return invariant.noise(x, y, z, yScale, yMax);
        double d0 = x + this.xo;
        double d1 = y + this.yo;
        double d2 = z + this.zo;
        int i = Mth.floor(d0);
        int j = Mth.floor(d1);
        int k = Mth.floor(d2);
        double d3 = d0 - (double) i;
        double d4 = d1 - (double) j;
        double d5 = d2 - (double) k;
        double d6;
        if (yScale != 0.0) {
            double d7;
            if (yMax >= 0.0 && yMax < d4) {
                d7 = yMax;
            } else {
                d7 = d4;
            }
            d6 = (double) Mth.floor(d7 / yScale + 1.0E-7F) * yScale;
        } else {
            d6 = 0.0;
        }
        return this.sampleAndLerp(i, j, k, d3, d4 - d6, d5, d4);
    }
}
