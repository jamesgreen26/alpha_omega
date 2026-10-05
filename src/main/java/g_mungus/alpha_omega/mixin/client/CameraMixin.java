package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.FaceCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eases the view over an edge ({@link FaceCamera}): an extra rotation and shift that fade out after a crossing. */
@Mixin(Camera.class)
abstract class CameraMixin {

    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void alpha_omega$easeOverEdges(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        float weight = FaceCamera.weight(partialTick);
        if (weight <= 0.0F) return;
        this.rotation.premul(FaceCamera.rotation(weight));
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
        this.setPosition(this.position.add(FaceCamera.shift(weight)));
    }
}
