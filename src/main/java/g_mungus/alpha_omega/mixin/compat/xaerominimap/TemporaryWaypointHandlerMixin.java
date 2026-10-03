package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xaero.hud.minimap.waypoint.TemporaryWaypointHandler;

/** Temporary waypoints are saved at canonical coordinates. */
@Mixin(TemporaryWaypointHandler.class)
abstract class TemporaryWaypointHandlerMixin {

    private static final String CREATE = "createTemporaryWaypoint(Lxaero/hud/minimap/world/MinimapWorld;IIIZD)V";

    @ModifyVariable(method = CREATE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$canonX(int x, @Local(argsOnly = true) double dimScale) {
        return XaeroWraps.forScale(dimScale).canonBlock(x);
    }

    @ModifyVariable(method = CREATE, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int alpha_omega$canonZ(int z, @Local(argsOnly = true) double dimScale) {
        return XaeroWraps.forScale(dimScale).canonBlock(z);
    }
}
