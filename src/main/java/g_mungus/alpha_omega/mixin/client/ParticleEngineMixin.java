package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.NeighbourEffects;
import g_mungus.alpha_omega.cube.CubeFace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles started on a neighbouring face appear where that face really is ({@link NeighbourEffects}). */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {

    @Shadow
    protected ClientLevel level;

    @Inject(method = "add", at = @At("HEAD"))
    private void alpha_omega$moveToCameraFace(Particle particle, CallbackInfo ci) {
        if (this.level == null) return;
        ParticleAccessor access = (ParticleAccessor) particle;
        CubeFace[] faces = NeighbourEffects.faces(this.level, access.alpha_omega$x(), access.alpha_omega$z());
        if (faces == null) return;
        double[] p = NeighbourEffects.position(this.level, faces, access.alpha_omega$x(), access.alpha_omega$y(), access.alpha_omega$z());
        double[] v = NeighbourEffects.direction(faces, access.alpha_omega$xd(), access.alpha_omega$yd(), access.alpha_omega$zd());
        particle.setPos(p[0], p[1], p[2]);
        access.alpha_omega$setXo(p[0]);
        access.alpha_omega$setYo(p[1]);
        access.alpha_omega$setZo(p[2]);
        particle.setParticleSpeed(v[0], v[1], v[2]);
    }
}
