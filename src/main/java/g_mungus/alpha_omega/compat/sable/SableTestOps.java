package g_mungus.alpha_omega.compat.sable;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
}
