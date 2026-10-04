package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
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

    /** The world position of the sub-level owning {@code plot}: {x, y, z, lastX, lastZ}. */
    public static double[] pose(ServerLevel level, BlockPos plot) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, plot);
        Vector3dc now = subLevel.logicalPose().position();
        Vector3dc last = subLevel.lastPose().position();
        return new double[] {now.x(), now.y(), now.z(), last.x(), last.z()};
    }

    /** Re-lifts the chunk under the sub-level owning {@code plot} by {@code lapX} laps, as an island shift would. */
    public static void relift(ServerLevel level, BlockPos plot, int lapX) {
        Wrap wrap = Wrap.of(level);
        Vector3dc position = Sable.HELPER.getContaining(level, plot).logicalPose().position();
        Long2LongOpenHashMap changes = new Long2LongOpenHashMap();
        changes.put(IslandGraph.key(wrap.canonChunk(SectionPos.posToSectionCoord(position.x())), wrap.canonChunk(SectionPos.posToSectionCoord(position.z()))),
            IslandGraph.packLaps(lapX, 0));
        new SableFrames().translate(level, changes);
    }
}
