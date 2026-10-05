package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.FaceCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eases the view over an edge ({@link FaceCamera}): an extra rotation and shift that fade out after a crossing. In
 * the third-person views the camera swings about the player's head with that rotation, rather than turning in place.
 */
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
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void alpha_omega$easeOverEdges(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        float weight = FaceCamera.weight(partialTick);
        if (weight <= 0.0F) return;
        Quaternionf ease = FaceCamera.rotation(weight);
        this.rotation.premul(ease);
        this.forwards.set(0.0F, 0.0F, -1.0F).rotate(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
        this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
        Vec3 position = this.position;
        if (detached) {
            // Vanilla backed the camera off from the head along the view: turn that offset with the view.
            Vec3 head = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                Mth.lerp(partialTick, entity.yo, entity.getY()) + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight),
                Mth.lerp(partialTick, entity.zo, entity.getZ()));
            Vector3d offset = ease.transform(new Vector3d(position.x - head.x, position.y - head.y, position.z - head.z));
            position = head.add(offset.x, offset.y, offset.z);
        }
        this.setPosition(position.add(FaceCamera.shift(weight)));
    }
}
