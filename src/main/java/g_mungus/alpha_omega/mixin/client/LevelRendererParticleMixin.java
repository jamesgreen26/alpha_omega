package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.ClientImages;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Particles are culled beyond 32 blocks of the camera before they are made: place them at the image nearest the
 * camera first (mod-compatibility §5.3). Every {@code Level.addParticle} on the client ends here.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererParticleMixin {

    @ModifyVariable(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$particleNearCameraX(double x) {
        return ClientImages.nearCameraX(x);
    }

    @ModifyVariable(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double alpha_omega$particleNearCameraZ(double z) {
        return ClientImages.nearCameraZ(z);
    }
}
