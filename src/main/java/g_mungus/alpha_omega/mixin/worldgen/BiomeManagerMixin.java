package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Biome boundary jitter is hashed from quart coordinates; hash the canonical quart (period W / 4). */
@Mixin(BiomeManager.class)
abstract class BiomeManagerMixin {

    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @ModifyArg(method = "getBiome",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getFiddledDistance(JIIIDDD)D"), index = 1)
    private int alpha_omega$canonQuartX(int quartX) {
        return this.alpha_omega$canonQuart(quartX);
    }

    @ModifyArg(method = "getBiome",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;getFiddledDistance(JIIIDDD)D"), index = 3)
    private int alpha_omega$canonQuartZ(int quartZ) {
        return this.alpha_omega$canonQuart(quartZ);
    }

    @Unique
    private int alpha_omega$canonQuart(int quart) {
        Wrap wrap = this.noiseBiomeSource instanceof Level level ? Wrap.of(level) : Wrap.NONE;
        return wrap.enabled() ? Math.floorMod(quart, wrap.period >> 2) : quart;
    }
}
