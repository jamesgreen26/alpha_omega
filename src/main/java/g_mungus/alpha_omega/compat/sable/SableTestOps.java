package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Sable-facing steps of {@link g_mungus.alpha_omega.gametest.SableGameTests}; loaded only when Sable is. */
public final class SableTestOps {

    private SableTestOps() {
    }

    /** Assembles the blocks into a sub-level and returns the plot position its anchor moved to. */
    public static BlockPos assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks) {
        BoundingBox3i bounds = new BoundingBox3i(anchor.getX() - 4, anchor.getY() - 4, anchor.getZ() - 4, anchor.getX() + 4, anchor.getY() + 4, anchor.getZ() + 4);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
        return subLevel.getPlot().getCenterBlock();
    }

    /** The world position of the sub-level owning {@code plot}. */
    public static double[] pose(ServerLevel level, BlockPos plot) {
        Vector3dc now = Sable.HELPER.getContaining(level, plot).logicalPose().position();
        return new double[] {now.x(), now.y(), now.z()};
    }

    /** Where a block of the plot of the sub-level owning {@code plot} is in the world (its centre). */
    public static double[] world(ServerLevel level, BlockPos plot, BlockPos plotBlock) {
        Vec3 at = Sable.HELPER.getContaining(level, plot).logicalPose().transformPosition(Vec3.atCenterOf(plotBlock));
        return new double[] {at.x, at.y, at.z};
    }

    /** Whether the sub-level owning {@code plot} is tracked by (sent to) the player. */
    public static boolean tracks(ServerLevel level, BlockPos plot, ServerPlayer player) {
        return ((ServerSubLevel) Sable.HELPER.getContaining(level, plot)).getTrackingPlayers().contains(player.getGameProfile().getId());
    }

    /** Adds velocity (blocks per second) to the sub-level owning {@code plot}. */
    public static void push(ServerLevel level, BlockPos plot, double vx, double vy, double vz) {
        ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(level, plot);
        RigidBodyHandle.of(subLevel).addLinearAndAngularVelocity(new Vector3d(vx, vy, vz), new Vector3d());
    }

    public static boolean exists(ServerLevel level, BlockPos plot) {
        return Sable.HELPER.getContaining(level, plot) != null;
    }

    public static void remove(ServerLevel level, BlockPos plot) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, plot);
        if (subLevel != null) SubLevelContainer.getContainer(level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
    }
}
