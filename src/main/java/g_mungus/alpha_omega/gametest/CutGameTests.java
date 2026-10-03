package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.island.Invariants;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 5: cuts, recentering and portal linking. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CutGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int W = Wrap.PERIOD;
    private static final int N = Wrap.CHUNK_PERIOD;

    /** A row of loaded chunks all the way around the world would make an island that wraps; it gets a cut. */
    @GameTest(template = TEMPLATE, batch = "alpha_omega_band", timeoutTicks = 3000)
    public static void aBandAroundTheWorldIsCut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int z = Wrap.canonChunk((helper.absolutePos(BlockPos.ZERO).getZ() >> 4) + IslandGameTests.BAND_REGION);
        int y = level.getMaxBuildHeight() - 10;
        for (int x = 0; x < N; x++) level.setChunkForced(x, z, true);
        Pig[] pig = new Pig[1];

        helper.succeedWhen(() -> {
            IslandManager islands = IslandManager.of(level);
            helper.assertTrue(islands.graph().chunkCount() >= N, "band not loaded yet");
            for (int x = 0; x < N; x += 37) helper.assertTrue(islands.laps(x, z) != IslandGraph.ABSENT, "band not loaded yet");
            if (pig[0] == null) {
                pig[0] = EntityType.PIG.create(level);
                pig[0].moveTo((N / 3 << 4) + 0.5, y, (z << 4) + 8.5);
                pig[0].setNoAi(true);
                pig[0].setNoGravity(true);
                level.addFreshEntity(pig[0]);
            }
            helper.assertTrue(!islands.graph().cuts(true).isEmpty(), "no cut across the band");
            helper.assertTrue(islands.graph().loops().isEmpty(), "an island still loops");
            List<String> violations = Invariants.check(level);
            helper.assertTrue(violations.isEmpty(), "invariant violations: " + violations.subList(0, Math.min(5, violations.size())));

            pig[0].discard();
            for (int x = 0; x < N; x++) level.setChunkForced(x, z, false);
        });
    }

    @GameTest(template = TEMPLATE, batch = "alpha_omega_recenter", timeoutTicks = 400)
    public static void distantIslandsRecenter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = Wrap.canonChunk((test.getX() >> 4) + N / 2);
        int cz = Wrap.canonChunk((test.getZ() >> 4) + IslandGameTests.RECENTER_REGION);
        int laps = IslandManager.RECENTER_LAPS + 20;
        int y = level.getMaxBuildHeight() - 10;
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setNoGravity(true);
        player.moveTo((cx << 4) + 8.5 + (double) laps * W, y, (cz << 4) + 8.5);
        level.getChunkSource().move(player);
        level.setChunkForced(cx, cz, true);
        boolean[] recentered = {false};

        helper.succeedWhen(() -> {
            IslandManager islands = IslandManager.of(level);
            long chunkLaps = islands.laps(cx, cz);
            helper.assertTrue(chunkLaps != IslandGraph.ABSENT, "chunk not loaded yet");
            if (!recentered[0]) {
                helper.assertTrue(IslandGraph.lapX(chunkLaps) == laps, "island did not seed in the player's distant frame");
                islands.recenter();
                recentered[0] = true;
            }
            chunkLaps = islands.laps(cx, cz);
            helper.assertTrue(IslandGraph.lapX(chunkLaps) == 0, "island not recentered: lap " + IslandGraph.lapX(chunkLaps));
            helper.assertTrue(Math.floorDiv(player.getBlockX(), W) == 0, "player not recentered with its island: " + player.position());
            level.setChunkForced(cx, cz, false);
            level.getServer().getPlayerList().remove(player);
        });
    }

    /** Every lap of an overworld position leads to the same place in the Nether. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void portalsLinkFromCanonicalPositions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        NetherPortalBlock portal = (NetherPortalBlock) Blocks.NETHER_PORTAL;
        DimensionTransition here = portal.getPortalDestination(level, pig, pos);
        pig.setPos(pig.getX() + 3 * W, pig.getY(), pig.getZ() - W);
        DimensionTransition elsewhere = portal.getPortalDestination(level, pig, pos);
        helper.assertTrue(here != null && elsewhere != null, "no portal destination");
        helper.assertTrue(here.pos().equals(elsewhere.pos()), "laps of one position link to different places: " + here.pos() + " vs " + elsewhere.pos());
        pig.discard();
        helper.succeed();
    }
}
