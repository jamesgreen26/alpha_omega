package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.sable.SableTestOps;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sable compatibility. Each test passes trivially without Sable; Sable types stay behind {@link SableTestOps} so
 * this class loads either way. Run with {@code -PwithSable}.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SableGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static boolean sable(GameTestHelper helper) {
        if (ModList.get().isLoaded("sable")) return true;
        helper.succeed();
        return false;
    }

    /** Plots are off the torus: assembled blocks land in the plot, not on the real terrain they would fold onto. */
    @GameTest(template = TEMPLATE)
    public static void plotsAreOffTorus(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wraps.overworld();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> blocks = List.of(anchor, anchor.east(), anchor.above());
        for (BlockPos pos : blocks) level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());

        BlockPos plot = SableTestOps.assemble(level, anchor, blocks);

        helper.assertTrue(Wrap.offTorus(plot.getX(), plot.getZ()), "plot is not off the torus: " + plot);
        helper.assertTrue(level.getBlockState(anchor).isAir(), "assembled block left behind at " + anchor);
        helper.assertTrue(level.getBlockState(plot).is(Blocks.GOLD_BLOCK), "plot block not readable at " + plot);
        int foldedX = Math.floorMod(SectionPos.blockToSectionCoord(plot.getX()), wrap.chunkPeriod);
        int foldedZ = Math.floorMod(SectionPos.blockToSectionCoord(plot.getZ()), wrap.chunkPeriod);
        helper.assertTrue(IslandManager.of(level).graph().laps(foldedX, foldedZ) == IslandGraph.ABSENT,
            "plot chunk joined an island at its folded position " + foldedX + ", " + foldedZ);
        helper.succeed();
    }
}
