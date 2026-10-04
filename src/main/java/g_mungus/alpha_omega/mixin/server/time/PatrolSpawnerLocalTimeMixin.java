package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Patrols spawn by day near the chosen player, judged by the sun where that player is. */
@Mixin(PatrolSpawner.class)
abstract class PatrolSpawnerLocalTimeMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isDay()Z"))
    private boolean alpha_omega$noGlobalDay(ServerLevel level, Operation<Boolean> original) {
        return LocalSky.local(level) || original.call(level);
    }

    /** Reusing the "too close to a village" exit: a player at local night gets no patrol either. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isCloseToVillage(Lnet/minecraft/core/BlockPos;I)Z"))
    private boolean alpha_omega$localDay(ServerLevel level, BlockPos pos, int distance, Operation<Boolean> original) {
        return original.call(level, pos, distance) || LocalSky.local(level) && !LocalSky.isDay(level, pos);
    }
}
