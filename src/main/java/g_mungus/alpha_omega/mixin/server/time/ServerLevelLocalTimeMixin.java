package g_mungus.alpha_omega.mixin.server.time;

import g_mungus.alpha_omega.sky.LocalSky;
import g_mungus.alpha_omega.sky.PlanetProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Skipping the night wakes everyone at the first daylight where the sleepers are: at their mean position on the planet
 * (a mean on the sphere, so neither the date line nor the poles are an edge). The result still goes through NeoForge's sleep event.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelLocalTimeMixin {

    @ModifyArg(method = "tick", index = 1, at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/EventHooks;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"))
    private long alpha_omega$localMorning(long vanillaTime) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LocalSky.local(level)) return vanillaTime;
        PlanetProjection.Position[] sleepers = level.players().stream().filter(ServerPlayer::isSleeping)
            .map(player -> LocalSky.position(level, player.getX(), player.getZ())).toArray(PlanetProjection.Position[]::new);
        if (sleepers.length == 0) return vanillaTime;
        PlanetProjection.Position mean = LocalSky.meanPosition(sleepers);
        if (mean == null) return vanillaTime;
        return level.getDayTime() + LocalSky.sleepTimeAddition(level.getDayTime(), mean);
    }
}
