package g_mungus.alpha_omega.mixin.server.time;

import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schedules (villagers working, meeting and sleeping) run on the local clock. Every caller of
 * {@code updateActivityFromSchedule} passes the global day time; the brain remembers whose it is, from its last tick,
 * and shifts the time by that entity's time zone.
 */
@Mixin(Brain.class)
abstract class BrainLocalTimeMixin {

    @Unique
    private LivingEntity alpha_omega$owner;

    @Inject(method = "tick", at = @At("HEAD"))
    private void alpha_omega$rememberOwner(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        this.alpha_omega$owner = entity;
    }

    @ModifyVariable(method = "updateActivityFromSchedule", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private long alpha_omega$localClock(long dayTime) {
        LivingEntity owner = this.alpha_omega$owner;
        if (owner == null || !LocalSky.local(owner.level())) return dayTime;
        return dayTime + LocalSky.localDayTime(owner) - owner.level().getDayTime();
    }
}
