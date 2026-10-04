package g_mungus.alpha_omega.mixin.compat.sable.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import g_mungus.alpha_omega.compat.sable.SableClientFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every pose snapshot (movement packets over TCP or UDP, and tracking start) lands near the camera. */
@Mixin(value = SubLevelSnapshotInterpolator.class, remap = false)
abstract class SubLevelSnapshotInterpolatorMixin {

    @ModifyVariable(method = "receiveSnapshot", at = @At("HEAD"), argsOnly = true)
    private Pose3dc alpha_omega$nearCamera(Pose3dc pose) {
        return SableClientFrames.nearCamera(pose);
    }

    @ModifyVariable(method = "setFirstPoses", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Pose3dc alpha_omega$firstNearCamera(Pose3dc pose) {
        return SableClientFrames.nearCamera(pose);
    }

    @ModifyVariable(method = "setFirstPoses", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private Pose3dc alpha_omega$secondNearCamera(Pose3dc pose) {
        return SableClientFrames.nearCamera(pose);
    }
}
