package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import g_mungus.alpha_omega.client.sky.AtmosphereRenderer;
import g_mungus.alpha_omega.client.sky.ClientSky;
import g_mungus.alpha_omega.client.sky.SkyState;
import g_mungus.alpha_omega.client.sky.SpaceFade;
import g_mungus.alpha_omega.client.sky.StarField;
import g_mungus.alpha_omega.sky.LocalSky;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The sky turns about the face's celestial pole: vanilla's {@code XP(timeOfDay·360°)} becomes the face's celestial
 * rotation ({@code CubeSun}), so the sun, moon and stars (all drawn in that frame) rise and set at the face's angle. The sunrise
 * glow points at the sun's azimuth instead of due east or west, unless the atmosphere is on: it draws its own.
 *
 * <p>With the atmosphere on (and the camera in air), the flat-coloured sky dome is replaced by the atmosphere drawn per
 * pixel. The sun, moon, stars and the dark lower hemisphere are still vanilla's, drawn over it.
 *
 * <p>The stars themselves are Genesis's ({@link StarField}), drawn in place of vanilla's star buffer.
 *
 * <p>Above the build height the sky thins out ({@link SpaceFade}): the sunrise glow fades, and rain neither hides the
 * sun, moon and stars nor falls around the camera.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererSkyMixin {

    @Shadow
    @Nullable
    private ClientLevel level;

    /** The celestial rotation, {@code XP(getTimeOfDay·360)}: the fifth rotation in renderSky. */
    @ModifyExpressionValue(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 4))
    private Quaternionf alpha_omega$celestialRotation(Quaternionf rotation) {
        if (!ClientSky.applies(this.level)) return rotation;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return LocalSky.celestialRotation(this.level, camera.x, camera.z);
    }

    /** The sunrise colour only depends on the sun's height. */
    @WrapOperation(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F", ordinal = 0))
    private float alpha_omega$sunriseTime(ClientLevel level, float partialTick, Operation<Float> original) {
        if (!ClientSky.applies(level)) return original.call(level, partialTick);
        return (float) ClientSky.atCamera(level).equivalentTimeOfDay();
    }

    /** Always pick vanilla's eastern (+X) sunrise glow; the first rotation then turns it toward the sun. */
    @ModifyExpressionValue(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSunAngle(F)F"))
    private float alpha_omega$sunriseSide(float angle) {
        if (!ClientSky.applies(this.level)) return angle;
        return (float) (-Math.PI / 2.0);
    }

    /** The sunrise glow's first rotation, {@code XP(90)}: turn the glow about +Y to the sun's azimuth first. */
    @ModifyExpressionValue(method = "renderSky",
        at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 0))
    private Quaternionf alpha_omega$sunriseAzimuth(Quaternionf rotation) {
        if (!ClientSky.applies(this.level)) return rotation;
        return new Quaternionf().rotateY(ClientSky.sunYaw(ClientSky.atCamera(this.level))).mul(rotation);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private float[] alpha_omega$noSunriseGlow(DimensionSpecialEffects effects, float timeOfDay, float partialTick, Operation<float[]> original) {
        if (SkyState.active(this.level)) return null;
        float[] color = original.call(effects, timeOfDay, partialTick);
        if (color == null) return null;
        color = color.clone();
        color[3] *= (float) SpaceFade.atCamera(this.level);
        return color;
    }

    @ModifyExpressionValue(method = {"renderSky", "renderSnowAndRain"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float alpha_omega$spaceRain(float rain) {
        return rain * (float) SpaceFade.atCamera(this.level);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
        ordinal = 0))
    private void alpha_omega$atmosphereDome(VertexBuffer dome, Matrix4f modelView, Matrix4f projection, ShaderInstance shader, Operation<Void> original) {
        boolean inAir = Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE;
        if (!inAir || !SkyState.active(this.level) || !AtmosphereRenderer.drawSky(this.level, modelView, projection)) {
            original.call(dome, modelView, projection, shader);
            return;
        }
        // Vanilla unbinds the dome's buffer next; leave it bound as vanilla would.
        dome.bind();
    }

    /** The second buffer drawn in renderSky is vanilla's stars; draw ours with the same transform and colour. */
    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
        ordinal = 1))
    private void alpha_omega$stars(VertexBuffer stars, Matrix4f modelView, Matrix4f projection, ShaderInstance shader, Operation<Void> original) {
        StarField.draw(modelView, projection, shader);
    }
}
