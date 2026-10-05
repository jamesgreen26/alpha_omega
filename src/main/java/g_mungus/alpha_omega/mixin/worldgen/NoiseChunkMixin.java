package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.noise.CellCentres;
import g_mungus.alpha_omega.worldgen.noise.GeometryHolder;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A noise chunk (and the aquifer it builds) generates in its noise's orbifold; there, it interpolates at block centres. */
@Mixin(NoiseChunk.class)
abstract class NoiseChunkMixin implements GeometryHolder, CellCentres {

    @Shadow
    @Final
    private int cellWidth;

    @Unique
    private OrbifoldGeometry alpha_omega$geometry;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$stamp(CallbackInfo ci, @Local(argsOnly = true) RandomState randomState) {
        this.alpha_omega$geometry = ((InvariantNoiseSource) (Object) randomState).alpha_omega$geometry();
    }

    /**
     * Interpolated terrain is sampled at block centres, not minimum corners: a half turn maps a block's centre to its
     * image block's centre (and the cell corners to cell corners), so the interpolated density of a block equals its
     * image's exactly across the folds. Vanilla's corner sampling would put the image one block off.
     */
    @ModifyVariable(method = "updateForX", at = @At("HEAD"), argsOnly = true)
    private double alpha_omega$centreX(double fraction) {
        return fraction + this.alpha_omega$centreOffset();
    }

    @ModifyVariable(method = "updateForZ", at = @At("HEAD"), argsOnly = true)
    private double alpha_omega$centreZ(double fraction) {
        return fraction + this.alpha_omega$centreOffset();
    }

    @Override
    public double alpha_omega$centreOffset() {
        return this.alpha_omega$geometry == null ? 0.0 : 0.5 / this.cellWidth;
    }

    @Override
    public OrbifoldGeometry alpha_omega$geometry() {
        return this.alpha_omega$geometry;
    }
}
