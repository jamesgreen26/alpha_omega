package g_mungus.alpha_omega.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.alpha_omega.client.SeamRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The world wrap seam is drawn along with the chunk borders (F3+G). */
@Mixin(ChunkBorderRenderer.class)
abstract class ChunkBorderRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void alpha_omega$renderSeam(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ, CallbackInfo ci) {
        SeamRenderer.render(poseStack, buffers, camX, camY, camZ);
    }
}
