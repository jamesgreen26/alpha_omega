package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
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

    /**
     * The element a sub-level jumped by between two poses it was sent, or null if it did not cross. Phase 5 works this
     * out from the frames of the two; until then nothing crosses.
     */
    @Nullable
    public static Motion crossing(OrbifoldGeometry geometry, Pose3dc from, Pose3dc to) {
        return null;
    }
}
