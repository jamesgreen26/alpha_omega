package g_mungus.alpha_omega.mixin.polish.client;

import g_mungus.alpha_omega.client.CloudFrame;
import g_mungus.alpha_omega.orbifold.Motion;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Vanilla's clouds are drawn through the client's {@link CloudFrame}: the camera's position goes to cloud space by its
 * map, and a half-turned map turns the layer 180° about the camera. Left out when Sodium, which draws its own clouds,
 * is installed ({@code compat.sodium.CloudRendererMixin} does the same there).
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCloudMixin {

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$cloudX(double camX) {
        return CloudFrame.current().pointX(camX);
    }

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double alpha_omega$cloudZ(double camZ) {
        return CloudFrame.current().pointZ(camZ);
    }

    @ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Matrix4f alpha_omega$cloudTurn(Matrix4f frustum) {
        Motion m = CloudFrame.current();
        return m.turned() ? new Matrix4f(frustum).rotateY((float) Math.PI) : frustum;
    }
}
