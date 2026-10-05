package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.util.Mth;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Gravity on sub-levels rounds out with height. At sea level (the face plane) it is the face's own down, as for
 * everything else; it turns toward the cube's centre as a sub-level climbs, and from the build limit up it points
 * straight at the centre, as round a planet. So a ship high over an edge feels no sudden turn of gravity when it
 * crosses. Its strength stays the dimension's. Loaded only when Sable is.
 *
 * <p>Sable's physics has one gravity for the whole level (the face's down, in storage); each physics substep, every
 * sub-level gets the difference as an impulse.
 */
public final class SubLevelGravity {

    private SubLevelGravity() {
    }

    /** How far gravity has turned toward the cube's centre at storage height {@code y}: 0 at sea level, 1 at the build limit. */
    public static double roundness(CubeGeometry geometry, double y) {
        return Mth.clamp((y - geometry.planeY) / (geometry.maxY - geometry.planeY), 0.0, 1.0);
    }

    /** Gravity at a storage point of {@code face}, with the strength of {@code base} (the level's gravity), in storage axes. */
    public static Vector3d at(CubeGeometry geometry, CubeFace face, Vector3dc position, Vector3dc base, Vector3d dest) {
        double t = roundness(geometry, position.y());
        if (t <= 0.0) return dest.set(base);
        double[] c = geometry.toCube(face, position.x(), position.y(), position.z());
        double length = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        double[] down = face.toCube(0, -1, 0);
        double x = (1.0 - t) * down[0] - t * c[0] / length;
        double y = (1.0 - t) * down[1] - t * c[1] / length;
        double z = (1.0 - t) * down[2] - t * c[2] / length;
        double[] local = face.toLocal(x, y, z);
        return dest.set(local[0], local[1], local[2]).normalize(base.length());
    }

    /** Each physics substep, before a sub-level moves: the difference between its gravity and the level's. */
    public static void prePhysicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        CubeGeometry geometry = Cube.of(subLevel.getLevel());
        if (geometry == null || subLevel.isRemoved()) return;
        Vector3dc position = subLevel.logicalPose().position();
        CubeFace face = geometry.faceAt(position.x(), position.z());
        if (face == null || roundness(geometry, position.y()) <= 0.0) return;
        Vector3d base = DimensionPhysicsData.getGravity(subLevel.getLevel());
        Vector3d impulse = at(geometry, face, position, base, new Vector3d()).sub(base).mul(subLevel.getMassTracker().getMass() * timeStep);
        // The handle takes impulses in the sub-level's own frame.
        handle.applyLinearAndAngularImpulse(subLevel.logicalPose().transformNormalInverse(impulse, new Vector3d()), new Vector3d(), false);
    }
}
