package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import g_mungus.alpha_omega.transfer.FaceTransfers;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Crossing an edge keeps what is still in view (design `transfer-retention.md`): a player who crosses, or crosses
 * and comes back, is never told to forget a chunk it ends up seeing, no such chunk unloads on the server, and an
 * entity it saw before and after stays sent to it throughout.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RetentionGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /**
     * The crossing tests run on their own: other tests' players near the same edges load chunks in this player's view
     * and leave again, and a chunk that stops ticking and starts again is sent again whoever caused it.
     */
    private static final String BATCH = "retention_crossing";
    /**
     * C2ME sends a chunk once it is loaded in full and again once its neighbours have their light (its chunk sending
     * stage), so a re-send only counts there if the chunk fell below full loading since it was last sent.
     */
    private static final boolean C2ME = ModList.get().isLoaded("c2me");
    /** Ticks watched after the (last) crossing. */
    private static final int WATCH_TICKS = 100;

    /**
     * What one player has been sent, and, once it starts crossing, what it was told to forget, what it was sent again
     * while it still had it, and which chunks unloaded on the server.
     */
    private static final class Recorder {
        final ServerPlayer player;
        final LongSet sent = new LongOpenHashSet();
        boolean crossing;
        final LongSet forgotten = new LongOpenHashSet();
        final LongSet resent = new LongOpenHashSet();
        final LongSet unloaded = new LongOpenHashSet();
        /** Per chunk sent, the highest ticket level it has had since it was last sent (checked once a tick). */
        final Long2IntMap highestSinceSent = new Long2IntOpenHashMap();

        Recorder(ServerPlayer player) {
            this.player = player;
        }
    }

    private static final List<Recorder> RECORDERS = new CopyOnWriteArrayList<>();
    private static boolean listening;

    private static synchronized void listen() {
        if (listening) return;
        listening = true;
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Watch event) -> {
            for (Recorder recorder : RECORDERS) {
                if (recorder.player != event.getPlayer()) continue;
                long chunk = event.getPos().toLong();
                boolean again = !recorder.sent.add(chunk);
                int highest = recorder.highestSinceSent.put(chunk, level(event.getLevel(), chunk));
                if (again && recorder.crossing && (!C2ME || highest > ChunkLevel.byStatus(FullChunkStatus.FULL))) recorder.resent.add(chunk);
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.UnWatch event) -> {
            for (Recorder recorder : RECORDERS) {
                if (recorder.player != event.getPlayer()) continue;
                recorder.sent.remove(event.getPos().toLong());
                recorder.highestSinceSent.remove(event.getPos().toLong());
                if (recorder.crossing) recorder.forgotten.add(event.getPos().toLong());
            }
        });
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            for (Recorder recorder : RECORDERS) {
                if (recorder.crossing && recorder.player.level() == event.getLevel()) recorder.unloaded.add(event.getChunk().getPos().toLong());
            }
        });
    }

    /** A chunk's ticket level, or past the highest for a chunk with no holder. */
    private static int level(ServerLevel level, long chunk) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk);
        return holder == null ? ChunkLevel.MAX_LEVEL + 1 : holder.getTicketLevel();
    }

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /**
     * A point of {@code from}'s storage {@code height} blocks above its face plane, {@code depth} blocks past the
     * diagonal toward {@code to} (negative: short of it), {@code lateral} blocks along the edge.
     */
    private static Vec3 crossing(CubeGeometry geometry, CubeFace from, CubeFace to, double height, double depth, double lateral) {
        double[] toward = from.toward(to);
        double along = geometry.radius + height + depth * Math.sqrt(2.0);
        return new Vec3(geometry.centerX(from) + toward[0] * along + toward[2] * lateral, geometry.planeY + height,
            geometry.centerZ() + toward[2] * along + toward[0] * lateral);
    }

    private static LongSet tracked(ServerPlayer player) {
        LongSet chunks = new LongOpenHashSet();
        player.getChunkTrackingView().forEach(pos -> chunks.add(pos.toLong()));
        return chunks;
    }

    /** Every chunk the player is meant to have in full (not just the outer ring) has been sent to it. */
    private static boolean viewSent(ServerPlayer player, Recorder recorder) {
        boolean[] sent = {true};
        player.getChunkTrackingView().forEach(pos -> {
            if (sent[0] && player.getChunkTrackingView().contains(pos.x, pos.z, false)
                && (!recorder.sent.contains(pos.toLong()) || player.connection.chunkSender.isPending(pos.toLong()))) {
                sent[0] = false;
            }
        });
        return sent[0];
    }

    /** Generates the squares a player at {@code pos} sees (its own and its virtual ones), so they load at once. */
    private static void generateView(ServerLevel level, CubeGeometry geometry, Vec3 pos) {
        int viewDistance = 3;
        List<ChunkPos> centers = new java.util.ArrayList<>();
        centers.add(new ChunkPos(net.minecraft.core.BlockPos.containing(pos)));
        NeighbourViews.virtuals(geometry, pos, viewDistance).forEach(virtual -> centers.add(virtual.center()));
        for (ChunkPos center : centers) {
            for (int x = center.x - viewDistance; x <= center.x + viewDistance; x++) {
                for (int z = center.z - viewDistance; z <= center.z + viewDistance; z++) {
                    if (geometry.inFootprint(x, z)) level.getChunk(x, z);
                }
            }
        }
    }

    private static boolean sees(ServerLevel level, ServerPlayer player, ArmorStand stand) {
        Object tracker = ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$entityMap().get(stand.getId());
        return tracker != null && ((TrackedEntityAccessor) tracker).alpha_omega$seenBy().contains(player.connection);
    }

    /**
     * Stands a player {@code height} blocks up, just past the diagonal from {@code from} toward {@code to}, waits for
     * its view (own face and neighbours) to load and for an armour stand beside it to be sent, then crosses: once, or
     * there and back. Checks nothing it still sees at the end was forgotten or unloaded on the way.
     */
    private static void cross(GameTestHelper helper, CubeFace from, CubeFace to, double height, double lateral, boolean andBack) {
        listen();
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        Vec3 start = crossing(geometry, from, to, height, FaceTransfer.MARGIN + 0.2, lateral);
        double[] t = geometry.transform(from, to, start.x, start.y, start.z);
        Vec3 arrived = new Vec3(t[0], t[1], t[2]);
        Vec3 back = crossing(geometry, from, to, height, -(FaceTransfer.MARGIN + 0.2), lateral);
        double[] bt = geometry.transform(from, to, back.x, back.y, back.z);
        Vec3 backClaim = new Vec3(bt[0], bt[1], bt[2]);
        Vec3 standAt = crossing(geometry, from, to, height, -3.0, lateral);

        // Gametest ticks run back to back, far faster than chunks generate around tickets: generate every square
        // the player will see first.
        for (Vec3 pos : List.of(start, arrived, back)) generateView(level, geometry, pos);

        ServerPlayer player = TestPlayers.mock(helper);
        Recorder recorder = new Recorder(player);
        RECORDERS.add(recorder);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, start.x, start.y, start.z, 0.0F, 0.0F);
        ArmorStand[] stand = {null};
        int[] phase = {0};
        int[] ticks = {0};
        int[] unseen = {0};
        LongSet[] before = {null};
        helper.onEachTick(() -> {
            recorder.highestSinceSent.replaceAll((chunk, highest) -> Math.max(highest, level(level, chunk)));
            switch (phase[0]) {
                case 0 -> {
                    boolean ready = NeighbourViews.current(player).stream().anyMatch(v -> v.face() == to) && viewSent(player, recorder);
                    if (ready && stand[0] == null) {
                        stand[0] = EntityType.ARMOR_STAND.create(level);
                        stand[0].setNoGravity(true);
                        stand[0].moveTo(standAt.x, standAt.y, standAt.z);
                        level.addFreshEntity(stand[0]);
                    }
                    if (ready && sees(level, player, stand[0])) {
                        before[0] = tracked(player);
                        recorder.crossing = true;
                        FaceTransfers.handleClaim(player, new FaceTransferPayload(from.slot(), to.slot(), start.x, start.y, start.z, 0.0F, 0.0F));
                        helper.assertTrue(player.position().distanceTo(arrived) < 1e-6, "the crossing was refused: player at " + player.position());
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    ticks[0]++;
                    if (!sees(level, player, stand[0])) unseen[0]++;
                    if (andBack && ticks[0] == 40) {
                        FaceTransfers.handleClaim(player, new FaceTransferPayload(to.slot(), from.slot(), backClaim.x, backClaim.y, backClaim.z, 0.0F, 0.0F));
                        helper.assertTrue(geometry.faceAt(player.getX(), player.getZ()) == from, "the crossing back was refused: player at " + player.position());
                    }
                    if (ticks[0] == (andBack ? 40 : 0) + WATCH_TICKS) {
                        phase[0] = 2;
                        RECORDERS.remove(recorder);
                        LongSet after = tracked(player);
                        LongSet churned = new LongOpenHashSet(recorder.forgotten);
                        churned.retainAll(after);
                        LongSet unloaded = new LongOpenHashSet(recorder.unloaded);
                        unloaded.retainAll(after);
                        LongSet left = new LongOpenHashSet(before[0]);
                        left.removeAll(after);
                        String counts = "view " + before[0].size() + " -> " + after.size() + ", left " + left.size() + ", forgotten " + recorder.forgotten.size()
                            + ", forgotten but still in view " + churned.size() + ", sent again " + recorder.resent.size() + ", unloaded but still in view "
                            + unloaded.size() + ", ticks the stand was unsent " + unseen[0];
                        AlphaOmegaMod.LOGGER.info("Retention {} -> {} at h {}{}: {}", from, to, height, andBack ? " and back" : "", counts);
                        stand[0].discard();
                        level.getServer().getPlayerList().remove(player);
                        if (!churned.isEmpty() || !recorder.resent.isEmpty() || !unloaded.isEmpty() || unseen[0] > 0) {
                            helper.fail("crossing lost what stayed in view: " + counts);
                        } else {
                            helper.succeed();
                        }
                    }
                }
                default -> {
                }
            }
        });
    }

    /** At ground level the old face stays in view almost unchanged: crossing forgets only what the geometry moves. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 6000)
    public static void crossingKeepsView(GameTestHelper helper) {
        cross(helper, CubeFace.UP, CubeFace.EAST, 4.0, -60.0, false);
    }

    /** High up, the old face's view jumps on crossing; crossing straight back must still find it all there. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 6000)
    public static void crossingBackKeepsView(GameTestHelper helper) {
        cross(helper, CubeFace.UP, CubeFace.SOUTH, 60.0, 50.0, true);
    }
}
