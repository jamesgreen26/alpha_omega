package g_mungus.alpha_omega.mixin.server.time;

import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Skipping the night wakes everyone at the next morning of the face where most sleepers are, not vanilla's global
 * morning. The result still goes through NeoForge's sleep event.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelLocalTimeMixin {

    @ModifyArg(method = "tick", index = 1, at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/EventHooks;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"))
    private long alpha_omega$localMorning(long vanillaTime) {
        Long morning = LocalSky.morningAfterSleep((ServerLevel) (Object) this);
        return morning == null ? vanillaTime : morning;
    }
}
