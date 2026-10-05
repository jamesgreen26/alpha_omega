package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.NeighbourEffects;
import g_mungus.alpha_omega.orbifold.Motion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles started at an image's position appear where the camera sees it ({@link NeighbourEffects}). */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {

    @Shadow
    protected ClientLevel level;

    @Inject(method = "add", at = @At("HEAD"))
    private void alpha_omega$moveToCameraFace(Particle particle, CallbackInfo ci) {
        if (this.level == null) return;
        ParticleAccessor access = (ParticleAccessor) particle;
        Motion g = NeighbourEffects.toCamera(this.level, access.alpha_omega$x(), access.alpha_omega$z());
        if (g == null) return;
        double[] p = {g.pointX(access.alpha_omega$x()), access.alpha_omega$y(), g.pointZ(access.alpha_omega$z())};
        double[] v = {g.vectorX(access.alpha_omega$xd()), access.alpha_omega$yd(), g.vectorZ(access.alpha_omega$zd())};
        particle.setPos(p[0], p[1], p[2]);
        access.alpha_omega$setXo(p[0]);
        access.alpha_omega$setYo(p[1]);
        access.alpha_omega$setZo(p[2]);
        particle.setParticleSpeed(v[0], v[1], v[2]);
    }
}
