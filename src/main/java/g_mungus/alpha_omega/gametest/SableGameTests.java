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
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * A re-lifted chunk carries the sub-level over it: pose, last pose and physics body move by whole laps. Then,
     * being out of its island's frame, it is brought back like an entity would be.
     */
    @GameTest(template = TEMPLATE)
    public static void subLevelsMoveWithTheirChunks(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        int period = Wraps.overworld().period;
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> blocks = List.of(anchor, anchor.east());
        for (BlockPos pos : blocks) level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, blocks);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(SableTestOps.inFrame(level, plot), "assembled sub-level not brought into its island's frame");
            double[] before = SableTestOps.pose(level, plot);

            SableTestOps.relift(level, plot, 1);

            double[] after = SableTestOps.pose(level, plot);
            helper.assertTrue(after[0] == before[0] + period && after[2] == before[2], "pose did not move one lap: " + before[0] + " -> " + after[0]);
            helper.assertTrue(after[3] == before[3] + period && after[4] == before[4], "last pose did not move one lap");
            helper.runAfterDelay(3, () -> {
                double[] later = SableTestOps.pose(level, plot);
                helper.assertTrue(SableTestOps.inFrame(level, plot), "sub-level left out of its island's frame");
                helper.assertTrue(Math.abs(later[0] - before[0]) < 4 && Math.abs(later[2] - before[2]) < 4,
                    "sub-level did not return by whole laps: " + before[0] + " -> " + later[0]);
                helper.succeed();
            });
        });
    }

    /**
     * Terrain edits reach physics in a frame other than lap 0: a sub-level resting on a block in an island lifted
     * three laps out falls when the block is removed through canonical storage.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void terrainChangesReachPhysicsInAnyLap(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wraps.overworld();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = wrap.canonChunk((test.getX() >> 4) + wrap.chunkPeriod / 2);
        int cz = wrap.canonChunk((test.getZ() >> 4) + wrap.chunkPeriod / 2 + 256);
        int laps = 3;
        int x = (cx << 4) + 8 + laps * wrap.period;
        int z = (cz << 4) + 8;
        int y = level.getMaxBuildHeight() - 60;
        ServerPlayer player = farPlayer(helper, x, y + 20, z);
        forceAround(level, cx, cz, true);
        BlockPos pad = new BlockPos(x, y, z);
        BlockPos[] plot = {null};
        double[] rested = {Double.NaN};
        long[] removedAt = {-1};

        helper.onEachTick(() -> {
            if (plot[0] == null) {
                long chunkLaps = IslandManager.of(level).laps(cx, cz);
                if (chunkLaps == IslandGraph.ABSENT || !level.hasChunk(cx, cz)) return;
                helper.assertTrue(IslandGraph.lapX(chunkLaps) == laps, "island did not seed in the player's frame: lap " + IslandGraph.lapX(chunkLaps));
                level.setBlockAndUpdate(pad, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pad.above(), Blocks.IRON_BLOCK.defaultBlockState());
                plot[0] = SableTestOps.assemble(level, pad.above(), List.of(pad.above()));
                removedAt[0] = level.getGameTime() + 60;
            } else if (level.getGameTime() == removedAt[0]) {
                rested[0] = SableTestOps.pose(level, plot[0])[1];
                helper.assertTrue(Math.abs(rested[0] - (y + 1.5)) < 0.6, "sub-level is not resting on the pad: y " + rested[0]);
                level.setBlockAndUpdate(pad, Blocks.AIR.defaultBlockState());
            } else if (removedAt[0] >= 0 && level.getGameTime() > removedAt[0] + 20) {
                double now = SableTestOps.pose(level, plot[0])[1];
                helper.assertTrue(now < rested[0] - 1, "sub-level did not fall after its support was removed: y " + rested[0] + " -> " + now);
                SableTestOps.remove(level, plot[0]);
                forceAround(level, cx, cz, false);
                level.getServer().getPlayerList().remove(player);
                helper.succeed();
            }
        });
    }

    /**
     * A sub-level parked three laps out unloads with the chunk under it and returns, in the same place, when the
     * chunk loads again.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void subLevelsUnloadAndReloadInAnyLap(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wraps.overworld();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = wrap.canonChunk((test.getX() >> 4) + wrap.chunkPeriod / 2);
        int cz = wrap.canonChunk((test.getZ() >> 4) + wrap.chunkPeriod / 2 + 320);
        int laps = 3;
        int x = (cx << 4) + 8 + laps * wrap.period;
        int z = (cz << 4) + 8;
        int y = level.getMaxBuildHeight() - 60;
        BlockPos pad = new BlockPos(x, y, z);
        ServerPlayer[] player = {farPlayer(helper, x, y + 20, z)};
        forceAround(level, cx, cz, true);
        BlockPos[] plot = {null};
        double[] parked = new double[3];
        int[] phase = {0};
        long[] at = {0};

        helper.onEachTick(() -> {
            switch (phase[0]) {
                case 0 -> {
                    long chunkLaps = IslandManager.of(level).laps(cx, cz);
                    if (chunkLaps == IslandGraph.ABSENT || !level.hasChunk(cx, cz)) return;
                    level.setBlockAndUpdate(pad, Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(pad.above(), Blocks.IRON_BLOCK.defaultBlockState());
                    plot[0] = SableTestOps.assemble(level, pad.above(), List.of(pad.above()));
                    at[0] = level.getGameTime() + 40;
                    phase[0] = 1;
                }
                case 1 -> {
                    if (level.getGameTime() < at[0]) return;
                    System.arraycopy(SableTestOps.pose(level, plot[0]), 0, parked, 0, 3);
                    forceAround(level, cx, cz, false);
                    level.getServer().getPlayerList().remove(player[0]);
                    phase[0] = 2;
                }
                case 2 -> {
                    if (SableTestOps.exists(level, plot[0]) || level.hasChunk(cx, cz)) return;
                    player[0] = farPlayer(helper, x, y + 20, z);
                    forceAround(level, cx, cz, true);
                    phase[0] = 3;
                }
                case 3 -> {
                    // Holding storage is read back over a few ticks; the test times out if it never is.
                    if (!SableTestOps.exists(level, plot[0])) return;
                    double[] back = SableTestOps.pose(level, plot[0]);
                    helper.assertTrue(SableTestOps.inFrame(level, plot[0]), "reloaded sub-level is not in its island's frame");
                    helper.assertTrue(Math.abs(wrap.minDelta(back[0], parked[0])) < 2 && Math.abs(back[1] - parked[1]) < 2 && Math.abs(back[2] - parked[2]) < 2,
                        "sub-level reloaded somewhere else: " + parked[0] + ", " + parked[2] + " -> " + back[0] + ", " + back[2]);
                    SableTestOps.remove(level, plot[0]);
                    forceAround(level, cx, cz, false);
                    level.getServer().getPlayerList().remove(player[0]);
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    private static ServerPlayer farPlayer(GameTestHelper helper, int x, int y, int z) {
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        player.getAbilities().flying = true;
        player.moveTo(x + 0.5, y, z + 0.5);
        helper.getLevel().getChunkSource().move(player);
        return player;
    }

    private static void forceAround(ServerLevel level, int cx, int cz, boolean force) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.setChunkForced(cx + dx, cz + dz, force);
    }
}
