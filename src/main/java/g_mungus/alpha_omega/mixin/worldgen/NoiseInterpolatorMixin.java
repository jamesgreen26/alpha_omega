package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.worldgen.noise.CellCentres;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The per-cell fill of an interpolated function (which the final density's cell cache reads its blocks from) samples
 * block centres in an orbifold, as {@code NoiseChunkMixin} does for the column-by-column interpolation.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator")
abstract class NoiseInterpolatorMixin {

    @Shadow
    @Final
    NoiseChunk this$0;

    @ModifyArg(method = "compute", index = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp3(DDDDDDDDDDD)D"))
    private double alpha_omega$centreX(double fraction) {
        return fraction + ((CellCentres) this.this$0).alpha_omega$centreOffset();
    }

    @ModifyArg(method = "compute", index = 2, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp3(DDDDDDDDDDD)D"))
    private double alpha_omega$centreZ(double fraction) {
        return fraction + ((CellCentres) this.this$0).alpha_omega$centreOffset();
    }
}
