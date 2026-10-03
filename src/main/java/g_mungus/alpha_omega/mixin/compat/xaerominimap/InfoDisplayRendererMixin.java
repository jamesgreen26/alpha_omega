package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.hud.minimap.info.BuiltInInfoDisplays;
import xaero.hud.minimap.info.InfoDisplay;
import xaero.hud.minimap.info.render.InfoDisplayRenderer;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.hud.minimap.module.MinimapSession;

/**
 * The minimap's coordinate lines are canonical, like F3. Other lines (light, biome, ...) keep the real position,
 * which is where the client's level holds that information.
 */
@Mixin(InfoDisplayRenderer.class)
abstract class InfoDisplayRendererMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/hud/minimap/info/render/compile/InfoDisplayCompiler;compile(Lxaero/hud/minimap/info/InfoDisplay;Lxaero/hud/minimap/module/MinimapSession;ILnet/minecraft/core/BlockPos;)Ljava/util/List;"))
    private List<Component> alpha_omega$canonicalCoordinates(InfoDisplayCompiler compiler, InfoDisplay<?> infoDisplay, MinimapSession session, int size,
                                                             BlockPos playerPos, Operation<List<Component>> original) {
        if (infoDisplay == BuiltInInfoDisplays.COORDINATES || infoDisplay == BuiltInInfoDisplays.OVERWORLD_COORDINATES
            || infoDisplay == BuiltInInfoDisplays.CHUNK_COORDINATES) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) playerPos = Wrap.of(minecraft.level).canon(playerPos);
        }
        return original.call(compiler, infoDisplay, session, size, playerPos);
    }
}
