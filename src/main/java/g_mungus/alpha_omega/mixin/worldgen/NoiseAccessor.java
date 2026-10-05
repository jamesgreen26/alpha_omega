package g_mungus.alpha_omega.mixin.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the cube's router visitor see inside vanilla's (package-private) noise function. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public interface NoiseAccessor {

    @Accessor("noise")
    DensityFunction.NoiseHolder alpha_omega$noise();

    @Accessor("xzScale")
    double alpha_omega$xzScale();

    @Accessor("yScale")
    double alpha_omega$yScale();
}
