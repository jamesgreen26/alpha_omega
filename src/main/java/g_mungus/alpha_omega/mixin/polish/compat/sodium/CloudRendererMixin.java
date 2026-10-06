package g_mungus.alpha_omega.mixin.polish.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import g_mungus.alpha_omega.client.CloudFrame;
import g_mungus.alpha_omega.orbifold.Motion;
import net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sodium's clouds through the client's {@link CloudFrame}, as {@code LevelRendererCloudMixin} does for vanilla's. */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 alpha_omega$cloudPosition(Camera camera, Operation<Vec3> original) {
        Vec3 pos = original.call(camera);
        Motion m = CloudFrame.current();
        return m.isIdentity() ? pos : new Vec3(m.pointX(pos.x), pos.y, m.pointZ(pos.z));
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void alpha_omega$cloudTurn(Camera camera, ClientLevel level, Matrix4f projection, PoseStack poseStack, float ticks, float tickDelta, CallbackInfo ci) {
        if (CloudFrame.current().turned()) poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
    }
}
