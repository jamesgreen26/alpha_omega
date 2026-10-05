package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.worldgen.noise.InvariantNormalNoise;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NormalNoise.class)
abstract class NormalNoiseMixin implements InvariantNormalNoise {

    @Unique
    private volatile NoiseSymmetry alpha_omega$symmetry;

    @Override
    public NoiseSymmetry alpha_omega$symmetry() {
        return this.alpha_omega$symmetry;
    }

    @Override
    public void alpha_omega$setSymmetry(NoiseSymmetry symmetry) {
        this.alpha_omega$symmetry = symmetry;
    }
}
