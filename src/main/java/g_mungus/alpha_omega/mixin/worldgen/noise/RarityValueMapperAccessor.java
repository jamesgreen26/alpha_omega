package g_mungus.alpha_omega.mixin.worldgen.noise;

import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler$RarityValueMapper")
public interface RarityValueMapperAccessor {

    @Accessor("mapper")
    Double2DoubleFunction alpha_omega$getMapper();
}
