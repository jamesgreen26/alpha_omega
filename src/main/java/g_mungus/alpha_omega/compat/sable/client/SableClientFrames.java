package g_mungus.alpha_omega.compat.sable.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import g_mungus.alpha_omega.client.ClientFrameTransfer;
import g_mungus.alpha_omega.client.NeighbourEffects;
import g_mungus.alpha_omega.compat.sable.SableFrames;
import g_mungus.alpha_omega.mixin.compat.sable.client.SubLevelSnapshotInterpolatorAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Sub-levels on the client across seams. Loaded only when Sable is.
 *
 * <p>When the server moves a sub-level by an element of {@code Γ}, its next pose snapshot arrives in the new frame.
 * The poses it interpolates and draws from (older snapshots, the running sample, the logical and last poses) are then
 * re-expressed in the new frame, so it carries on smoothly instead of sliding across storage; the local player
 * standing on it goes with it.
 *
 * <p>One an image shows is drawn there: its render pose is moved by that image's element.
 *
 * <p>Inactive until phases 5 and 6 ({@link SableFrames#crossing} and {@link NeighbourEffects#toCamera} give nothing).
 */
public final class SableClientFrames {

    private SableClientFrames() {
    }

    /** Before a client sub-level ticks: re-expresses its poses in the frame of its newest snapshot. Returns whether any moved. */
    public static boolean reframe(ClientSubLevel subLevel) {
        OrbifoldGeometry geometry = Orbifold.of(subLevel.getLevel());
        if (geometry == null) return false;
        SubLevelSnapshotInterpolator interpolator = subLevel.getInterpolator();
        List<SubLevelSnapshotInterpolator.Snapshot> buffer = interpolator.buffer;
        Pose3d newest;
        boolean moved = false;
        // Snapshots arrive on the network threads, under this lock.
        synchronized (buffer) {
            if (buffer.isEmpty()) return false;
            newest = new Pose3d(buffer.getLast().pose());
            for (int i = 0; i < buffer.size(); i++) {
                SubLevelSnapshotInterpolator.Snapshot snapshot = buffer.get(i);
                Motion g = SableFrames.crossing(geometry, snapshot.pose(), newest);
                if (g == null) continue;
                Pose3d pose = new Pose3d(snapshot.pose());
                SableFrames.transform(g, pose);
                buffer.set(i, new SubLevelSnapshotInterpolator.Snapshot(snapshot.gameTick(), pose));
                moved = true;
            }
        }
        Motion carried = SableFrames.crossing(geometry, subLevel.logicalPose(), newest);
        for (Pose3d pose : new Pose3d[] {((SubLevelSnapshotInterpolatorAccessor) interpolator).alpha_omega$runningSnapshot(), subLevel.logicalPose(),
            (Pose3d) subLevel.lastPose()}) {
            Motion g = SableFrames.crossing(geometry, pose, newest);
            if (g == null) continue;
            SableFrames.transform(g, pose);
            moved = true;
        }
        // The local player standing on it crosses with it, in the same tick, before it moves.
        LocalPlayer player = Minecraft.getInstance().player;
        if (carried != null && player != null && Sable.HELPER.getTrackingSubLevel(player) == subLevel) {
            ClientFrameTransfer.carriedAcross(player, carried);
        }
        return moved;
    }

    /** A freshly computed render pose: if an image shows the sub-level to the camera, the same pose moved there. */
    public static void toCameraFace(ClientSubLevel subLevel, Pose3d pose) {
        Motion g = NeighbourEffects.toCamera(subLevel.getLevel(), pose.position().x, pose.position().z);
        if (g != null) SableFrames.transform(g, pose);
    }
}
