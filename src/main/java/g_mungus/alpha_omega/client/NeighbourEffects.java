package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where things the client hears of are seen from the camera: particles and sounds at an image's position play at the
 * transformed position, and a remote entity that jumped by an element of {@code Γ} is re-expressed in its new frame.
 *
 * <p>Inactive until phase 6 (image views), which works out the elements; until then everything plays where it is.
 */
public final class NeighbourEffects {

    private NeighbourEffects() {
    }

    /**
     * The element taking a point to where the camera sees it, or null when it is already there. Phase 6: the image
     * element whose view holds the point.
     */
    @Nullable
    public static Motion toCamera(Level level, double x, double z) {
        if (Orbifold.of(level) == null) return null;
        return null;
    }

    /**
     * The element a remote entity moved by between two positions it was sent, or null if it did not cross. Phase 5:
     * the frame change between the two.
     */
    @Nullable
    public static Motion crossing(Level level, Vec3 from, Vec3 to) {
        if (Orbifold.of(level) == null) return null;
        return null;
    }
}
