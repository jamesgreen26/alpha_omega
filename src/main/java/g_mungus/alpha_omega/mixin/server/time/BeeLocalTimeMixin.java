package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bees head home at local nightfall. */
@Mixin(Bee.class)
abstract class BeeLocalTimeMixin {

    @WrapOperation(method = "wantsToEnterHive", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isNight()Z"))
    private boolean alpha_omega$localNight(Level level, Operation<Boolean> original) {
        return LocalSky.local(level) ? LocalSky.isNight((Bee) (Object) this) : original.call(level);
    }
}
