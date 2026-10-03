package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Biome boundary jitter is hashed from quart coordinates; hash the canonical quart (period W / 4). */
@Mixin(BiomeManager.class)
abstract class BiomeManagerMixin {

    @ModifyVariable(method = "getFiddledDistance", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int alpha_omega$canonQuartX(int quartX) {
        return Math.floorMod(quartX, Wrap.PERIOD >> 2);
    }

    @ModifyVariable(method = "getFiddledDistance", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int alpha_omega$canonQuartZ(int quartZ) {
        return Math.floorMod(quartZ, Wrap.PERIOD >> 2);
    }
}
