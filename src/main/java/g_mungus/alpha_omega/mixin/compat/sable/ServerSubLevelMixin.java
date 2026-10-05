package g_mungus.alpha_omega.mixin.compat.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import g_mungus.alpha_omega.compat.sable.SubLevelGravity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sub-levels' gravity rounds out with height ({@link SubLevelGravity}). */
@Mixin(value = ServerSubLevel.class, remap = false)
abstract class ServerSubLevelMixin {

    @Inject(method = "prePhysicsTick", at = @At("HEAD"))
    private void alpha_omega$roundGravity(SubLevelPhysicsSystem system, RigidBodyHandle handle, double timeStep, CallbackInfo ci) {
        SubLevelGravity.prePhysicsTick((ServerSubLevel) (Object) this, handle, timeStep);
    }
}
