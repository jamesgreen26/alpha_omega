package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A remote entity that crossed an edge arrives thousands of blocks away in storage (design §5.3). Before it starts
 * moving toward its new position, its current one is re-expressed on the new face, so it glides on from where it
 * really was instead of streaking across storage.
 */
@Mixin({Entity.class, LivingEntity.class})
abstract class EntityLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"))
    private void alpha_omega$crossFaces(double x, double y, double z, float yRot, float xRot, int steps, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!entity.level().isClientSide()) return;
        CubeGeometry geometry = Cube.of(entity.level());
        if (geometry == null) return;
        CubeFace from = geometry.faceAt(entity.getX(), entity.getZ());
        CubeFace to = geometry.faceAt(x, z);
        if (from == null || to == null || from == to) return;
        double[] p = geometry.transform(from, to, entity.getX(), entity.getY(), entity.getZ());
        float[] rotation = FaceTransfer.rotateLook(FaceTransfer.mode(entity), from, to, entity.getYRot(), entity.getXRot());
        entity.setPos(p[0], p[1], p[2]);
        entity.setYRot(rotation[0]);
        entity.setXRot(rotation[1]);
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = rotation[0];
            living.yHeadRot = living.yHeadRotO = rotation[0];
        }
    }
}
