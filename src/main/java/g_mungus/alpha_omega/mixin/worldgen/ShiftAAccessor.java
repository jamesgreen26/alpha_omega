package g_mungus.alpha_omega.mixin.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's offset noise sampled at (x, 0, z): the x warp of the flat noises. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
public interface ShiftAAccessor {

    @Accessor("offsetNoise")
    DensityFunction.NoiseHolder alpha_omega$offsetNoise();
}
