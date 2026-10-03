package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.levelgen.OreVeinifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Ore vein blocks are picked with a per-block random; seed it with the canonical position. */
@Mixin(OreVeinifier.class)
abstract class OreVeinifierMixin {

    @ModifyArg(method = "lambda$create$0",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"), index = 0)
    private static int alpha_omega$canonX(int x) {
        return Wrap.canonBlock(x);
    }

    @ModifyArg(method = "lambda$create$0",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"), index = 2)
    private static int alpha_omega$canonZ(int z) {
        return Wrap.canonBlock(z);
    }
}
