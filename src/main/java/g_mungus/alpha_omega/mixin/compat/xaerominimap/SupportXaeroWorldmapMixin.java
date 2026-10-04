package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWorldMapView;
import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.mods.SupportXaeroWorldmap;
import xaero.map.region.MapTileChunk;

/**
 * The minimap drawn from the world map's data places each tile chunk by its own coordinates, which are canonical.
 * It is drawn at its image nearest the player instead, so the minimap loops like the terrain on every lap.
 * Only applies when the world map is installed: the target class is loaded only then.
 */
@Mixin(SupportXaeroWorldmap.class)
abstract class SupportXaeroWorldmapMixin {

    @WrapOperation(method = "renderChunks", at = @At(value = "INVOKE", target = "Lxaero/map/region/MapTileChunk;getX()I"))
    private int alpha_omega$nearestX(MapTileChunk chunk, Operation<Integer> original, @Local(argsOnly = true, ordinal = 8) int chunkX) {
        return XaeroWraps.nearestTileChunk(XaeroWorldMapView.wrap(), original.call(chunk), chunkX);
    }

    @WrapOperation(method = "renderChunks", at = @At(value = "INVOKE", target = "Lxaero/map/region/MapTileChunk;getZ()I"))
    private int alpha_omega$nearestZ(MapTileChunk chunk, Operation<Integer> original, @Local(argsOnly = true, ordinal = 9) int chunkZ) {
        return XaeroWraps.nearestTileChunk(XaeroWorldMapView.wrap(), original.call(chunk), chunkZ);
    }
}
