package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.XaeroWorldMapView;
import g_mungus.alpha_omega.compat.XaeroWraps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.element.render.ElementReader;

/** Culling and hovering see map elements where they are drawn: at the image nearest the view or the cursor. */
@Mixin(ElementReader.class)
abstract class ElementReaderMixin {

    private static final String GET_RENDER_X = "Lxaero/map/element/render/ElementReader;getRenderX(Ljava/lang/Object;Ljava/lang/Object;F)D";
    private static final String GET_RENDER_Z = "Lxaero/map/element/render/ElementReader;getRenderZ(Ljava/lang/Object;Ljava/lang/Object;F)D";

    @WrapOperation(method = "isOnScreen", at = @At(value = "INVOKE", target = GET_RENDER_X))
    private double alpha_omega$onScreenX(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                         @Local(argsOnly = true, ordinal = 0) double cameraX, @Local(argsOnly = true, ordinal = 4) double dimDiv) {
        return XaeroWraps.nearest(XaeroWorldMapView.wrap(), original.call(reader, element, context, partialTicks), dimDiv, cameraX);
    }

    @WrapOperation(method = "isOnScreen", at = @At(value = "INVOKE", target = GET_RENDER_Z))
    private double alpha_omega$onScreenZ(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                         @Local(argsOnly = true, ordinal = 1) double cameraZ, @Local(argsOnly = true, ordinal = 4) double dimDiv) {
        return XaeroWraps.nearest(XaeroWorldMapView.wrap(), original.call(reader, element, context, partialTicks), dimDiv, cameraZ);
    }

    @WrapOperation(method = "isHoveredOnMap", at = @At(value = "INVOKE", target = GET_RENDER_X))
    private double alpha_omega$hoveredX(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                        @Local(argsOnly = true, ordinal = 0) double mouseX, @Local(argsOnly = true, ordinal = 4) double dimDiv) {
        return XaeroWraps.nearest(XaeroWorldMapView.wrap(), original.call(reader, element, context, partialTicks), dimDiv, mouseX);
    }

    @WrapOperation(method = "isHoveredOnMap", at = @At(value = "INVOKE", target = GET_RENDER_Z))
    private double alpha_omega$hoveredZ(ElementReader<?, ?, ?> reader, Object element, Object context, float partialTicks, Operation<Double> original,
                                        @Local(argsOnly = true, ordinal = 1) double mouseZ, @Local(argsOnly = true, ordinal = 4) double dimDiv) {
        return XaeroWraps.nearest(XaeroWorldMapView.wrap(), original.call(reader, element, context, partialTicks), dimDiv, mouseZ);
    }
}
