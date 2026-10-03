package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.util.WaypointUtils;

/** Distances in the waypoint list are to the nearest image. */
@Mixin(WaypointUtils.class)
abstract class WaypointUtilsMixin {

    private static final String GET_DISTANCE_TEXT = "getDistanceText(Lxaero/common/minimap/waypoints/Waypoint;DDDDD)Ljava/lang/String;";

    @WrapOperation(method = GET_DISTANCE_TEXT, at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getX(D)I"))
    private static int alpha_omega$nearestX(Waypoint waypoint, double divider, Operation<Integer> original,
                                            @Local(argsOnly = true, ordinal = 0) double fromX, @Local(argsOnly = true, ordinal = 3) double scale) {
        return XaeroWraps.forScale(scale).nearestBlock(original.call(waypoint, divider), Mth.floor(fromX));
    }

    @WrapOperation(method = GET_DISTANCE_TEXT, at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getZ(D)I"))
    private static int alpha_omega$nearestZ(Waypoint waypoint, double divider, Operation<Integer> original,
                                            @Local(argsOnly = true, ordinal = 2) double fromZ, @Local(argsOnly = true, ordinal = 3) double scale) {
        return XaeroWraps.forScale(scale).nearestBlock(original.call(waypoint, divider), Mth.floor(fromZ));
    }
}
