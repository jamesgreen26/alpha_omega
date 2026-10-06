package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.EntityBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges on {@code ServerLevel}: its entity storage learns its level (for the box query bridge). */
@Mixin(ServerLevel.class)
abstract class ServerLevelBridgeMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpha_omega$storageKnowsItsLevel(CallbackInfo ci) {
        var storage = ((PersistentEntitySectionManagerAccessor) ((ServerLevelEntitiesAccessor) this).alpha_omega$entityManager()).alpha_omega$sectionStorage();
        ((EntityBridge.Storage) storage).alpha_omega$setLevel((ServerLevel) (Object) this);
    }
}
