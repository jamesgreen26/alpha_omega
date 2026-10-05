package g_mungus.alpha_omega.mixin.compat.sodium;

import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After Sodium draws a terrain layer of the camera's face, the same layer of the neighbouring faces is drawn. */
@Mixin(SodiumWorldRenderer.class)
abstract class SodiumWorldRendererMixin {

    @Shadow
    private RenderSectionManager renderSectionManager;

    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    private void alpha_omega$drawNeighbours(RenderType layer, ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
        SodiumNeighbours.draw(this.renderSectionManager, layer, matrices, x, y, z);
    }
}
