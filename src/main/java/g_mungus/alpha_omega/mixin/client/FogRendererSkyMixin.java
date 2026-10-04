package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.client.sky.ClientSky;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fog brightness and the sunrise tint in the fog follow the local sun: the tint is strongest looking toward the sun's
 * azimuth rather than due east or west.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererSkyMixin {

    @WrapOperation(method = "setupColor",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private static float alpha_omega$localTime(ClientLevel level, float partialTick, Operation<Float> original,
                                               @Local(argsOnly = true) Camera camera) {
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.at(level, camera.getPosition()).equivalentTimeOfDay();
    }

    @ModifyExpressionValue(method = "setupColor", at = @At(value = "NEW", target = "org/joml/Vector3f"))
    private static Vector3f alpha_omega$sunriseDirection(Vector3f direction, @Local(argsOnly = true) Camera camera,
                                                         @Local(argsOnly = true) ClientLevel level) {
        if (!ClientSky.applies(level)) return direction;
        LocalSky.Sample sun = ClientSky.at(level, camera.getPosition());
        float yaw = ClientSky.sunYaw(sun);
        return direction.set((float) Math.cos(yaw), 0.0F, (float) -Math.sin(yaw));
    }
}
