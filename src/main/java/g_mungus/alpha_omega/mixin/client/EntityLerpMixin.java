package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.NeighbourEffects;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A remote entity that crossed a seam arrives far away in storage, moved by an element of {@code Γ}. Before it starts
 * moving toward its new position, its current one is re-expressed in the new frame, so it glides on from where it
 * really was instead of streaking across storage.
 */
@Mixin({Entity.class, LivingEntity.class})
abstract class EntityLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"))
    private void alpha_omega$crossSeams(double x, double y, double z, float yRot, float xRot, int steps, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!entity.level().isClientSide()) return;
        Motion g = NeighbourEffects.crossing(entity.level(), entity.position(), new Vec3(x, y, z));
        if (g == null) return;
        Vec3 p = Transform.of(g).position(entity.position());
        float[] rotation = FaceTransfer.rotateLook(g, entity.getYRot(), entity.getXRot());
        entity.setPos(p.x, p.y, p.z);
        entity.setYRot(rotation[0]);
        entity.setXRot(rotation[1]);
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = rotation[0];
            living.yHeadRot = living.yHeadRotO = rotation[0];
        }
    }
}
