package g_mungus.alpha_omega.mixin.compat.sable.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The interpolator's running sample, which the sub-level's pose is set from each tick. */
@Mixin(value = SubLevelSnapshotInterpolator.class, remap = false)
public interface SubLevelSnapshotInterpolatorAccessor {

    @Accessor("runningSnapshot")
    Pose3d alpha_omega$runningSnapshot();
}
