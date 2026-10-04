package g_mungus.alpha_omega.mixin.compat.sable.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundRecentlySplitSubLevelPacket;
import g_mungus.alpha_omega.compat.sable.SableClientFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A piece split off a sub-level starts at the image of its pose nearest the camera. */
@Mixin(value = ClientboundRecentlySplitSubLevelPacket.class, remap = false)
abstract class ClientboundRecentlySplitSubLevelPacketMixin {

    @ModifyExpressionValue(method = "handle", at = @At(value = "FIELD", target = "Ldev/ryanhcode/sable/network/packets/tcp/ClientboundRecentlySplitSubLevelPacket;pose:Ldev/ryanhcode/sable/companion/math/Pose3d;"))
    private Pose3d alpha_omega$poseNearCamera(Pose3d pose) {
        return (Pose3d) SableClientFrames.nearCamera(pose);
    }
}
