package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.transfer.Frames;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Sub-level poses moved by an element of {@code Γ}: the same pose in another frame. Loaded only when Sable is. */
public final class SableFrames {

    private SableFrames() {
    }

    /** {@code g}'s turn, as a rotation matrix. */
    public static Matrix3d rotation(Motion g) {
        return g.turned() ? new Matrix3d().scaling(-1.0, 1.0, -1.0) : new Matrix3d();
    }

    /** Moves a pose by {@code g}: position by the motion, orientation turned. */
    public static void transform(Motion g, Pose3d pose) {
        Vector3d p = pose.position();
        p.set(g.pointX(p.x), p.y, g.pointZ(p.z));
        pose.orientation().premul(rotation(g).getNormalizedRotation(new Quaterniond())).normalize();
    }

    /** How near the same place in the world a pose must land to count as crossing rather than moving. */
    private static final double SLACK = 16.0;

    /** The element a sub-level jumped by between two poses it was sent, or null if it did not cross. */
    @Nullable
    public static Motion crossing(OrbifoldGeometry geometry, Pose3dc from, Pose3dc to) {
        return Frames.crossing(geometry, from.position().x(), from.position().z(), to.position().x(), to.position().z(), SLACK);
    }
}
