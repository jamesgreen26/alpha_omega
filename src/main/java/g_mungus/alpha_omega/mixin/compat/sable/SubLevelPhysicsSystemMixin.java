package g_mungus.alpha_omega.mixin.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import g_mungus.alpha_omega.compat.sable.SubLevelTransfers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sub-levels cross edges once physics has moved them for the tick, before tracking sends their poses: the next
 * tick's terrain upload then covers where they are now.
 */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
abstract class SubLevelPhysicsSystemMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void alpha_omega$crossEdges(SubLevelContainer container, CallbackInfo ci) {
        SubLevelTransfers.afterPhysics((SubLevelPhysicsSystem) (Object) this);
    }
}
