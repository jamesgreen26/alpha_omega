package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Phantoms come for each player when it is dark where that player is, not when it is dark at the prime meridian. */
@Mixin(PhantomSpawner.class)
abstract class PhantomSpawnerLocalTimeMixin {

    /** Skip the global "is it night" gate; the per-player check below replaces it. */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSkyDarken()I"))
    private int alpha_omega$noGlobalNight(ServerLevel level, Operation<Integer> original) {
        return LocalSky.local(level) ? 5 : original.call(level);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/entity/player/PlayerSpawnPhantomsEvent;shouldSpawnPhantoms(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean alpha_omega$localNight(PlayerSpawnPhantomsEvent event, ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        boolean spawn = original.call(event, level, pos);
        if (!spawn || !LocalSky.local(level) || event.getResult() == PlayerSpawnPhantomsEvent.Result.ALLOW) return spawn;
        return !level.dimensionType().hasSkyLight() || LocalSky.skyDarken(level, pos) >= 5;
    }
}
