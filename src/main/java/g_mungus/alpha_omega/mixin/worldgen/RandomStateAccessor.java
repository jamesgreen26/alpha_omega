package g_mungus.alpha_omega.mixin.worldgen;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The cube world swaps in its own router (and the climate sampler built from it) once the state is made. */
@Mixin(RandomState.class)
public interface RandomStateAccessor {

    @Mutable
    @Accessor("router")
    void alpha_omega$setRouter(NoiseRouter router);

    @Mutable
    @Accessor("sampler")
    void alpha_omega$setSampler(Climate.Sampler sampler);
}
