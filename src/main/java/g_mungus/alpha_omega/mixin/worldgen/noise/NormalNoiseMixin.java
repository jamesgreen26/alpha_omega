package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.wrap.noise.PeriodicNormalNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NormalNoise.class)
abstract class NormalNoiseMixin implements PeriodicNormalNoise {

    @Unique
    private double[] alpha_omega$periods;

    @Override
    public double[] alpha_omega$getPeriods() {
        return this.alpha_omega$periods;
    }

    @Override
    public void alpha_omega$setPeriods(double[] periods) {
        this.alpha_omega$periods = periods;
    }
}
