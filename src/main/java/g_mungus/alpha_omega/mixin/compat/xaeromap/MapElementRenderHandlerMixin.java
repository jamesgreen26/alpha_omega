package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.element.MapElementRenderHandler;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;

/** Map elements (waypoints, markers) draw at their image nearest the view, so they repeat with the terrain. */
@Mixin(MapElementRenderHandler.class)
abstract class MapElementRenderHandlerMixin {

    @WrapOperation(method = "transformAndRenderElement", at = @At(value = "INVOKE",
        target = "Lxaero/map/element/render/ElementReader;getRenderX(Ljava/lang/Object;Ljava/lang/Object;F)D"))
    private double alpha_omega$nearestX(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                        @Local(argsOnly = true) ElementRenderInfo renderInfo, @Local(argsOnly = true, ordinal = 1) double dimDiv) {
        double x = original.call(reader, element, context, partialTicks);
        return XaeroWraps.nearest(XaeroWraps.of(renderInfo.mapDimension), x, dimDiv, renderInfo.renderPos.x);
    }

    @WrapOperation(method = "transformAndRenderElement", at = @At(value = "INVOKE",
        target = "Lxaero/map/element/render/ElementReader;getRenderZ(Ljava/lang/Object;Ljava/lang/Object;F)D"))
    private double alpha_omega$nearestZ(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                        @Local(argsOnly = true) ElementRenderInfo renderInfo, @Local(argsOnly = true, ordinal = 1) double dimDiv) {
        double z = original.call(reader, element, context, partialTicks);
        return XaeroWraps.nearest(XaeroWraps.of(renderInfo.mapDimension), z, dimDiv, renderInfo.renderPos.z);
    }
}
