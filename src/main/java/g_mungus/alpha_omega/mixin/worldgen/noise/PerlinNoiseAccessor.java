package g_mungus.alpha_omega.mixin.worldgen.noise;

import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PerlinNoise.class)
public interface PerlinNoiseAccessor {

    @Accessor("noiseLevels")
    ImprovedNoise[] alpha_omega$getNoiseLevels();

    @Accessor("lowestFreqInputFactor")
    double alpha_omega$getLowestFreqInputFactor();
}
