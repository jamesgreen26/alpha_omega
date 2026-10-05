package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.mixin.band.TicketAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Paired tickets (RS §3.7): a ticket near the band is repeated at its images under the group, so that a band chunk's
 * source is at least as loaded as the band chunk, and a tile chunk's copies as loaded as the tile chunk. Each ticket
 * placed directly (by players, forcing, commands, other mods) is mirrored by every motion that takes some linked chunk
 * within its reach to its copy; the image ticket has the same level, so the chunk levels match exactly (the motions are
 * isometries). Image tickets are never mirrored again, so pairs cannot hold each other loaded.
 *
 * <p>The loading tracker and the simulation (ticking) tracker are both mirrored. One {@link State} per level.
 */
public final class BandTickets {

    /** An image ticket; the key is unique per image, so images of different tickets never merge. */
    public static final TicketType<Long> PAIRED = TicketType.create("alpha_omega_band_pair", Long::compare);

    private static long nextKey;

    private BandTickets() {
    }

    /** A ticket as vanilla compares them: type, level and key, at a chunk. */
    private record Id(boolean ticking, long chunk, TicketType<?> type, int level, @Nullable Object key) {
    }

    private record Image(long chunk, int level, long key) {
    }

    /** A level's mirrored tickets. Main thread. */
    public static final class State {
        final ServerLevel level;
        final Map<Id, Image[]> images = new HashMap<>();
        /** Mirrored tickets that time out (vanilla drops those without {@code removeTicket}). */
        final Set<Id> expiring = new HashSet<>();
        int count;

        public State(ServerLevel level) {
            this.level = level;
        }
    }

    private static boolean ours(TicketType<?> type) {
        return type == PAIRED || type == BandGate.GATE;
    }

    /** How far from a ticket chunks are full (loading) or block ticking (simulation): the reach that matters. */
    private static int reach(boolean ticking, int level) {
        int limit = ticking ? ChunkLevel.byStatus(FullChunkStatus.BLOCK_TICKING) : ChunkLevel.byStatus(FullChunkStatus.FULL);
        return limit - level;
    }

