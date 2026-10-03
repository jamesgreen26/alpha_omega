package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.waypoint.render.WaypointMapRenderer;
import xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderer;

/**
 * Waypoint renderers measure distances (for labels and range culling) from the waypoint itself rather than from
 * where it is drawn; they measure to its nearest image too.
 */
@Mixin({WaypointMapRenderer.class, WaypointWorldRenderer.class})
abstract class WaypointRenderersMixin {

    private static final String RENDER_ELEMENT = "renderElement(Lxaero/common/minimap/waypoints/Waypoint;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z";

    @WrapOperation(method = RENDER_ELEMENT, at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getX(D)I"))
    private int alpha_omega$nearestX(Waypoint waypoint, double divider, Operation<Integer> original, @Local(argsOnly = true) MinimapElementRenderInfo renderInfo) {
        return XaeroWraps.of(renderInfo.mapDimension).nearestBlock(original.call(waypoint, divider), Mth.floor(renderInfo.renderPos.x));
    }

    @WrapOperation(method = RENDER_ELEMENT, at = @At(value = "INVOKE", target = "Lxaero/common/minimap/waypoints/Waypoint;getZ(D)I"))
    private int alpha_omega$nearestZ(Waypoint waypoint, double divider, Operation<Integer> original, @Local(argsOnly = true) MinimapElementRenderInfo renderInfo) {
        return XaeroWraps.of(renderInfo.mapDimension).nearestBlock(original.call(waypoint, divider), Mth.floor(renderInfo.renderPos.z));
    }
}
