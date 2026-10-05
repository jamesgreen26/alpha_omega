package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.DistanceManagerAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Players see and simulate the neighbouring faces near them (design §6.1). Each player has a virtual position on
 * every neighbouring face within view distance: where it would stand if that face's ground carried on flat past the
 * edge. There the server places the same tickets a player gets (loading to view distance, ticking to simulation
 * distance) and the player's chunk tracking view covers the same square, so vanilla's square distances do the rest.
 * Neighbours are only asked for once the player's own view on its face has loaded.
 */
public final class NeighbourViews {

    /** One per player and face (the value is the player's entity id), so players never share a ticket. */
    private static final TicketType<Integer> LOADING = TicketType.create("alpha_omega_neighbour", Integer::compare);
    private static final TicketType<Integer> TICKING = TicketType.create("alpha_omega_neighbour_ticking", Integer::compare);

    private static final Map<UUID, State> STATES = new HashMap<>();

    private record State(ServerLevel level, int id, List<CubeTrackingView.Virtual> virtuals, int loadingLevel, int tickingLevel) {
    }

    private NeighbourViews() {
    }

    /** A player's virtual positions: one per neighbouring face whose shared edge is within its view distance. */
    public static List<CubeTrackingView.Virtual> virtuals(CubeGeometry geometry, Vec3 pos, int viewDistance) {
        CubeFace home = geometry.faceAt(pos.x, pos.z);
        if (home == null) return List.of();
        List<CubeTrackingView.Virtual> virtuals = new ArrayList<>(4);
        double[] cube = geometry.toCube(home, pos.x, pos.y, pos.z);
        for (CubeFace face : CubeFace.values()) {
            if (!home.isNeighbour(face)) continue;
            // How far short of the shared edge the player is (negative past it, in the overhang).
            double shortOfEdge = geometry.radius - cube[face.axis] * face.sign;
            if (shortOfEdge > (viewDistance + 2) * 16.0) continue;
            double[] v = geometry.unfold(home, face, pos.x, pos.y, pos.z);
            virtuals.add(new CubeTrackingView.Virtual(face, new ChunkPos((int) Math.floor(v[0]) >> 4, (int) Math.floor(v[2]) >> 4)));
        }
        return virtuals;
    }

    /** The tracking view for a player: vanilla's square plus the virtual squares it has tickets for. */
    public static CubeTrackingView view(ServerLevel level, ServerPlayer player, ChunkPos center, int viewDistance) {
        CubeGeometry geometry = Cube.of(level);
        State state = STATES.get(player.getUUID());
        List<CubeTrackingView.Virtual> virtuals = state != null && state.level == level ? state.virtuals : List.of();
        return new CubeTrackingView(new net.minecraft.server.level.ChunkTrackingView.Positioned(center, viewDistance), virtuals, geometry);
    }

    /**
     * Whether a player's own face has loaded around it: every chunk in its view square that is meant to be fully
     * loaded (its ticket level says so) has finished. Only then do the neighbouring faces load.
     */
    public static boolean homeLoaded(ServerLevel level, ServerPlayer player, int viewDistance) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        ChunkPos center = player.chunkPosition();
        for (int x = center.x - viewDistance; x <= center.x + viewDistance; x++) {
            for (int z = center.z - viewDistance; z <= center.z + viewDistance; z++) {
                net.minecraft.server.level.ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
                if (holder == null || holder.getTicketLevel() > ChunkLevel.byStatus(FullChunkStatus.FULL)) continue;
                if (!holder.getFullChunkFuture().getNow(net.minecraft.server.level.ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess()) return false;
            }
        }
        return true;
    }

    /** The virtual positions a player currently has tickets and a view for. */
    public static List<CubeTrackingView.Virtual> current(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? List.of() : state.virtuals;
    }

    /** Once per level tick: move each player's neighbour tickets after it, and its tracking view with them. */
    public static void tick(ServerLevel level) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return;
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        DistanceManager distances = chunkMap.getDistanceManager();
        int tickingLevel = Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - level.getServer().getPlayerList().getSimulationDistance());
        for (ServerPlayer player : level.players()) {
            int viewDistance = ((ChunkMapAccessor) chunkMap).alpha_omega$getPlayerViewDistance(player);
            int loadingLevel = ChunkLevel.byStatus(FullChunkStatus.FULL) - viewDistance;
            List<CubeTrackingView.Virtual> virtuals = player.isSpectator() && !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SPECTATORSGENERATECHUNKS)
                ? List.of() : virtuals(geometry, player.position(), viewDistance);
            State old = STATES.get(player.getUUID());
            // The player's own face loads first: until its view is loaded, no new neighbour chunks are asked for.
            if (!virtuals.isEmpty() && !homeLoaded(level, player, viewDistance)) {
                virtuals = old != null && old.level == level && old.virtuals.equals(virtuals) ? virtuals : List.of();
            }
            if (old != null && old.level == level && old.virtuals.equals(virtuals) && old.loadingLevel == loadingLevel && old.tickingLevel == tickingLevel) continue;
            if (old != null) remove(old);
            State state = new State(level, player.getId(), virtuals, loadingLevel, tickingLevel);
            add(state);
            STATES.put(player.getUUID(), state);
            ((ChunkMapAccessor) chunkMap).alpha_omega$updateChunkTracking(player);
            // Vanilla re-checks who sees an entity only when one of them moves: the view just changed.
            for (Object tracker : ((ChunkMapAccessor) chunkMap).alpha_omega$entityMap().values()) {
                ((g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor) tracker).alpha_omega$updatePlayer(player);
            }
        }
        for (Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            if (state.level != level) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != level) {
                remove(state);
                it.remove();
            }
        }
    }

    private static void add(State state) {
        DistanceManager distances = state.level.getChunkSource().chunkMap.getDistanceManager();
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            distances.addTicket(LOADING, virtual.center(), state.loadingLevel, state.id);
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().addTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
    }

    private static void remove(State state) {
        DistanceManager distances = state.level.getChunkSource().chunkMap.getDistanceManager();
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            distances.removeTicket(LOADING, virtual.center(), state.loadingLevel, state.id);
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().removeTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
    }

    /** Where a player is, measured from an entity on another face: its virtual position there, when neighbours. */
    public static Vec3 playerPositionFor(ServerLevel level, Vec3 player, Vec3 entity) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return player;
        CubeFace home = geometry.faceAt(player.x, player.z);
        CubeFace there = geometry.faceAt(entity.x, entity.z);
        if (home == null || there == null || home == there || !home.isNeighbour(there)) return player;
        double[] v = geometry.unfold(home, there, player.x, player.y, player.z);
        return new Vec3(v[0], v[1], v[2]);
    }

    public static void clear() {
        STATES.clear();
    }
}
