package g_mungus.alpha_omega.mixin.compat.sable.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import g_mungus.alpha_omega.compat.sable.client.SableClientFrames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client sub-levels follow transfers between faces, and draw where they are from the camera's face ({@link SableClientFrames}). */
@Mixin(value = ClientSubLevel.class, remap = false)
abstract class ClientSubLevelMixin {

    @Unique
    private boolean alpha_omega$reframed;

    @Inject(method = "tick", at = @At("HEAD"))
    private void alpha_omega$reframe(CallbackInfo ci) {
        this.alpha_omega$reframed = SableClientFrames.reframe((ClientSubLevel) (Object) this);
    }

    /** The bounds swept since last tick would span both faces' storage: start them afresh where it is now. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void alpha_omega$freshBounds(CallbackInfo ci) {
        if (this.alpha_omega$reframed) ((ClientSubLevel) (Object) this).forceUpdateBounds();
    }

    /** Only the freshly computed pose (the second return): the cached one returned before it is already taken across. */
    @Inject(method = "renderPose(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;", at = @At(value = "RETURN", ordinal = 1))
    private void alpha_omega$toCameraFace(float partialTick, CallbackInfoReturnable<Pose3dc> cir) {
        SableClientFrames.toCameraFace((ClientSubLevel) (Object) this, (Pose3d) cir.getReturnValue());
    }
}
