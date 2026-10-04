package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.entity.ai.goal.OfferFlowerGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Iron golems offer flowers by local daylight. */
@Mixin(OfferFlowerGoal.class)
abstract class OfferFlowerGoalLocalTimeMixin {

    @Shadow
    @Final
    private IronGolem golem;

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean alpha_omega$localDay(Level level, Operation<Boolean> original) {
        return LocalSky.local(level) ? LocalSky.isDay(this.golem) : original.call(level);
    }
}
