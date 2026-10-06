package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import g_mungus.alpha_omega.neighbour.ImageViews;
import g_mungus.alpha_omega.network.FrameTransferPayload;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.transfer.FrameTransfer;
import g_mungus.alpha_omega.transfer.FrameTransfers;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ClientInformation;
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
 * Crossing a seam keeps what is still in view ({@code transfer-retention.md}, adapted from v2 to frames): a player who
 * crosses, or crosses and comes back, is never told to forget a chunk it ends up seeing, no such chunk unloads on the
 * server, and an entity it saw before and after stays sent to it throughout.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class RetentionGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /**
     * The crossing tests run on their own: other tests' players near the same seams load chunks in this player's view
     * and leave again, and a chunk that stops ticking and starts again is sent again whoever caused it.
     */
    private static final String BATCH = "retention_crossing";
    private static final boolean C2ME = ModList.get().isLoaded("c2me");
    /** Ticks watched after the (last) crossing. */
    private static final int WATCH_TICKS = 100;
    /** The view distance the mock players ask for. */
    private static final int VIEW = 4;

    private static final class Recorder {
        final ServerPlayer player;
        final LongSet sent = new LongOpenHashSet();
        boolean crossing;
        final LongSet forgotten = new LongOpenHashSet();
        final LongSet resent = new LongOpenHashSet();
        final LongSet unloaded = new LongOpenHashSet();
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

    private static int level(ServerLevel level, long chunk) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk);
        return holder == null ? ChunkLevel.MAX_LEVEL + 1 : holder.getTicketLevel();
    }

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    private static LongSet tracked(ServerPlayer player) {
        LongSet chunks = new LongOpenHashSet();
        player.getChunkTrackingView().forEach(pos -> chunks.add(pos.toLong()));
        return chunks;
    }

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

    /** Generates the squares a player at {@code pos} sees (its own and its image squares), so they load at once. */
    private static void generateView(ServerLevel level, OrbifoldGeometry geometry, Vec3 pos, int viewDistance) {
        List<ChunkPos> centers = new ArrayList<>();
        centers.add(new ChunkPos(BlockPos.containing(pos)));
        ImageViews.virtuals(geometry, pos, viewDistance).forEach(virtual -> centers.add(virtual.center()));
        for (ChunkPos center : centers) {
            for (int x = center.x - viewDistance - 1; x <= center.x + viewDistance + 1; x++) {
                for (int z = center.z - viewDistance - 1; z <= center.z + viewDistance + 1; z++) {
                    if (geometry.inFootprintChunk(x, z)) level.getChunk(x, z);
                }
            }
        }
    }

    private static boolean sees(ServerLevel level, ServerPlayer player, ArmorStand stand) {
        Object tracker = ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$entityMap().get(stand.getId());
        return tracker != null && ((TrackedEntityAccessor) tracker).alpha_omega$seenBy().contains(player.connection);
    }

    private static void claim(ServerPlayer player, Motion g, Vec3 at, int number) {
        FrameTransfers.handleClaim(player, new FrameTransferPayload(g, at.x, at.y, at.z, player.getYRot(), player.getXRot(), number,
            FrameTransfers.serverTransfers(player)));
    }

    /**
     * Stands a player at {@code start}, just past the claim depth, waits for its view to load and an armour stand
     * beside it to be sent, then crosses by claim; with {@code andBack}, the server moves it straight back 40 ticks later. Checks nothing it still sees at the end was
     * forgotten or unloaded on the way, and the stand stayed sent.
     */
    private static void cross(GameTestHelper helper, Vec3 start, Vec3 standAt, boolean andBack) {
        listen();
        OrbifoldGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        Motion g = FrameTransfer.destination(geometry, start.x, start.z, FrameTransfer.playerDepth(geometry));
        if (g == null) {
            helper.fail("the start " + start + " does not cross");
            return;
        }
        Vec3 arrived = Transform.of(g).position(start);
        for (Vec3 pos : List.of(start, arrived)) generateView(level, geometry, pos, VIEW);
        ServerPlayer player = TestPlayers.mock(helper);
        ClientInformation defaults = ClientInformation.createDefault();
        player.updateOptions(new ClientInformation(defaults.language(), VIEW, defaults.chatVisibility(), defaults.chatColors(),
            defaults.modelCustomisation(), defaults.mainHand(), defaults.textFilteringEnabled(), defaults.allowsListing()));
        Recorder recorder = new Recorder(player);
        RECORDERS.add(recorder);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, start.x, start.y, start.z, 0.0F, 0.0F);
        ArmorStand[] stand = {null};
        int[] phase = {0};
        int[] ticks = {0};
        int[] unseen = {0};
        int[] waited = {0};
        LongSet[] before = {null};
        helper.onEachTick(() -> {
            recorder.highestSinceSent.replaceAll((chunk, highest) -> Math.max(highest, level(level, chunk)));
            switch (phase[0]) {
                case 0 -> {
                    waited[0]++;
                    boolean ready = !ImageViews.current(player).isEmpty() && viewSent(player, recorder);
                    if (ready && stand[0] == null) {
                        stand[0] = EntityType.ARMOR_STAND.create(level);
                        stand[0].setNoGravity(true);
                        stand[0].moveTo(standAt.x, standAt.y, standAt.z);
                        level.addFreshEntity(stand[0]);
                    }
                    if (ready && sees(level, player, stand[0])) {
                        before[0] = tracked(player);
                        recorder.crossing = true;
                        claim(player, g, start, 1);
                        helper.assertTrue(player.position().distanceTo(arrived) < 1e-6, "the crossing was refused: player at " + player.position());
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    ticks[0]++;
                    if (!sees(level, player, stand[0])) unseen[0]++;
                    if (andBack && ticks[0] == 40) {
                        // Straight back, as the server moves a player into its group's frame or a block owner's: by
                        // the inverse element, told to the client, no teleport.
                        FrameTransfers.transfer(level, player, g.inverse(), null);
                        helper.assertTrue(player.position().distanceTo(start) < 1e-6, "the transfer back left the player at " + player.position());
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
                        AlphaOmegaMod.LOGGER.info("Retention from {} by {}{}: {}", start, g, andBack ? " and back" : "", counts);
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

    /** Crossing the east seam keeps everything still in view. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 6000)
    public static void crossingKeepsView(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        double depth = FrameTransfer.playerDepth(g) + 0.2;
        cross(helper, new Vec3(g.maxX + depth, 120.0, -3100.5), new Vec3(g.maxX + depth - 5.0, 120.0, -3098.5), false);
    }

    /** Crossing the north fold and straight back again keeps everything still in view. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 6000)
    public static void crossingBackKeepsView(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        double depth = FrameTransfer.playerDepth(g) + 0.2;
        cross(helper, new Vec3(1500.5, 160.0, g.northRow - depth), new Vec3(1498.5, 160.0, g.northRow - depth + 5.0), true);
    }
}
