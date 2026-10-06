package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.transfer.FrameTransfers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Root entities that have crossed an edge transfer once they and their passengers have ticked. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("TAIL"))
    private void alpha_omega$transfer(Entity entity, CallbackInfo ci) {
        FrameTransfers.afterTick((ServerLevel) (Object) this, entity);
    }
}
