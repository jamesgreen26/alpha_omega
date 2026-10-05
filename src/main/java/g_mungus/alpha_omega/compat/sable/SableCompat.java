package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.transfer.FaceTransfer;

/** Sable compatibility set up at mod construction; loaded only when Sable is. */
public final class SableCompat {

    private SableCompat() {
    }

    public static void init() {
        // Whatever stands on a sub-level crosses with it ({@link SubLevelTransfers}), not on its own.
        FaceTransfer.registerCarrier(entity -> Sable.HELPER.getTrackingSubLevel(entity) != null);
        // Sub-levels' blocks live in plots, outside every face: building on a sub-level is Sable's business.
        Cube.registerOutsider((level, pos) -> {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            return container != null && container.inBounds(pos);
        });
    }
}
