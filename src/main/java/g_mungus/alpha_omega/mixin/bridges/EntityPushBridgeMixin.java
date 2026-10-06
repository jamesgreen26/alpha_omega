package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.EntityBridge;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entities found through an image do not push each other along their storage offset ({@link EntityBridge#pushAcrossFrames}). */
@Mixin(Entity.class)
abstract class EntityPushBridgeMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$notAcrossFrames(Entity other, CallbackInfo ci) {
        if (EntityBridge.pushAcrossFrames((Entity) (Object) this, other)) ci.cancel();
    }
}