    /** The motions taking chunks within {@code reach} of {@code (cx, cz)} to their copies. */
    private static List<Motion> motions(OrbifoldGeometry geometry, int cx, int cz, int reach) {
        int[] footprint = geometry.footprintChunks();
        if (cx + reach < footprint[0] || cz + reach < footprint[1] || cx - reach > footprint[2] || cz - reach > footprint[3]) return List.of();
        int edge = (geometry.reach + 15) >> 4;
        int minX = (geometry.minX >> 4) + edge, maxX = (geometry.maxX >> 4) - 1 - edge;
        int minZ = (geometry.northRow >> 4) + edge, maxZ = (geometry.southRow >> 4) - 1 - edge;
        if (cx - reach > minX && cx + reach < maxX && cz - reach > minZ && cz + reach < maxZ) return List.of();
        List<Motion> motions = new ArrayList<>(2);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                if (!geometry.inFootprintChunk(x, z)) continue;
                if (geometry.isTileChunk(x, z)) {
                    for (OrbifoldGeometry.Cell copy : geometry.copiesChunk(x, z)) {
                        Motion toCopy = copy.frame().inverse();
                        if (!motions.contains(toCopy)) motions.add(toCopy);
                    }
                } else {
                    Motion toSource = geometry.frameChunk(x, z);
                    if (!motions.contains(toSource)) motions.add(toSource);
                }
            }
        }
        return motions;
    }

    /** After vanilla added {@code ticket} at {@code chunk} to the loading tracker ({@code ticking} false) or the simulation one. */
    public static void added(@Nullable State state, Object tracker, boolean ticking, long chunk, Ticket<?> ticket) {
        if (state == null || ours(ticket.getType())) return;
        OrbifoldGeometry geometry = Band.geometry(state.level);
        if (geometry == null) return;
        int reach = reach(ticking, ticket.getTicketLevel());
        if (reach < 0) return;
        Id id = new Id(ticking, chunk, ticket.getType(), ticket.getTicketLevel(), ((TicketAccessor) (Object) ticket).alpha_omega$key());
        if (state.images.containsKey(id)) return;
        List<Motion> motions = motions(geometry, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), reach);
        if (motions.isEmpty()) return;
        Image[] images = new Image[motions.size()];
        for (int i = 0; i < images.length; i++) {
            Motion motion = motions.get(i);
            Image image = new Image(ChunkPos.asLong(motion.chunkX(ChunkPos.getX(chunk)), motion.chunkZ(ChunkPos.getZ(chunk))), ticket.getTicketLevel(), ++nextKey);
            images[i] = image;
            add(tracker, ticking, image);
        }
        state.images.put(id, images);
        if (ticket.getType().timeout() != 0) state.expiring.add(id);
        state.count += images.length;
        BandCounters.pairedTicketsAdded += images.length;
    }

    /** After vanilla removed {@code ticket} at {@code chunk}. */
    public static void removed(@Nullable State state, Object tracker, boolean ticking, long chunk, Ticket<?> ticket) {
        if (state == null || ours(ticket.getType()) || state.images.isEmpty()) return;
        Id id = new Id(ticking, chunk, ticket.getType(), ticket.getTicketLevel(), ((TicketAccessor) (Object) ticket).alpha_omega$key());
        drop(state, tracker, id);
    }

    private static void drop(State state, Object tracker, Id id) {
        Image[] images = state.images.remove(id);
        if (images == null) return;
        state.expiring.remove(id);
        for (Image image : images) remove(tracker, id.ticking, image);
        state.count -= images.length;
    }

    /**
     * After vanilla dropped timed-out tickets from the loading tracker (it does so without {@code removeTicket}): drops
     * their images.
     */
    public static void purged(@Nullable State state, DistanceManager manager, Function<Long, SortedArraySet<Ticket<?>>> tickets) {
        if (state == null || state.expiring.isEmpty()) return;
        List<Id> gone = new ArrayList<>();
        for (Id id : state.expiring) {
            if (id.ticking) continue;
            SortedArraySet<Ticket<?>> present = tickets.apply(id.chunk);
            if (present == null || !contains(present, id)) gone.add(id);
        }
        for (Id id : gone) drop(state, manager, id);
    }

    private static boolean contains(SortedArraySet<Ticket<?>> tickets, Id id) {
        for (Ticket<?> ticket : tickets) {
            if (ticket.getType() == id.type && ticket.getTicketLevel() == id.level
                && Objects.equals(((TicketAccessor) (Object) ticket).alpha_omega$key(), id.key)) return true;
        }
        return false;
    }

    private static void add(Object tracker, boolean ticking, Image image) {
        ChunkPos pos = new ChunkPos(image.chunk);
        if (ticking) ((TickingTracker) tracker).addTicket(PAIRED, pos, image.level, image.key);
        else ((DistanceManager) tracker).addTicket(PAIRED, pos, image.level, image.key);
    }

    private static void remove(Object tracker, boolean ticking, Image image) {
        ChunkPos pos = new ChunkPos(image.chunk);
        if (ticking) ((TickingTracker) tracker).removeTicket(PAIRED, pos, image.level, image.key);
        else ((DistanceManager) tracker).removeTicket(PAIRED, pos, image.level, image.key);
    }

    public static int count(@Nullable State state) {
        return state == null ? 0 : state.count;
    }

    /** For tests: the images held for tickets at {@code chunk}, as chunk positions. */
    public static List<ChunkPos> imagesAt(@Nullable State state, long chunk) {
        List<ChunkPos> result = new ArrayList<>();
        if (state == null) return result;
        for (Iterator<Map.Entry<Id, Image[]>> it = state.images.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Id, Image[]> entry = it.next();
            if (entry.getKey().chunk != chunk || entry.getKey().ticking) continue;
            for (Image image : entry.getValue()) result.add(new ChunkPos(image.chunk));
        }
        return result;
    }
}
