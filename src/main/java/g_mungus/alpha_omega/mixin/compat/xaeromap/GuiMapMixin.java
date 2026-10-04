package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.compat.XaeroWorldMapView;
import g_mungus.alpha_omega.compat.XaeroWraps;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xaero.map.gui.GuiMap;

/**
 * Coordinates shown on the world map are canonical, like F3. The map itself keeps lifted coordinates so it scrolls
 * continuously; only the text changes.
 */
@Mixin(GuiMap.class)
abstract class GuiMapMixin {

    /** The cursor coordinates line at the top of the screen. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/graphics/MapRenderHelper;drawCenteredStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFFFFLcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
        index = 2)
    private String alpha_omega$canonicalCursor(String text) {
        Wrap wrap = XaeroWorldMapView.wrap();
        return wrap.enabled() ? XaeroWraps.canonicalCoordinates(text, wrap) : text;
    }

    /** The coordinates line in the right-click menu. */
    @WrapOperation(method = "getRightClickOptions", at = @At(value = "INVOKE",
        target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String alpha_omega$canonicalRightClick(String format, Object[] args, Operation<String> original) {
        Wrap wrap = XaeroWorldMapView.wrap();
        if (wrap.enabled() && format.startsWith("X: %1$d") && args.length >= 3 && args[0] instanceof Integer x && args[2] instanceof Integer z) {
            args = Arrays.copyOf(args, args.length);
            args[0] = wrap.canonBlock(x);
            args[2] = wrap.canonBlock(z);
        }
        return original.call(format, args);
    }
}
