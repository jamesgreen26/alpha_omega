package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.ClientFrameTransfer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The local player crosses edges after it has moved and sent its position for the tick. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void alpha_omega$crossEdges(CallbackInfo ci) {
        ClientFrameTransfer.afterTick((LocalPlayer) (Object) this);
    }
}
