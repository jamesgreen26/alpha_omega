package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.client.sky.ClientSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sky darkening (and with it the lightmap), cloud colour, star brightness and sky colour follow the local sun. Each
 * only uses {@code cos(2π·timeOfDay)}, so the equivalent time of day (the one that puts the sun at the same height)
 * gives the right value at any latitude.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelSkyMixin {

    @WrapOperation(method = {"getSkyDarken", "getCloudColor", "getStarBrightness"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$localTimeAtCamera(ClientLevel level, float partialTick, Operation<Float> original) {
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.atCamera(level).equivalentTimeOfDay();
    }

    @WrapOperation(method = "getSkyColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$localTimeAtPosition(ClientLevel level, float partialTick, Operation<Float> original,
                                                  @Local(argsOnly = true) Vec3 pos) {
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.at(level, pos).equivalentTimeOfDay();
    }
}
