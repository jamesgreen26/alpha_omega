package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.gui.GuiAddWaypoint;

/** New waypoints are prefilled with canonical coordinates. */
@Mixin(GuiAddWaypoint.class)
abstract class GuiAddWaypointMixin {

    @ModifyReturnValue(method = {"getAutomaticX", "getAutomaticZ"}, at = @At("RETURN"))
    private int alpha_omega$canonical(int coordinate, @Local(argsOnly = true) double waypointDimScale) {
        return XaeroWraps.forScale(waypointDimScale).canonBlock(coordinate);
    }
}
