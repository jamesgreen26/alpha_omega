package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.client.sky.ClientSky;
import g_mungus.alpha_omega.client.sky.SkyState;
import g_mungus.alpha_omega.client.sky.SpaceFade;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sky darkening (and with it the lightmap), cloud colour, star brightness and sky colour follow the local sun. Each
 * only uses {@code cos(2π·timeOfDay)}, so the equivalent time of day (the one that puts the sun at the same height)
 * gives the right value at any latitude.
 *
 * <p>With the atmosphere on, the sky and cloud colours come from it instead: vanilla's day-night dimming is held at
 * noon, the biome's sky colour is replaced by the atmosphere's, and clouds are tinted by the light reaching them.
 * Vanilla's rain, thunder and lightning adjustments still apply on top.
 *
 * <p>Above the build height the sky colour fades to black and the stars to full brightness ({@link SpaceFade}).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelSkyMixin {

    @WrapOperation(method = {"getSkyDarken", "getStarBrightness"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$localTimeAtCamera(ClientLevel level, float partialTick, Operation<Float> original) {
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.atCamera(level).equivalentTimeOfDay();
    }

    @WrapOperation(method = "getCloudColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$cloudTime(ClientLevel level, float partialTick, Operation<Float> original) {
        if (SkyState.active(level)) return 0.0F;
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.atCamera(level).equivalentTimeOfDay();
    }

    @WrapOperation(method = "getSkyColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"))
    private float alpha_omega$skyTime(ClientLevel level, float partialTick, Operation<Float> original, @Local(argsOnly = true) Vec3 pos) {
        if (SkyState.active(level)) return 0.0F;
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.at(level, pos).equivalentTimeOfDay();
    }

    /** The biome's sky colour, sampled around the camera: the atmosphere's dome colour instead. */
    @ModifyExpressionValue(method = "getSkyColor", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 alpha_omega$atmosphereSky(Vec3 biomeColor) {
        ClientLevel level = (ClientLevel) (Object) this;
        return SkyState.active(level) ? SkyState.skyColor(level) : biomeColor;
    }

    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 alpha_omega$spaceSky(Vec3 color, @Local(argsOnly = true) Vec3 pos) {
        return color.scale(SpaceFade.density((ClientLevel) (Object) this, pos.y));
    }

    @ModifyReturnValue(method = "getCloudColor", at = @At("RETURN"))
    private Vec3 alpha_omega$atmosphereClouds(Vec3 color) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!SkyState.active(level)) return color;
        double[] tint = SkyState.cloudTint(level);
        return new Vec3(color.x * tint[0], color.y * tint[1], color.z * tint[2]);
    }

    @ModifyReturnValue(method = "getStarBrightness", at = @At("RETURN"))
    private float alpha_omega$atmosphereStars(float brightness) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (SkyState.active(level)) brightness = SkyState.starBrightness(level);
        float density = (float) SpaceFade.atCamera(level);
        return 1.0F + (brightness - 1.0F) * density;
    }
}
