package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.ClientImages;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every particle, including ones other mods construct themselves, joins the engine here: move it to the image of
 * its position nearest the camera (mod-compatibility §5.3). Whole laps only, so its motion is unchanged.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {

    @Inject(method = "add", at = @At("HEAD"))
    private void alpha_omega$addNearCamera(Particle particle, CallbackInfo ci) {
        Vec3 pos = particle.getPos();
        double dx = ClientImages.nearCameraX(pos.x) - pos.x;
        double dz = ClientImages.nearCameraZ(pos.z) - pos.z;
        if (dx == 0 && dz == 0) return;
        ParticleAccessor accessor = (ParticleAccessor) particle;
        particle.setPos(pos.x + dx, pos.y, pos.z + dz);
        accessor.alpha_omega$setXo(accessor.alpha_omega$getXo() + dx);
        accessor.alpha_omega$setZo(accessor.alpha_omega$getZo() + dz);
    }
}
