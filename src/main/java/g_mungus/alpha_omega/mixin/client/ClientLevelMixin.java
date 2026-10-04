package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.ClientImages;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Positional sounds are delayed by their distance from the camera: measure it to the nearest image, or a sound at
 * another image would be held back for minutes (mod-compatibility §5.3).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {

    @ModifyVariable(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double alpha_omega$soundNearCameraX(double x) {
        return ClientImages.nearCameraX(x);
    }

    @ModifyVariable(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double alpha_omega$soundNearCameraZ(double z) {
        return ClientImages.nearCameraZ(z);
    }
}
