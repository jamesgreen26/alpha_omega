package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.neighbour.ImageGeometry;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where things the client hears of are seen from the camera: particles and sounds at an image's position play at the
 * transformed position, and a remote entity that jumped by an element of {@code Γ} is re-expressed in its new frame.
 */
public final class NeighbourEffects {

    private NeighbourEffects() {
    }

    /**
     * The element taking a point to where the camera sees it, or null when that is where it is. A point is seen at its
     * source in the tile and at that source moved by each image the camera has ({@link ImageGeometry#placements});
     * a sound or particle plays at whichever of those is nearest the camera.
     */
    @Nullable
    public static Motion toCamera(Level level, double x, double z) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return null;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Motion k = ImageGeometry.nearestPlacement(geometry, ImageRenderer.currentImages(geometry), x, z, camera.x, camera.z);
        return k.isIdentity() ? null : k;
    }

    /** How near the same place in the world an entity must land to count as crossing rather than moving. */
    private static final double CROSSING_SLACK = 16.0;

    /**
     * The element a remote entity moved by between two positions it was sent, or null if it did not cross: it jumped
     * in storage to near an expression of where it was ({@code Frames#crossing}).
     */
    @Nullable
    public static Motion crossing(Level level, Vec3 from, Vec3 to) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return null;
        return g_mungus.alpha_omega.transfer.Frames.crossing(geometry, from.x, from.z, to.x, to.z, CROSSING_SLACK);
    }
}
