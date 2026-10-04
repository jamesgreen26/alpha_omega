package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.client.sky.ClientSky;
import g_mungus.alpha_omega.client.sky.SkyState;
import g_mungus.alpha_omega.client.sky.SpaceFade;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fog brightness and the sunrise tint in the fog follow the local sun: the tint is strongest looking toward the sun's
 * azimuth rather than due east or west.
 *
 * <p>With the atmosphere on, the fog takes the colour of the horizon in the direction of view, blended toward the sky
 * at short render distances as vanilla does. Rain, thunder, void darkness, mob effects, night vision and NeoForge's
 * fog colour event all still apply afterwards; underwater, lava and powder snow fog are untouched.
 *
 * <p>Above the build height the fog fades to black with the sky ({@link SpaceFade}), before NeoForge's event.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererSkyMixin {

    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

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

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private static float[] alpha_omega$noSunriseTint(DimensionSpecialEffects effects, float timeOfDay, float partialTick, Operation<float[]> original,
                                                     @Local(argsOnly = true) ClientLevel level) {
        return SkyState.active(level) ? null : original.call(effects, timeOfDay, partialTick);
    }

    /** Just before rain darkens the fog: replace vanilla's biome fog with the atmosphere's horizon. */
    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private static void alpha_omega$atmosphereFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld,
                                                  CallbackInfo ci) {
        if (!SkyState.active(level)) return;
        Vector3f look = camera.getLookVector();
        double[] horizon = SkyState.fogColor(level, look.x, look.z);
        Vec3 sky = level.getSkyColor(camera.getPosition(), partialTick);
        float towardSky = 1.0F - (float) Math.pow(0.25F + 0.75F * renderDistance / 32.0F, 0.25);
        fogRed = (float) (horizon[0] + (sky.x - horizon[0]) * towardSky);
        fogGreen = (float) (horizon[1] + (sky.y - horizon[1]) * towardSky);
        fogBlue = (float) (horizon[2] + (sky.z - horizon[2]) * towardSky);
    }

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/client/ClientHooks;getFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFFFF)Lorg/joml/Vector3f;"))
    private static Vector3f alpha_omega$spaceFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld,
                                                 float red, float green, float blue, Operation<Vector3f> original) {
        if (camera.getFluidInCamera() == FogType.NONE) {
            float density = (float) SpaceFade.density(level, camera.getPosition().y);
            red *= density;
            green *= density;
            blue *= density;
        }
        return original.call(camera, partialTick, level, renderDistance, darkenWorld, red, green, blue);
    }
}
