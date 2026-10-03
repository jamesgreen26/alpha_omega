package g_mungus.alpha_omega.mixin.compat.xaeromap;

import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.region.LeveledRegion;
import xaero.map.world.MapDimension;

/**
 * Regions are loaded, kept and unloaded by distance from the player. Regions are canonical while the player is
 * lifted, so the distance is to the region's nearest image.
 */
@Mixin(LeveledRegion.class)
abstract class LeveledRegionMixin {

    @Shadow
    private static int comparisonX;
    @Shadow
    private static int comparisonZ;
    @Shadow
    protected static int comparisonLevel;
    @Shadow
    private static int comparisonLeafX;
    @Shadow
    private static int comparisonLeafZ;
    @Shadow
    protected int regionX;
    @Shadow
    protected int regionZ;
    @Shadow
    protected int level;

    @Shadow
    public abstract MapDimension getDim();

    @Inject(method = "distanceFromPlayer", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$wrappedDistance(CallbackInfoReturnable<Integer> cir) {
        int regionPeriod = this.alpha_omega$regionPeriod();
        if (regionPeriod == 0 || comparisonLevel > 31 || regionPeriod % (1 << comparisonLevel) != 0) return;
        int dx = (this.regionX << this.level >> comparisonLevel) - comparisonX;
        int dz = (this.regionZ << this.level >> comparisonLevel) - comparisonZ;
        cir.setReturnValue(alpha_omega$distance(dx, dz, regionPeriod >> comparisonLevel));
    }

    @Inject(method = "leafDistanceFromPlayer", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$wrappedLeafDistance(CallbackInfoReturnable<Integer> cir) {
        int regionPeriod = this.alpha_omega$regionPeriod();
        if (regionPeriod == 0) return;
        int dx = (this.regionX << this.level) - comparisonLeafX;
        int dz = (this.regionZ << this.level) - comparisonLeafZ;
        cir.setReturnValue(alpha_omega$distance(dx, dz, regionPeriod));
    }

    /** In chunks: 32 per region. */
    @Inject(method = "chunkDistanceFromPlayer", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$wrappedChunkDistance(CallbackInfoReturnable<Integer> cir) {
        int regionPeriod = this.alpha_omega$regionPeriod();
        if (regionPeriod == 0) return;
        int dx = (this.regionX << this.level << 5) - comparisonX;
        int dz = (this.regionZ << this.level << 5) - comparisonZ;
        cir.setReturnValue(alpha_omega$distance(dx, dz, regionPeriod << 5));
    }

    @Unique
    private int alpha_omega$regionPeriod() {
        MapDimension dimension = this.getDim();
        return dimension == null ? 0 : XaeroWraps.regionPeriod(XaeroWraps.of(dimension.getDimId()));
    }

    @Unique
    private static int alpha_omega$distance(int dx, int dz, int period) {
        dx = Math.floorMod(dx + period / 2, period) - period / 2;
        dz = Math.floorMod(dz + period / 2, period) - period / 2;
        return (int) Math.sqrt((double) dx * dx + (double) dz * dz);
    }
}
