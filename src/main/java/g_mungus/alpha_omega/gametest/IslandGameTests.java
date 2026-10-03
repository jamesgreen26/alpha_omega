package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.island.Invariants;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
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
    /**
     * Tests that build islands far from the test area each use their own region (a chunk offset along z), so
     * leftover chunks from one test never touch another's.
     */
    static final int SEED_REGION = N / 2;
    static final int MERGE_REGION = N / 2 + 64;
    static final int RECENTER_REGION = N / 2 + 128;
    static final int BAND_REGION = N / 2 + 192;

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
        int z = Wrap.canonBlock(test.getZ() + (SEED_REGION << 4)) - W;
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

    /**
     * Phase 4: an island with no players meeting a player's island in a different frame shifts into it. Its
     * entities move by whole laps, together with the absolute positions they remember.
     */
    @GameTest(template = TEMPLATE, batch = PLAYER_BATCH, timeoutTicks = 600)
    public static void meetingIslandsMergeByShiftingTheLighterOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = Wrap.canonChunk((test.getX() >> 4) + N / 2);
        int cz = Wrap.canonChunk((test.getZ() >> 4) + MERGE_REGION);
        int bx = Wrap.canonChunk(cx + 8);
        int y = level.getMaxBuildHeight() - 10;

        // Island B: forced before any player is near, so it seeds lap 0.
        level.setChunkForced(bx, cz, true);
        level.setChunkForced(Wrap.canonChunk(bx + 1), cz, true);
        Pig[] pig = new Pig[1];
        Villager[] villager = new Villager[1];
        BlockPos home = new BlockPos((bx << 4) + 20, y - 5, (cz << 4) + 4);
        ServerPlayer[] player = new ServerPlayer[1];
        boolean[] started = {false};

        helper.onEachTick(() -> {
            IslandManager islands = IslandManager.of(level);
            if (started[0] || islands.laps(bx, cz) == IslandGraph.ABSENT || islands.laps(bx + 1, cz) == IslandGraph.ABSENT) return;
            started[0] = true;
            helper.assertTrue(islands.laps(bx, cz) == IslandGraph.packLaps(0, 0), "island B should seed lap 0");
            pig[0] = spawnFloating(level, EntityType.PIG, (bx << 4) + 4, y, (cz << 4) + 4);
            villager[0] = spawnFloating(level, EntityType.VILLAGER, (bx << 4) + 8, y, (cz << 4) + 4);
            villager[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), home));

            // Island A: a player in lap 1, whose frame seeds a row of forced chunks reaching towards B.
            player[0] = helper.makeMockServerPlayerInLevel();
            player[0].setNoGravity(true);
            player[0].moveTo((cx << 4) + 8.5 + W, y, (cz << 4) + 8.5);
            level.getChunkSource().move(player[0]);
            for (int x = cx; x < cx + 8; x++) level.setChunkForced(Wrap.canonChunk(x), cz, true);
        });

        helper.succeedWhen(() -> {
            IslandManager islands = IslandManager.of(level);
            helper.assertTrue(started[0], "island B not loaded yet");
            int a = islands.graph().islandOf(cx, cz);
            helper.assertTrue(a != 0 && a == islands.graph().islandOf(bx, cz), "islands not merged yet");
            long b = islands.laps(bx, cz);
            helper.assertTrue(b == IslandGraph.packLaps(1, 0), "island B did not shift into the player's frame: B lap "
                + IslandGraph.lapX(b) + "," + IslandGraph.lapZ(b) + ", player " + player[0].position());
            helper.assertTrue(Math.floorDiv(player[0].getBlockX(), W) == 1, "the player should not have changed frames: " + player[0].position());
            helper.assertTrue(Math.floorDiv(pig[0].getBlockX(), W) == 1, "pig not shifted: " + pig[0].position());
            helper.assertTrue(Math.floorDiv(villager[0].getBlockX(), W) == 1, "villager not shifted: " + villager[0].position());
            GlobalPos remembered = villager[0].getBrain().getMemory(MemoryModuleType.HOME).orElseThrow();
            helper.assertTrue(remembered.pos().equals(home.offset(W, 0, 0)), "home memory not translated: " + remembered.pos());
            assertNoViolations(helper, level);

            pig[0].discard();
            villager[0].discard();
            for (int x = cx; x < cx + 10; x++) level.setChunkForced(Wrap.canonChunk(x), cz, false);
            level.getServer().getPlayerList().remove(player[0]);
        });
    }

    private static <T extends Mob> T spawnFloating(ServerLevel level, EntityType<T> type, double x, double y, double z) {
        T mob = type.create(level);
        mob.moveTo(x + 0.5, y, z + 0.5);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        level.addFreshEntity(mob);
        return mob;
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
