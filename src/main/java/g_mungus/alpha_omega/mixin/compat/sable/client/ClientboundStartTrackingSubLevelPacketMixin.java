package g_mungus.alpha_omega.mixin.compat.sable.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStartTrackingSubLevelPacket;
import g_mungus.alpha_omega.compat.sable.SableClientFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A newly tracked sub-level starts at the image of its pose nearest the camera. */
@Mixin(value = ClientboundStartTrackingSubLevelPacket.class, remap = false)
abstract class ClientboundStartTrackingSubLevelPacketMixin {

    @ModifyExpressionValue(method = "handle", at = @At(value = "FIELD", target = "Ldev/ryanhcode/sable/network/packets/tcp/ClientboundStartTrackingSubLevelPacket;lastPose:Ldev/ryanhcode/sable/companion/math/Pose3dc;"))
    private Pose3dc alpha_omega$lastPoseNearCamera(Pose3dc pose) {
        return SableClientFrames.nearCamera(pose);
    }

    @ModifyExpressionValue(method = "handle", at = @At(value = "FIELD", target = "Ldev/ryanhcode/sable/network/packets/tcp/ClientboundStartTrackingSubLevelPacket;pose:Ldev/ryanhcode/sable/companion/math/Pose3d;"))
    private Pose3d alpha_omega$poseNearCamera(Pose3d pose) {
        return (Pose3d) SableClientFrames.nearCamera(pose);
    }
}
