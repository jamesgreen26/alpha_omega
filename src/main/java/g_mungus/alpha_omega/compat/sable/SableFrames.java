package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Sub-level poses between faces' storage: the same cube pose, seen from another face. Loaded only when Sable is. */
public final class SableFrames {

    private SableFrames() {
    }

    /** {@code from}'s storage axes to {@code to}'s, as a rotation matrix. */
    public static Matrix3d rotation(CubeFace from, CubeFace to) {
        Matrix3d m = new Matrix3d();
        for (int j = 0; j < 3; j++) {
            double[] column = CubeGeometry.rotate(from, to, j == 0 ? 1 : 0, j == 1 ? 1 : 0, j == 2 ? 1 : 0);
            m.setColumn(j, column[0], column[1], column[2]);
        }
        return m;
    }

    /** Moves a pose of {@code from}'s storage to the same pose in {@code to}'s: position by {@code T}, orientation turned. */
    public static void transform(CubeGeometry geometry, CubeFace from, CubeFace to, Pose3d pose) {
        Vector3d p = pose.position();
        double[] moved = geometry.transform(from, to, p.x, p.y, p.z);
        p.set(moved[0], moved[1], moved[2]);
        pose.orientation().premul(rotation(from, to).getNormalizedRotation(new Quaterniond())).normalize();
    }

    /** The face whose storage a pose is in, or null between faces (Sable's plots, for one). */
    @Nullable
    public static CubeFace face(CubeGeometry geometry, Pose3dc pose) {
        return geometry.faceAt(pose.position().x(), pose.position().z());
    }
}
