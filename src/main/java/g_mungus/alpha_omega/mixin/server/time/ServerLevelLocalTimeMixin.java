package g_mungus.alpha_omega.mixin.server.time;

import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Skipping the night wakes everyone at the first daylight where the sleepers are: at their mean longitude (a circular
 * mean, so the date line is no edge) and mean latitude. The result still goes through NeoForge's sleep event.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelLocalTimeMixin {

    @ModifyArg(method = "tick", index = 1, at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/event/EventHooks;onSleepFinished(Lnet/minecraft/server/level/ServerLevel;JJ)J"))
    private long alpha_omega$localMorning(long vanillaTime) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LocalSky.local(level)) return vanillaTime;
        double[] longitudes = level.players().stream().filter(ServerPlayer::isSleeping)
            .mapToDouble(player -> LocalSky.longitude(level, player.getX())).toArray();
        if (longitudes.length == 0) return vanillaTime;
        double longitude = LocalSky.meanLongitude(longitudes);
        if (Double.isNaN(longitude)) return vanillaTime;
        double latitude = level.players().stream().filter(ServerPlayer::isSleeping)
            .mapToDouble(player -> LocalSky.latitude(level, player.getZ())).average().orElse(0.0);
        return level.getDayTime() + LocalSky.sleepTimeAddition(level.getDayTime(), longitude, latitude);
    }
}
