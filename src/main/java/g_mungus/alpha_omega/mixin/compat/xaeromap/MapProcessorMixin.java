package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWorldMapView;
import g_mungus.alpha_omega.compat.XaeroWraps;
import g_mungus.alpha_omega.wrap.Wrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xaero.map.MapProcessor;
import xaero.map.world.MapWorld;

/**
 * The world map's region storage is periodic, like the client's chunk storage: every region lookup is canonicalized,
 * so the map loops like the terrain and each place is mapped (and saved) once. Rendering places a region's texture
 * at the coordinates it was asked for, so all images of a region draw where they belong.
 */
@Mixin(MapProcessor.class)
abstract class MapProcessorMixin {

    @Shadow
    private MapWorld mapWorld;

    @Unique
    private int alpha_omega$canon(int region, int level) {
        Wrap wrap = XaeroWorldMapView.wrap(this.mapWorld);
        return XaeroWraps.canonRegion(region, XaeroWraps.regionPeriod(wrap), XaeroWraps.regionOrigin(wrap), level);
    }

    @ModifyVariable(method = {"getLeafMapRegion", "regionExists", "regionDetectionExists"}, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$canonRegionX(int regionX) {
        return this.alpha_omega$canon(regionX, 0);
    }

    @ModifyVariable(method = {"getLeafMapRegion", "regionExists", "regionDetectionExists"}, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int alpha_omega$canonRegionZ(int regionZ) {
        return this.alpha_omega$canon(regionZ, 0);
    }

    /** Zoomed-out levels loop too, where the canonical window is a whole number of their regions. */
    @ModifyVariable(method = "getLeveledRegion", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$canonLeveledX(int regionX, @Local(argsOnly = true, ordinal = 3) int level) {
        return this.alpha_omega$canon(regionX, level);
    }

    @ModifyVariable(method = "getLeveledRegion", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int alpha_omega$canonLeveledZ(int regionZ, @Local(argsOnly = true, ordinal = 3) int level) {
        return this.alpha_omega$canon(regionZ, level);
    }
}
