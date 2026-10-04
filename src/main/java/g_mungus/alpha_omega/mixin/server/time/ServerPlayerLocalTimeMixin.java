package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Beds work when it is night at the bed. The check sits in NeoForge's lambda wrapping the vanilla bed logic. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerLocalTimeMixin {

    @WrapOperation(method = "lambda$startSleepInBed$13", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean alpha_omega$localDay(Level level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos bed) {
        return LocalSky.local(level) ? LocalSky.isDay(level, bed) : original.call(level);
    }
}
