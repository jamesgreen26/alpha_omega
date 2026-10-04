package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Foxes sleep through the local day. */
@Mixin(targets = "net.minecraft.world.entity.animal.Fox$SleepGoal")
abstract class FoxSleepGoalLocalTimeMixin {

    @Shadow
    @Final
    Fox this$0;

    @WrapOperation(method = "canSleep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean alpha_omega$localDay(Level level, Operation<Boolean> original) {
        return LocalSky.local(level) ? LocalSky.isDay(this.this$0) : original.call(level);
    }
}
