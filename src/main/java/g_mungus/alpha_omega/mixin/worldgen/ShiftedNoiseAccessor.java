package g_mungus.alpha_omega.mixin.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the cube's router visitor see inside vanilla's (package-private) shifted noise function. */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public interface ShiftedNoiseAccessor {

    @Accessor("shiftX")
    DensityFunction alpha_omega$shiftX();

    @Accessor("shiftY")
    DensityFunction alpha_omega$shiftY();

    @Accessor("shiftZ")
    DensityFunction alpha_omega$shiftZ();

    @Accessor("xzScale")
    double alpha_omega$xzScale();

    @Accessor("yScale")
    double alpha_omega$yScale();

    @Accessor("noise")
    DensityFunction.NoiseHolder alpha_omega$noise();
}
