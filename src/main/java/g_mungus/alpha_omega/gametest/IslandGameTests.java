package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import g_mungus.alpha_omega.island.Invariants;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 3: islands, lift tables, entities entering frames, frame-tagged saves. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class IslandGameTests {

    private static Wrap wrap() {
        return Wraps.overworld();
    }

    private static int period() {
        return wrap().period;
    }

    private static int chunks() {
        return wrap().chunkPeriod;
    }

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final String PLAYER_BATCH = "alpha_omega_islands";
    /**
     * Tests that build islands far from the test area each use their own region (a chunk offset along z), so
     * leftover chunks from one test never touch another's. Regions sit a quarter of the world from the test area,
     * which is near spawn: far from it and from the seam, which is half a world from spawn.
     */
    static int seedRegion() {
        return chunks() / 4;
    }

    static int mergeRegion() {
        return chunks() / 4 + 32;
    }

    static int recenterRegion() {
        return chunks() / 4 + 64;
    }

    static int bandRegion() {
        return chunks() / 4 + 96;
    }

    static int idleRegion() {
        return chunks() / 4 + 128;
    }

    static int loginRegion() {
        return -chunks() / 4 - 64;
    }

    /** A chunk column a quarter of the world from the test area along x. */
    static int farChunkX(BlockPos test) {
        return wrap().canonChunk((test.getX() >> 4) + chunks() / 4);
    }

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
        pig.moveTo(pos.getX() + 0.5 + 3 * period(), pos.getY(), pos.getZ() + 0.5 - period());
        level.addFreshEntity(pig);
        helper.assertTrue(inFrame(level, pig), "pig not moved into its island's frame: " + pig.position());
        helper.assertTrue(wrap().canon(pig.blockPosition()).equals(wrap().canon(pos)), "pig changed canonical position");
        pig.discard();
        helper.succeed();
    }

    /** Teleporting to another image of a position lands in the island's frame by the end of the tick. */
    @GameTest(template = TEMPLATE)
    public static void teleportsLandInFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        pig.setNoAi(true);
        pig.teleportTo(pig.getX() - 2 * period(), pig.getY(), pig.getZ() + period());
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
        // A quarter of the world from the test, two laps up: nothing is loaded there yet.
        int x = wrap().canonBlock(test.getX() + period() / 4) + 2 * period();
        int z = wrap().canonBlock(test.getZ() + (seedRegion() << 4)) - period();
        int y = level.getMaxBuildHeight() - 10;
        ServerPlayer player = TestPlayers.mock(helper);
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
            pig.moveTo(wrap().canonBlock(x) + 2.5, y, wrap().canonBlock(z) + 0.5);
            pig.setNoGravity(true);
            level.addFreshEntity(pig);
            helper.assertTrue(wrap().lap(pig.getBlockX()) == 2 && wrap().lap(pig.getBlockZ()) == -1, "pig not lifted into the player's frame: " + pig.position());

            CompoundTag saved = new CompoundTag();
            pig.save(saved);
            double savedX = saved.getList("Pos", Tag.TAG_DOUBLE).getDouble(0);
            helper.assertTrue(wrap().canon(savedX) == savedX, "saved Pos is not canonical: " + savedX);
            int[] tag = saved.getIntArray("alpha_omega:Lap");
            helper.assertTrue(tag.length == 2 && tag[0] == 2 && tag[1] == -1, "lap tag missing or wrong");
            pig.discard();

            Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
            helper.assertTrue(loaded != null && wrap().lap(loaded.getBlockX()) == 2 && wrap().lap(loaded.getBlockZ()) == -1, "loaded entity not restored to its frame: " + (loaded == null ? null : loaded.position()) + " from " + saved.get("Pos"));
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
        int cx = farChunkX(test);
        int cz = wrap().canonChunk((test.getZ() >> 4) + mergeRegion());
        int bx = wrap().canonChunk(cx + 8);
        int y = level.getMaxBuildHeight() - 10;

        // Island B: forced before any player is near, so it seeds lap 0.
        level.setChunkForced(bx, cz, true);
        level.setChunkForced(wrap().canonChunk(bx + 1), cz, true);
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
            player[0] = TestPlayers.mock(helper);
            player[0].setNoGravity(true);
            player[0].moveTo((cx << 4) + 8.5 + period(), y, (cz << 4) + 8.5);
            level.getChunkSource().move(player[0]);
            for (int x = cx; x < cx + 8; x++) level.setChunkForced(wrap().canonChunk(x), cz, true);
        });

        helper.succeedWhen(() -> {
            IslandManager islands = IslandManager.of(level);
            helper.assertTrue(started[0], "island B not loaded yet");
            int a = islands.graph().islandOf(cx, cz);
            helper.assertTrue(a != 0 && a == islands.graph().islandOf(bx, cz), "islands not merged yet");
            long b = islands.laps(bx, cz);
            helper.assertTrue(b == IslandGraph.packLaps(1, 0), "island B did not shift into the player's frame: B lap "
                + IslandGraph.lapX(b) + "," + IslandGraph.lapZ(b) + ", player " + player[0].position());
            helper.assertTrue(wrap().lap(player[0].getBlockX()) == 1, "the player should not have changed frames: " + player[0].position());
            helper.assertTrue(wrap().lap(pig[0].getBlockX()) == 1, "pig not shifted: " + pig[0].position());
            helper.assertTrue(wrap().lap(villager[0].getBlockX()) == 1, "villager not shifted: " + villager[0].position());
            GlobalPos remembered = villager[0].getBrain().getMemory(MemoryModuleType.HOME).orElseThrow();
            helper.assertTrue(remembered.pos().equals(home.offset(period(), 0, 0)), "home memory not translated: " + remembered.pos());
            assertNoViolations(helper, level);

            pig[0].discard();
            villager[0].discard();
            for (int x = cx; x < cx + 10; x++) level.setChunkForced(wrap().canonChunk(x), cz, false);
            level.getServer().getPlayerList().remove(player[0]);
        });
    }

    /**
     * A player who logged out in lap 2 comes back in lap 0 (mod-compatibility §3.2): their client frame starts
     * fresh, so their position is restored canonically, and the chunks around them seed lap 0.
     */
    @GameTest(template = TEMPLATE, batch = "alpha_omega_login", timeoutTicks = 1200)
    public static void returningPlayersLoadInLapZero(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = farChunkX(test);
        int cz = wrap().canonChunk((test.getZ() >> 4) + loginRegion());
        int y = level.getMaxBuildHeight() - 10;
        double x = (cx << 4) + 8.5;
        double z = (cz << 4) + 8.5;

        // Save a player standing two laps east of there, without adding it to the level.
        UUID id = UUID.randomUUID();
        ServerPlayer leaving = new ServerPlayer(level.getServer(), level, new GameProfile(id, "test-leaving-player"), ClientInformation.createDefault());
        leaving.setPos(x + 2 * period(), y, z);
        CompoundTag saved = NbtUtils.addCurrentDataVersion(leaving.saveWithoutId(new CompoundTag()));
        int[] lapTag = saved.getIntArray("alpha_omega:Lap");
        helper.assertTrue(lapTag.length == 2 && lapTag[0] == 2, "saved player has no lap tag");
        Path data = level.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Files.createDirectories(data);
        NbtIo.writeCompressed(saved, data.resolve(id + ".dat"));

        ServerPlayer player = TestPlayers.mock(helper, id);
        player.setNoGravity(true);
        helper.assertTrue(player.getX() == x && player.getZ() == z, "player did not load at its canonical position: " + player.position());
        level.getChunkSource().move(player);

        helper.succeedWhen(() -> {
            long laps = IslandManager.of(level).laps(cx, cz);
            helper.assertTrue(laps != IslandGraph.ABSENT, "chunks around the player not loaded yet");
            helper.assertTrue(laps == IslandGraph.packLaps(0, 0), "chunks seeded lap " + IslandGraph.lapX(laps) + "," + IslandGraph.lapZ(laps));
            helper.assertTrue(wrap().lap(player.getBlockX()) == 0 && inFrame(level, player), "player not in lap 0: " + player.position());
            level.getServer().getPlayerList().remove(player);
        });
    }

    /**
     * An island no player is near shifts into lap 0 with its entities (mod-compatibility §3.3), while an island
     * with a player keeps the player's frame.
     */
    @GameTest(template = TEMPLATE, batch = "alpha_omega_idle", timeoutTicks = 600)
    public static void idleIslandsMoveToLapZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos test = helper.absolutePos(BlockPos.ZERO);
        int cx = farChunkX(test);
        int cz = wrap().canonChunk((test.getZ() >> 4) + idleRegion());
        int y = level.getMaxBuildHeight() - 10;
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        player.moveTo((cx << 4) + 8.5 + 3 * period(), y, (cz << 4) + 8.5 - period());
        level.getChunkSource().move(player);
        level.setChunkForced(cx, cz, true);
        Pig[] pig = {null};
        boolean[] left = {false};

        helper.succeedWhen(() -> {
            IslandManager islands = IslandManager.of(level);
            long laps = islands.laps(cx, cz);
            helper.assertTrue(laps != IslandGraph.ABSENT, "chunk not loaded yet");
            if (pig[0] == null) {
                helper.assertTrue(laps == IslandGraph.packLaps(3, -1), "island did not seed in the player's frame");
                islands.recenter();
                helper.assertTrue(islands.laps(cx, cz) == laps, "an island with a player must keep its frame");
                pig[0] = spawnFloating(level, EntityType.PIG, (cx << 4) + 4, y, (cz << 4) + 4);
                helper.assertTrue(wrap().lap(pig[0].getBlockX()) == 3, "pig not in the player's frame");
            }
            if (!left[0]) {
                level.getServer().getPlayerList().remove(player);
                left[0] = true;
            }
            islands.recenter();
            long after = islands.laps(cx, cz);
            helper.assertTrue(after == IslandGraph.packLaps(0, 0), "idle island not in lap 0: " + IslandGraph.lapX(after) + "," + IslandGraph.lapZ(after));
            helper.assertTrue(wrap().lap(pig[0].getBlockX()) == 0 && wrap().lap(pig[0].getBlockZ()) == 0, "pig not moved with its island: " + pig[0].position());
            assertNoViolations(helper, level);
            pig[0].discard();
            level.setChunkForced(cx, cz, false);
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
            || (wrap().chunkLap(entity.chunkPosition().x) == IslandGraph.lapX(laps) && wrap().chunkLap(entity.chunkPosition().z) == IslandGraph.lapZ(laps));
    }

    private static void assertNoViolations(GameTestHelper helper, ServerLevel level) {
        List<String> violations = Invariants.check(level);
        helper.assertTrue(violations.isEmpty(), "invariant violations: " + violations.subList(0, Math.min(5, violations.size())));
    }
}
