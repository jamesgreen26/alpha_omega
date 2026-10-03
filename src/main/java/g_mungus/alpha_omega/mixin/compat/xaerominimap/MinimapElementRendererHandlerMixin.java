package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.compat.XaeroWraps;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.MinimapElementRendererHandler;

/**
 * Minimap elements (waypoints, radar) are placed at their image nearest the view, on the minimap and in the world.
 * Coordinates here are already in the map's dimension.
 */
@Mixin(MinimapElementRendererHandler.class)
abstract class MinimapElementRendererHandlerMixin {

    @WrapOperation(method = "transformAndRenderForRenderer(Ljava/lang/Object;Lxaero/hud/minimap/element/render/MinimapElementRenderer;Ljava/lang/Object;IDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
        at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/element/render/MinimapElementRendererHandler;transformAndRenderForRenderer(Ljava/lang/Object;DDDLxaero/hud/minimap/element/render/MinimapElementRenderer;Ljava/lang/Object;IDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z"))
    private boolean alpha_omega$nearestImage(MinimapElementRendererHandler handler, Object element, double x, double y, double z,
                                             MinimapElementRenderer<?, ?> renderer, Object context, int elementIndex, double optionalDepth,
                                             MinimapElementRenderInfo renderInfo, GuiGraphics guiGraphics, MultiBufferSource.BufferSource bufferSource,
                                             Operation<Boolean> original) {
        Wrap wrap = XaeroWraps.of(renderInfo.mapDimension);
        if (wrap.enabled() && renderInfo.renderPos != null) {
            x = wrap.nearest(x, renderInfo.renderPos.x);
            z = wrap.nearest(z, renderInfo.renderPos.z);
        }
        return original.call(handler, element, x, y, z, renderer, context, elementIndex, optionalDepth, renderInfo, guiGraphics, bufferSource);
    }
}
