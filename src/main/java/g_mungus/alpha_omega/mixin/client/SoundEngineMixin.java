package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Listener;
import g_mungus.alpha_omega.client.ClientImages;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Positional sounds play at the image of their position nearest the listener (mod-compatibility §5.3), so a sound
 * placed at a canonical block entity position is still heard next to the player. The instance itself is moved, so
 * subtitles and other sound listeners see the same position. Moving sounds are moved again after every tick.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {

    @Shadow
    @Final
    private Listener listener;

    /** After NeoForge's sound event, which may replace the instance. */
    @ModifyVariable(method = "play", argsOnly = true,
        at = @At(value = "INVOKE_ASSIGN", target = "Lnet/neoforged/neoforge/client/ClientHooks;playSound(Lnet/minecraft/client/sounds/SoundEngine;Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/resources/sounds/SoundInstance;"))
    private SoundInstance alpha_omega$playNearListener(SoundInstance sound) {
        this.alpha_omega$moveNearListener(sound);
        return sound;
    }

    @WrapOperation(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/TickableSoundInstance;tick()V"))
    private void alpha_omega$keepNearListener(TickableSoundInstance sound, Operation<Void> original) {
        original.call(sound);
        this.alpha_omega$moveNearListener(sound);
    }

    @Unique
    private void alpha_omega$moveNearListener(SoundInstance sound) {
        if (sound == null || sound.isRelative() || !(sound instanceof AbstractSoundInstance)) return;
        Wrap wrap = ClientImages.wrap();
        if (!wrap.enabled()) return;
        Vec3 at = this.listener.getTransform().position();
        double x = wrap.nearest(sound.getX(), at.x);
        double z = wrap.nearest(sound.getZ(), at.z);
        if (x != sound.getX()) ((AbstractSoundInstanceAccessor) sound).alpha_omega$setX(x);
        if (z != sound.getZ()) ((AbstractSoundInstanceAccessor) sound).alpha_omega$setZ(z);
    }
}
