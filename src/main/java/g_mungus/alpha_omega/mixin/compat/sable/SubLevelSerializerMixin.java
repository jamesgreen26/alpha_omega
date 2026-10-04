package g_mungus.alpha_omega.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import g_mungus.alpha_omega.frame.Frames;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A sub-level saved in one frame loads into the frame of the island under it now (I4), as entities do on load:
 * its pose moves by whole laps before Sable places it and its physics body.
 */
@Mixin(value = SubLevelSerializer.class, remap = false)
abstract class SubLevelSerializerMixin {

    @ModifyExpressionValue(method = "fullyLoad", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/util/SableNBTUtils;readPose3d(Lnet/minecraft/nbt/CompoundTag;)Ldev/ryanhcode/sable/companion/math/Pose3d;"))
    private static Pose3d alpha_omega$intoFrame(Pose3d pose, @Local(argsOnly = true) ServerLevel level) {
        long offset = Frames.lapOffset(level, SectionPos.posToSectionCoord(pose.position().x), SectionPos.posToSectionCoord(pose.position().z));
        pose.position().add(Frames.offsetX(offset), 0, Frames.offsetZ(offset));
        return pose;
    }
}
