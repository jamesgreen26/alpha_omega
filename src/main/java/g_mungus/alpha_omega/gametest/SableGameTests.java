package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.sable.SableTestOps;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sable compatibility. Each test passes trivially without Sable; Sable types stay behind {@link SableTestOps} so this
 * class loads either way. Run with {@code -PwithSable}. Sub-levels crossing seams come back with phase 5.
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

    /** Blocks assemble into a sub-level: they leave the ground and can be read in the plot. */
    @GameTest(template = TEMPLATE)
    public static void assembles(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> blocks = List.of(anchor, anchor.east(), anchor.above());
        for (BlockPos pos : blocks) level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, blocks);
        helper.assertTrue(level.getBlockState(anchor).isAir(), "assembled block left behind at " + anchor);
        helper.assertTrue(level.getBlockState(plot).is(Blocks.GOLD_BLOCK), "plot block not readable at " + plot + ": " + level.getBlockState(plot));
        helper.assertTrue(SableTestOps.exists(level, plot), "no sub-level owns the plot");
        SableTestOps.remove(level, plot);
        helper.succeed();
    }

    /** A sub-level dropped over the ground falls onto it and comes to rest. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void fallsOntoTheGround(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlockAndUpdate(anchor, Blocks.IRON_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, List.of(anchor));
        double start = SableTestOps.pose(level, plot)[1];
        helper.runAfterDelay(100, () -> {
            double y = SableTestOps.pose(level, plot)[1];
            // The template's floor is its bottom layer: the block comes to rest on it, one cell lower than it started... or more.
            helper.assertTrue(y < start - 0.5, "the sub-level did not fall: " + start + " -> " + y);
            helper.assertTrue(y > helper.absolutePos(BlockPos.ZERO).getY(), "the sub-level fell through the floor: " + y);
            SableTestOps.remove(level, plot);
            helper.succeed();
        });
    }
}
