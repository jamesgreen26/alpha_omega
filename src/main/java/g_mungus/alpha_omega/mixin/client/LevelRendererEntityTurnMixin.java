package g_mungus.alpha_omega.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import g_mungus.alpha_omega.client.EntityTurns;
import g_mungus.alpha_omega.client.FaceCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Models turn over an edge instead of snapping upright. In the third-person views the player's own model turns with
 * the camera while it eases over an edge ({@link FaceCamera}): about its head, by the same rotation, so it stays
 * upright in the view as the world turns around it. Other players and mobs turn about their middle
 * ({@link EntityTurns}).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererEntityTurnMixin {

    @Unique
    private boolean alpha_omega$turned;

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void alpha_omega$turnWithCamera(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
                                            MultiBufferSource buffers, CallbackInfo ci) {
        this.alpha_omega$turned = false;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Quaternionf turn;
        double pivot;
        if (camera.getEntity() == entity) {
            float weight = FaceCamera.weight(partialTick);
            if (!camera.isDetached() || weight <= 0.0F) return;
            turn = FaceCamera.rotation(weight);
            pivot = entity.getEyeHeight();
        } else {
            turn = EntityTurns.rotation(entity, partialTick);
            if (turn == null) return;
            pivot = entity.getBbHeight() / 2.0;
        }
        // The pivot, as renderEntity places the model relative to the camera.
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - camX;
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + pivot - camY;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - camZ;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(turn);
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
