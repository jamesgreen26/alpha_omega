package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import g_mungus.alpha_omega.neighbour.CubeTrackingView;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Players see and simulate the neighbouring faces near them (design §6.1): a player near UP's east edge loads,
 * ticks and is sent EAST's chunks just over that edge, and is sent the entities there.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class NeighbourGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /** A point {@code inside} blocks inside {@code face}'s edge toward {@code toward}, over the middle of the edge. */
    private static Vec3 nearEdge(CubeGeometry geometry, CubeFace face, CubeFace toward, double inside, double y, double lateral) {
        double[] d = face.toward(toward);
        double along = geometry.radius - inside;
        return new Vec3(geometry.centerX(face) + d[0] * along + d[2] * lateral, y, geometry.centerZ() + d[2] * along + d[0] * lateral);
    }

    private static ChunkPos chunk(Vec3 pos) {
        return new ChunkPos(BlockPos.containing(pos));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void neighbourChunksLoadTickAndAreSent(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        Vec3 standing = nearEdge(geometry, CubeFace.UP, CubeFace.EAST, 4, 100, 5);
        ChunkPos across = chunk(nearEdge(geometry, CubeFace.EAST, CubeFace.UP, 8, 100, -5));
        helper.assertTrue(geometry.faceAtChunk(across.x, across.z) == CubeFace.EAST, "test chunk should be on EAST");
        ServerPlayer player = TestPlayers.mock(helper);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, standing.x, standing.y, standing.z, 0.0F, 0.0F);
        helper.succeedWhen(() -> {
            helper.assertTrue(player.getChunkTrackingView() instanceof CubeTrackingView, "player has a plain tracking view: " + player.getChunkTrackingView());
            helper.assertTrue(player.getChunkTrackingView().contains(across), "the chunk over the edge is not in the player's view");
            var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(across.toLong());
            helper.assertTrue(level.getChunkSource().getChunkNow(across.x, across.z) != null, "the chunk over the edge is not loaded: level "
                + (holder == null ? "none" : holder.getTicketLevel() + ", status " + holder.getLatestStatus()) + ", virtuals " + NeighbourViews.current(player)
                + ", home loaded " + NeighbourViews.homeLoaded(level, player, 2) + ", player at " + player.chunkPosition());
            helper.assertTrue(level.shouldTickBlocksAt(across.toLong()), "the chunk over the edge does not tick");
            level.getServer().getPlayerList().remove(player);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void neighbourEntitiesAreSent(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        Vec3 standing = nearEdge(geometry, CubeFace.UP, CubeFace.SOUTH, 4, 100, -12);
        Vec3 there = nearEdge(geometry, CubeFace.SOUTH, CubeFace.UP, 6, 100, 12);
        helper.assertTrue(geometry.faceAt(there.x, there.z) == CubeFace.SOUTH, "zombie should be on SOUTH");
        // Keep the zombie's chunk loaded until the player's neighbour view reaches it.
        TestChunks.force(level, chunk(there));
        ServerPlayer player = TestPlayers.mock(helper);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, standing.x, standing.y, standing.z, 0.0F, 0.0F);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.moveTo(there.x, there.y, there.z);
        level.addFreshEntity(zombie);
        helper.succeedWhen(() -> {
            Object tracker = ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$entityMap().get(zombie.getId());
            helper.assertTrue(tracker != null, "zombie is not tracked at all");
            helper.assertTrue(((TrackedEntityAccessor) tracker).alpha_omega$seenBy().contains(player.connection), "the player near the edge is not sent the zombie over it");
            zombie.discard();
            TestChunks.release(level, chunk(there));
            level.getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A player arriving somewhere new has chunks loaded nearest first along the cube's surface, its own face ahead: a
     * neighbour chunk is only asked for once the player's own face has loaded {@link NeighbourViews#HOME_LEAD} chunks
     * further out than it is. In the end every neighbour chunk in reach is asked for.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void neighboursLoadBehindHome(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        // Near DOWN's edge with NORTH, far from anything earlier tests generated.
        Vec3 standing = nearEdge(geometry, CubeFace.DOWN, CubeFace.NORTH, 6, 100, 70);
        ServerPlayer player = TestPlayers.mock(helper);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, standing.x, standing.y, standing.z, 0.0F, 0.0F);
        int loadDistance = ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$serverViewDistance();
        LongOpenHashSet[] before = {new LongOpenHashSet()};
        String[] early = {null};
        helper.onEachTick(() -> {
            LongOpenHashSet held = NeighbourViews.held(player);
            int homeLoaded = NeighbourViews.homeLoadedTo(player);
            for (long chunk : held) {
                if (before[0].contains(chunk) || homeLoaded >= loadDistance) continue;
                ChunkPos pos = new ChunkPos(chunk);
                // One already loaded is held whatever its distance: that is how crossings keep what they can see.
                if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) continue;
                for (CubeTrackingView.Virtual virtual : NeighbourViews.current(player)) {
                    if (geometry.faceAtChunk(pos.x, pos.z) != virtual.face()) continue;
                    int distance = Math.max(Math.abs(pos.x - virtual.center().x), Math.abs(pos.z - virtual.center().z));
                    if (distance + NeighbourViews.HOME_LEAD > homeLoaded && early[0] == null) {
                        early[0] = "neighbour chunk " + pos + " " + distance + " out was asked for with home loaded only " + homeLoaded + " out";
                    }
                }
            }
            before[0] = new LongOpenHashSet(held);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(early[0] == null, early[0]);
            helper.assertTrue(NeighbourViews.homeLoadedTo(player) >= loadDistance, "home never loaded");
            helper.assertTrue(!NeighbourViews.held(player).isEmpty(), "no neighbour chunks were asked for");
            level.getServer().getPlayerList().remove(player);
        });
    }

    /** Walking along an edge changes a strip of chunks, not the whole view. */
    @GameTest(template = TEMPLATE)
    public static void viewsChangeIncrementally(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.mock(helper);
        Vec3 a = nearEdge(geometry, CubeFace.UP, CubeFace.WEST, 10, 100, 0);
        Vec3 b = a.add(0, 0, 16);
        CubeTrackingView before = new CubeTrackingView(new ChunkTrackingView.Positioned(chunk(a), 8), NeighbourViews.virtuals(geometry, a, 8), geometry);
        CubeTrackingView after = new CubeTrackingView(new ChunkTrackingView.Positioned(chunk(b), 8), NeighbourViews.virtuals(geometry, b, 8), geometry);
        helper.assertTrue(!before.virtuals().isEmpty(), "a player 10 blocks from WEST's edge should see WEST");
        AtomicInteger size = new AtomicInteger(), added = new AtomicInteger(), removed = new AtomicInteger();
        before.forEach(pos -> size.incrementAndGet());
        ChunkTrackingView.difference(before, after, pos -> added.incrementAndGet(), pos -> removed.incrementAndGet());
        helper.assertTrue(removed.get() > 0 && removed.get() < size.get() / 4, "moving a chunk dropped " + removed + " of " + size + " chunks");
        helper.assertTrue(added.get() > 0 && added.get() < size.get() / 4, "moving a chunk added " + added + " chunks");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
