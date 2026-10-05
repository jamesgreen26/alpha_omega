package g_mungus.alpha_omega.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import g_mungus.alpha_omega.client.ImageRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link ImageRenderer} into vanilla's own terrain pipeline: the area swap on crossing a seam, the clip of
 * vanilla's own sections to the tile and band, and the images' terrain layers. Not applied with Sodium, which replaces that pipeline and draws the neighbours itself
 * ({@code compat.sodium.SodiumNeighbours}).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererVanillaTerrainMixin {

    @Shadow
    private net.minecraft.client.multiplayer.ClientLevel level;
    @Shadow
    @Final
    private it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection> visibleSections;

    /** Home draws the tile and band only: the skirt and the void beyond are the images' to draw. */
    @Inject(method = "setupRender", at = @At("TAIL"))
    private void alpha_omega$hideDead(Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator, CallbackInfo ci) {
        ImageRenderer.hideDead(this.level, this.visibleSections);
    }

    @Inject(method = "setupRender", at = @At("HEAD"))
    private void alpha_omega$swapAreas(Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator, CallbackInfo ci) {
        ImageRenderer.beforeSetupRender((LevelRenderer) (Object) this, camera);
    }

    @Inject(method = "setupRender", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)V"))
    private void alpha_omega$awaitSwappedGraph(Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator, CallbackInfo ci) {
        ImageRenderer.afterOcclusionUpdate((LevelRenderer) (Object) this);
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private void alpha_omega$drawNeighbours(RenderType type, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        ImageRenderer.drawLayer(type, RenderSystem.getShader(), camX, camY, camZ, modelView);
    }
}
