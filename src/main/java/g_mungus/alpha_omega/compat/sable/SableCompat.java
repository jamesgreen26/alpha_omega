package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.Sable;
import g_mungus.alpha_omega.transfer.FrameTransfer;

/** Sable compatibility set up at mod construction; loaded only when Sable is. */
public final class SableCompat {

    private SableCompat() {
    }

    public static void init() {
        // Whatever stands on a sub-level crosses with it ({@link SubLevelTransfers}), not on its own.
        FrameTransfer.registerCarrier(entity -> Sable.HELPER.getTrackingSubLevel(entity) != null);
    }
}
