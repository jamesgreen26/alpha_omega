package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.DestinationHandler;

/** A destination waypoint is reached at any of its images. */
@Mixin(DestinationHandler.class)
abstract class DestinationHandlerMixin {

    @Shadow
    private Entity renderEntity;

    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getX(D)I"))
    private int alpha_omega$nearestX(Waypoint waypoint, double divider, Operation<Integer> original) {
        return Wrap.of(this.renderEntity.level()).nearestBlock(original.call(waypoint, divider), this.renderEntity.getBlockX());
    }

    @WrapOperation(method = "handle", at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getZ(D)I"))
    private int alpha_omega$nearestZ(Waypoint waypoint, double divider, Operation<Integer> original) {
        return Wrap.of(this.renderEntity.level()).nearestBlock(original.call(waypoint, divider), this.renderEntity.getBlockZ());
    }
}
