package g_mungus.alpha_omega.mixin.network;

import g_mungus.alpha_omega.network.PacketNormalization;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R6: every game packet handler hops to the main thread through here, and only returns normally once it is on
 * the main thread. That is the single point where incoming positions are normalized, before vanilla reads them.
 */
@Mixin(PacketUtils.class)
abstract class PacketUtilsMixin {

    @Inject(method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
        at = @At("RETURN"))
    private static void alpha_omega$normalize(Packet<?> packet, PacketListener listener, BlockableEventLoop<?> loop, CallbackInfo ci) {
        PacketNormalization.normalize(packet, listener);
    }
}
