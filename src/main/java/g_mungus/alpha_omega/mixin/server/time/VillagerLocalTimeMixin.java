package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A freshly built villager brain starts on the villager's local clock (it has not ticked yet to know its owner). */
@Mixin(Villager.class)
abstract class VillagerLocalTimeMixin {

    @WrapOperation(method = "registerBrainGoals", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getDayTime()J"))
    private long alpha_omega$localClock(Level level, Operation<Long> original) {
        return LocalSky.local(level) ? LocalSky.localDayTime((Villager) (Object) this) : original.call(level);
    }
}
