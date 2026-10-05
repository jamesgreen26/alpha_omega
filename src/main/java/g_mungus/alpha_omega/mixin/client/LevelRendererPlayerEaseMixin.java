package g_mungus.alpha_omega.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.alpha_omega.client.FaceCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the third-person views the player's own model turns with the camera while it eases over an edge
 * ({@link FaceCamera}): about its head, by the same rotation, so it stays upright in the view as the world turns
 * around it.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererPlayerEaseMixin {

    @Unique
    private boolean alpha_omega$turned;

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void alpha_omega$turnWithCamera(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
                                            MultiBufferSource buffers, CallbackInfo ci) {
        this.alpha_omega$turned = false;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isDetached() || camera.getEntity() != entity) return;
        float weight = FaceCamera.weight(partialTick);
        if (weight <= 0.0F) return;
        // The head, as renderEntity places the model relative to the camera.
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camX;
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getEyeHeight() - camY;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camZ;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(FaceCamera.rotation(weight));
        pose.translate(-x, -y, -z);
        this.alpha_omega$turned = true;
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void alpha_omega$turnBack(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, CallbackInfo ci) {
        if (this.alpha_omega$turned) pose.popPose();
        this.alpha_omega$turned = false;
    }
}
