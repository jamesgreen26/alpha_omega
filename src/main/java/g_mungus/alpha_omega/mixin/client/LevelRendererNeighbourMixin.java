package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.alpha_omega.client.NeighbourRenderer;
import g_mungus.alpha_omega.client.TransferStats;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@link NeighbourRenderer} into the level renderer: setup, entities, block entities, dirt. The hooks into vanilla's
 * own terrain pipeline, which Sodium replaces, are in {@link LevelRendererVanillaTerrainMixin}.
 */
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
    @Shadow
    @Final
    private it.unimi.dsi.fastutil.objects.ObjectArrayList<net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/renderer/LevelRenderer;compileSections(Lnet/minecraft/client/Camera;)V"))
    private void alpha_omega$setupNeighbours(DeltaTracker delta, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                             LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                             @Local Frustum frustum) {
        NeighbourRenderer.setup((LevelRenderer) (Object) this, camera, frustum, this.minecraft.options.getEffectiveRenderDistance());
        TransferStats.frame(this.visibleSections.size());
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

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void alpha_omega$neighbourGround(BlockGetter getter, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (this.level != null) NeighbourRenderer.blockChanged(this.level, pos);
    }

    @Inject(method = {"allChanged", "setLevel"}, at = @At("HEAD"))
    private void alpha_omega$resetNeighbours(CallbackInfo ci) {
        NeighbourRenderer.reset();
    }
}
