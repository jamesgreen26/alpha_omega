package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.NeighbourEffects;
import g_mungus.alpha_omega.cube.CubeFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sounds played at a point on a neighbouring face are heard where that face really is ({@link NeighbourEffects}).
 * Sounds that follow an entity re-read its position every tick, so they stay where vanilla puts them.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void alpha_omega$moveToCameraFace(SoundInstance sound, CallbackInfo ci) {
        if (!(sound instanceof AbstractSoundInstance instance) || sound instanceof EntityBoundSoundInstance || sound.isRelative()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        CubeFace[] faces = NeighbourEffects.faces(level, sound.getX(), sound.getZ());
        if (faces == null) return;
        double[] p = NeighbourEffects.position(level, faces, sound.getX(), sound.getY(), sound.getZ());
        AbstractSoundInstanceAccessor access = (AbstractSoundInstanceAccessor) instance;
        access.alpha_omega$setX(p[0]);
        access.alpha_omega$setY(p[1]);
        access.alpha_omega$setZ(p[2]);
    }
}
