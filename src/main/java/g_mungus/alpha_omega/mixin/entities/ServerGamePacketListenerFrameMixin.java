package g_mungus.alpha_omega.mixin.entities;

import g_mungus.alpha_omega.transfer.FrameTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the server moves a player to another frame, the client's movement is in the frame it left until it has applied
 * the same element: ignored until then, as vanilla ignores movement around a teleport, but with no position sent.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerFrameMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = {"handleMovePlayer", "handleMoveVehicle"}, cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"))
    private void alpha_omega$awaitFrame(CallbackInfo ci) {
        if (FrameTransfers.awaitingAck(this.player)) ci.cancel();
    }
}
