package g_mungus.alpha_omega.mixin.entities;

import g_mungus.alpha_omega.transfer.FrameAnchors;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An entity put into another frame by code other than transfers (a mod re-attaching it to a block entity with
 * {@code setPos}) is anchored there for a while (RS §4.3): {@link FrameAnchors}.
 */
@Mixin(Entity.class)
abstract class EntityAnchorMixin {

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"))
    private void alpha_omega$anchor(double x, double y, double z, CallbackInfo ci) {
        FrameAnchors.beforeSetPos((Entity) (Object) this, x, z);
    }
}
