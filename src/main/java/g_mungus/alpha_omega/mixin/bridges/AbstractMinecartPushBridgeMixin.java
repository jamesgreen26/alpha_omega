package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.EntityBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecarts push with their own maths; the same rule as {@link EntityPushBridgeMixin}. */
@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartPushBridgeMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$notAcrossFrames(Entity other, CallbackInfo ci) {
        if (EntityBridge.pushAcrossFrames((Entity) (Object) this, other)) ci.cancel();
    }
}
