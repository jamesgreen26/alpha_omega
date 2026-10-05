package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseUser;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code shift} samples {@code offsetNoise(x / 4, y / 4, z / 4) * 4}, a displacement along an axis it does not know, so
 * whether a half turn negates it depends on where it is used. The overworld does not use it; if a data pack does, it is
 * left vanilla (and not invariant), with a warning.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Shift")
abstract class ShiftMixin implements InvariantNoiseUser {

    @Override
    public void alpha_omega$makeInvariant(InvariantNoiseSource source) {
        if (source.alpha_omega$geometry() != null) {
            AlphaOmegaMod.LOGGER.warn("An orbifold's noise router uses minecraft:shift, which is left vanilla: its seams will show");
        }
    }
}
