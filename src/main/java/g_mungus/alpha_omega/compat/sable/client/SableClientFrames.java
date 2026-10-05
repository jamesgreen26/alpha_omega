package g_mungus.alpha_omega.compat.sable.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import g_mungus.alpha_omega.compat.sable.SableFrames;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.compat.sable.client.SubLevelSnapshotInterpolatorAccessor;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Sub-levels on the client between faces (design §8.1). Loaded only when Sable is.
 *
 * <p>When the server moves a sub-level to another face, its next pose snapshot arrives in the new face's storage.
 * The poses it interpolates and draws from (older snapshots, the running sample, the logical and last poses) are then
 * re-expressed in the new face's storage, so it carries on smoothly instead of sliding thousands of blocks.
 *
 * <p>One on a neighbouring face is drawn where it really is: its render pose is taken into the camera's face.
 */
public final class SableClientFrames {

    private SableClientFrames() {
    }

    /** Before a client sub-level ticks: re-expresses its poses on the face of its newest snapshot. Returns whether any moved. */
    public static boolean reframe(ClientSubLevel subLevel) {
        CubeGeometry geometry = Cube.of(subLevel.getLevel());
        if (geometry == null) return false;
        SubLevelSnapshotInterpolator interpolator = subLevel.getInterpolator();
        List<SubLevelSnapshotInterpolator.Snapshot> buffer = interpolator.buffer;
        CubeFace now;
        boolean moved = false;
        // Snapshots arrive on the network threads, under this lock.
        synchronized (buffer) {
            if (buffer.isEmpty()) return false;
            now = SableFrames.face(geometry, buffer.getLast().pose());
            if (now == null) return false;
            for (int i = 0; i < buffer.size(); i++) {
                SubLevelSnapshotInterpolator.Snapshot snapshot = buffer.get(i);
                CubeFace face = SableFrames.face(geometry, snapshot.pose());
                if (face == null || face == now) continue;
                Pose3d pose = new Pose3d(snapshot.pose());
                SableFrames.transform(geometry, face, now, pose);
                buffer.set(i, new SubLevelSnapshotInterpolator.Snapshot(snapshot.gameTick(), pose));
                moved = true;
            }
        }
        for (Pose3d pose : new Pose3d[] {((SubLevelSnapshotInterpolatorAccessor) interpolator).alpha_omega$runningSnapshot(), subLevel.logicalPose(),
            (Pose3d) subLevel.lastPose()}) {
            CubeFace face = SableFrames.face(geometry, pose);
            if (face == null || face == now) continue;
            SableFrames.transform(geometry, face, now, pose);
            moved = true;
        }
        return moved;
    }

    /** A freshly computed render pose: if the sub-level is on a neighbour of the camera's face, the same pose in the camera's face. */
    public static void toCameraFace(ClientSubLevel subLevel, Pose3d pose) {
        CubeGeometry geometry = Cube.of(subLevel.getLevel());
        if (geometry == null) return;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        CubeFace home = geometry.faceAt(camera.x, camera.z);
        CubeFace face = SableFrames.face(geometry, pose);
        if (home == null || face == null || face == home || !face.isNeighbour(home)) return;
        SableFrames.transform(geometry, face, home, pose);
    }
}
