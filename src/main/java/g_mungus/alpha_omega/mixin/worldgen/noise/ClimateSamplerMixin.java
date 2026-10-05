package g_mungus.alpha_omega.mixin.worldgen.noise;

import g_mungus.alpha_omega.worldgen.noise.CenteredClimate;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** An orbifold's climate is sampled at quart centres ({@link CenteredClimate}). */
@Mixin(Climate.Sampler.class)
abstract class ClimateSamplerMixin implements CenteredClimate {

    @Unique
    private int alpha_omega$offset;

    @Override
    public void alpha_omega$sampleCentres() {
        this.alpha_omega$offset = 2;
    }

    @ModifyArg(method = "sample", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$centreX(int x) {
        return x + this.alpha_omega$offset;
    }

    @ModifyArg(method = "sample", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$centreZ(int z) {
        return z + this.alpha_omega$offset;
    }
}
