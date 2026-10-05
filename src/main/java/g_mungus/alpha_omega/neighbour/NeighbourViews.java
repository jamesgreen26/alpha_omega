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

    /** A player's virtual positions: one per neighbouring face whose footprint is within its view distance. */
    public static List<CubeTrackingView.Virtual> virtuals(CubeGeometry geometry, Vec3 pos, int viewDistance) {
        CubeFace home = geometry.faceAt(pos.x, pos.z);
        if (home == null) return List.of();
        List<CubeTrackingView.Virtual> virtuals = new ArrayList<>(4);
        int reach = Math.ceilDiv(geometry.footprint, 16) + 1;
        for (CubeFace face : CubeFace.values()) {
            if (!home.isNeighbour(face)) continue;
            double[] v = geometry.unfold(home, face, pos.x, pos.y, pos.z);
            ChunkPos center = new ChunkPos((int) Math.floor(v[0]) >> 4, (int) Math.floor(v[2]) >> 4);
            int dx = Math.max(0, Math.abs(center.x - (geometry.centerX(face) >> 4)) - reach);
            int dz = Math.max(0, Math.abs(center.z - (geometry.centerZ() >> 4)) - reach);
            if (Math.max(dx, dz) <= viewDistance + 1) virtuals.add(new CubeTrackingView.Virtual(face, center));
        }
        return virtuals;
    }

    /** The tracking view for a player: vanilla's square plus its virtual squares. */
    public static CubeTrackingView view(ServerLevel level, ServerPlayer player, ChunkPos center, int viewDistance) {
        CubeGeometry geometry = Cube.of(level);
        return new CubeTrackingView(new net.minecraft.server.level.ChunkTrackingView.Positioned(center, viewDistance),
            List.copyOf(virtuals(geometry, player.position(), viewDistance)), geometry);
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
            if (old != null && old.level == level && old.virtuals.equals(virtuals) && old.loadingLevel == loadingLevel && old.tickingLevel == tickingLevel) continue;
            if (old != null) remove(old);
            State state = new State(level, player.getId(), virtuals, loadingLevel, tickingLevel);
            add(state);
            STATES.put(player.getUUID(), state);
            ((ChunkMapAccessor) chunkMap).alpha_omega$updateChunkTracking(player);
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
