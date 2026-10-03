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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 3: islands, lift tables, entities entering frames, frame-tagged saves. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class IslandGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final String PLAYER_BATCH = "alpha_omega_islands";
    private static final int W = Wrap.PERIOD;
    private static final int N = Wrap.CHUNK_PERIOD;

    @GameTest(template = TEMPLATE)
    public static void loadedChunksAreInConsistentIslands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.assertTrue(IslandManager.of(level).laps(pos.getX() >> 4, pos.getZ() >> 4) != IslandGraph.ABSENT, "test chunk is not in an island");
        helper.succeedWhen(() -> assertNoViolations(helper, level));
    }

    /** An entity added at another image of its position is brought into its island's frame. */
    @GameTest(template = TEMPLATE)
    public static void entitiesEnterTheirIslandsFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(pos.getX() + 0.5 + 3 * W, pos.getY(), pos.getZ() + 0.5 - W);
        level.addFreshEntity(pig);
        helper.assertTrue(inFrame(level, pig), "pig not moved into its island's frame: " + pig.position());
        helper.assertTrue(Wrap.canon(pig.blockPosition()).equals(Wrap.canon(pos)), "pig changed canonical position");
        pig.discard();
        helper.succeed();
    }

    /** Teleporting to another image of a position lands in the island's frame by the end of the tick. */
    @GameTest(template = TEMPLATE)
    public static void teleportsLandInFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        pig.setNoAi(true);
        pig.teleportTo(pig.getX() - 2 * W, pig.getY(), pig.getZ() + W);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(inFrame(level, pig), "pig not back in frame after teleport: " + pig.position());
            pig.discard();
            helper.succeed();
        });
    }

    /**
     * A player away from loaded terrain seeds new chunks in its own frame (§5.8); an entity saved there is
     * written with a canonical Pos and a lap tag, and comes back into the same frame.
     */
    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH, timeoutTicks = 400)
    public static void playersSeedFramesAndSavesRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        // Half a world from the test, two laps up: nothing is loaded there yet.
        int x = Wrap.canonBlock(test.getX() + W / 2) + 2 * W;
        int z = Wrap.canonBlock(test.getZ() + W / 2) - W;
        int y = level.getMaxBuildHeight() - 10;
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(x + 0.5, y, z + 0.5);
        player.setNoGravity(true);
        player.getAbilities().flying = true;
        // Mock players are not ticked by a connection, so update their chunk tickets by hand.
        level.getChunkSource().move(player);

        helper.succeedWhen(() -> {
            long laps = IslandManager.of(level).laps(x >> 4, z >> 4);
            helper.assertTrue(laps != IslandGraph.ABSENT, "chunks around the player not loaded yet");
            helper.assertTrue(IslandGraph.lapX(laps) == 2 && IslandGraph.lapZ(laps) == -1,
                "seeded lap " + IslandGraph.lapX(laps) + "," + IslandGraph.lapZ(laps) + " is not the player's frame");
            helper.assertTrue(inFrame(level, player), "player not in its island's frame");

            // An entity spawned at the canonical position joins the player's frame.
            Pig pig = EntityType.PIG.create(level);
            pig.moveTo(Wrap.canonBlock(x) + 2.5, y, Wrap.canonBlock(z) + 0.5);
            pig.setNoGravity(true);
            level.addFreshEntity(pig);
            helper.assertTrue(pig.getX() > 2 * W && pig.getZ() < 0, "pig not lifted into the player's frame: " + pig.position());

            CompoundTag saved = new CompoundTag();
            pig.save(saved);
            double savedX = saved.getList("Pos", Tag.TAG_DOUBLE).getDouble(0);
            helper.assertTrue(savedX >= 0 && savedX < W, "saved Pos is not canonical: " + savedX);
            int[] tag = saved.getIntArray("alpha_omega:Lap");
            helper.assertTrue(tag.length == 2 && tag[0] == 2 && tag[1] == -1, "lap tag missing or wrong");
            pig.discard();

            Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
            helper.assertTrue(loaded != null && loaded.getX() > 2 * W && loaded.getZ() < 0, "loaded entity not restored to its frame: " + (loaded == null ? null : loaded.position()) + " from " + saved.get("Pos"));
            level.addFreshEntity(loaded);
            helper.assertTrue(inFrame(level, loaded), "loaded entity not in frame");
            loaded.discard();

            assertNoViolations(helper, level);
            level.getServer().getPlayerList().remove(player);
        });
    }

    private static boolean inFrame(ServerLevel level, Entity entity) {
        long laps = IslandManager.of(level).laps(entity.chunkPosition().x, entity.chunkPosition().z);
        return laps == IslandGraph.ABSENT
            || (Math.floorDiv(entity.chunkPosition().x, N) == IslandGraph.lapX(laps) && Math.floorDiv(entity.chunkPosition().z, N) == IslandGraph.lapZ(laps));
    }

    private static void assertNoViolations(GameTestHelper helper, ServerLevel level) {
        List<String> violations = Invariants.check(level);
        helper.assertTrue(violations.isEmpty(), "invariant violations: " + violations.subList(0, Math.min(5, violations.size())));
    }
}
