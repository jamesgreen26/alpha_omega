package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.alpha_omega.client.NeighbourRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks {@link NeighbourRenderer} into the level renderer: setup, terrain layers, entities, block entities, dirt. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererNeighbourMixin {

    @Shadow
    private ClientLevel level;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/Camera;)V"))
    private void alpha_omega$setupNeighbours(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                             LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                             @Local Frustum frustum) {
        NeighbourRenderer.setup((LevelRenderer) (Object) this, camera, frustum, this.minecraft.options.getEffectiveRenderDistance());
    }

    @Inject(method = "setupRender", at = @At("HEAD"))
    private void alpha_omega$swapAreas(Camera camera, Frustum frustum, boolean capturedFrustum, boolean spectator, CallbackInfo ci) {
        NeighbourRenderer.beforeSetupRender((LevelRenderer) (Object) this, camera);
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private void alpha_omega$drawNeighbours(RenderType type, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        NeighbourRenderer.drawLayer(type, RenderSystem.getShader(), camX, camY, camZ, modelView);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void alpha_omega$neighbourEntities(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                               LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                               @Local Frustum frustum, @Local PoseStack pose, @Local MultiBufferSource.BufferSource buffers) {
        NeighbourRenderer.renderEntities((LevelRenderer) (Object) this, camera, frustum, delta, pose, buffers);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void alpha_omega$neighbourBlockEntities(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                                    LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                                    @Local PoseStack pose, @Local MultiBufferSource.BufferSource buffers) {
        NeighbourRenderer.renderBlockEntities((LevelRenderer) (Object) this, camera, delta.getGameTimeDeltaPartialTick(false), pose, buffers,
            this.blockEntityRenderDispatcher);
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$neighbourDirt(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
        if (this.level != null && NeighbourRenderer.setDirty(this.level, x, y, z, playerChanged)) ci.cancel();
    }

    @Inject(method = {"allChanged", "setLevel"}, at = @At("HEAD"))
    private void alpha_omega$resetNeighbours(CallbackInfo ci) {
        NeighbourRenderer.reset();
    }
}
