package g_mungus.alpha_omega.mixin.worldgen.noise;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NormalNoise.class)
public interface NormalNoiseAccessor {

    @Accessor("first")
    PerlinNoise alpha_omega$getFirst();

    @Accessor("second")
    PerlinNoise alpha_omega$getSecond();
}
