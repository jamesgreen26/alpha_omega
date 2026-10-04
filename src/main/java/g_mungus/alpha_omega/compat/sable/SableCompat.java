package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import g_mungus.alpha_omega.island.FrameParticipants;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.wrap.Wrap;

/** Sable compatibility set up at mod construction; loaded only when Sable is. */
public final class SableCompat {

    private SableCompat() {
    }

    public static void init() {
        // Sub-level plots are real chunks far out in the same level; they stay where Sable put them.
        int plotChunks = 1 << SubLevelContainer.DEFAULT_LOG_PLOT_SIZE;
        int min = SubLevelContainer.DEFAULT_ORIGIN * plotChunks;
        int max = (SubLevelContainer.DEFAULT_ORIGIN + (1 << SubLevelContainer.DEFAULT_LOG_SIZE_LENGTH)) * plotChunks;
        Wrap.excludeFromTorus(min, max);
        FrameParticipants.register(new SableFrames());
        // Physics runs in f32: within 100,000 blocks of the origin, positions stay finer than 1/100 block.
        IslandManager.limitRecenterDistance(100_000);
    }
}
