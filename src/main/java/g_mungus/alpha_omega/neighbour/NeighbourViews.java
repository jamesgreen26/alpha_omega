package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.mixin.server.DistanceManagerAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Players see and simulate the neighbouring faces near them (design §6.1). Each player has a virtual position on
 * every neighbouring face within view distance: where it would stand if that face's ground carried on flat past the
 * edge. There the server places the same tickets a player gets (loading to the server's view distance, ticking to
 * simulation distance) and the player's chunk tracking view covers the same square, so vanilla's square distances do the rest.
 * A neighbouring face new to the player is only asked for once the player's own view on its face has loaded. When the
 * player changes face, what it saw before stays in view: the old face becomes a neighbour in the same update, and
 * squares that drop out linger for a while ({@code transfer-retention.md}).
 */
public final class NeighbourViews {

    /** One per player and face (the value is the player's entity id), so players never share a ticket. */
    private static final TicketType<Integer> LOADING = TicketType.create("alpha_omega_neighbour", Integer::compare);
    private static final TicketType<Integer> TICKING = TicketType.create("alpha_omega_neighbour_ticking", Integer::compare);
    /** Holds a square a crossing took out of view (loaded and sent, not ticking) until it stops lingering. */
    private static final TicketType<Integer> LINGERING = TicketType.create("alpha_omega_lingering", Integer::compare);

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** A square a change of face took out of view, kept until game time {@code until}. */
    private record Lingering(CubeTrackingView.Virtual square, long until) {
    }

    private record State(ServerLevel level, int id, List<CubeTrackingView.Virtual> virtuals, List<Lingering> lingering, int loadingLevel, int tickingLevel) {

        List<CubeTrackingView.Virtual> lingeringSquares() {
            return this.lingering.stream().map(Lingering::square).toList();
        }
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

    /**
     * The tracking view for a player: vanilla's square plus its neighbour and lingering squares. When the player has
     * changed face since its last view (a crossing, a teleport), its neighbour squares move here, in the same update
     * as its own square, so nothing that stays in view drops out in between.
     */
    public static CubeTrackingView view(ServerLevel level, ServerPlayer player, ChunkPos center, int viewDistance) {
        CubeGeometry geometry = Cube.of(level);
        CubeTrackingView.Virtual was = homeSquare(geometry, player.getChunkTrackingView());
        State state = was != null && was.face() != geometry.faceAtChunk(center.x, center.z) ? update(level, geometry, player) : STATES.get(player.getUUID());
        boolean here = state != null && state.level == level;
        return new CubeTrackingView(new net.minecraft.server.level.ChunkTrackingView.Positioned(center, viewDistance),
            here ? state.virtuals : List.of(), here ? state.lingeringSquares() : List.of(), geometry);
    }

    /** The face and centre of the square a tracking view has around the player, or null if it is not a cube view. */
    @Nullable
    private static CubeTrackingView.Virtual homeSquare(CubeGeometry geometry, ChunkTrackingView view) {
        if (!(view instanceof CubeTrackingView cube)) return null;
        ChunkPos center = cube.home().center();
        CubeFace face = geometry.faceAtChunk(center.x, center.z);
        return face == null ? null : new CubeTrackingView.Virtual(face, center);
    }

    /**
     * Whether a player's own face has loaded around it: every chunk in its view square that is meant to be fully
     * loaded (its ticket level says so) has finished. Only then do new neighbouring faces load.
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

    /** The squares a player's crossings left behind that it still keeps. */
    public static List<CubeTrackingView.Virtual> lingering(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? List.of() : state.lingeringSquares();
    }

    /** Once per level tick: move each player's neighbour tickets after it, and its tracking view with them. */
    public static void tick(ServerLevel level) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return;
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (ServerPlayer player : level.players()) {
            State old = STATES.get(player.getUUID());
            if (update(level, geometry, player) == old) continue;
            ChunkTrackingView before = player.getChunkTrackingView();
            ((ChunkMapAccessor) chunkMap).alpha_omega$updateChunkTracking(player);
            recheckEntities(chunkMap, player, before, player.getChunkTrackingView());
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

    /**
     * Works out a player's neighbour and lingering squares where it is now, moves their tickets and stores them.
     * Returns the stored state itself when nothing changed.
     */
    private static State update(ServerLevel level, CubeGeometry geometry, ServerPlayer player) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        int viewDistance = ((ChunkMapAccessor) chunkMap).alpha_omega$getPlayerViewDistance(player);
        // As vanilla's player tickets: entity ticking out to the server's view distance, fading over two rings. Chunks
        // handed between vanilla's tickets and these on a crossing keep their level, so they are not sent again.
        int loadingLevel = Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - ((ChunkMapAccessor) chunkMap).alpha_omega$serverViewDistance());
        int tickingLevel = Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - level.getServer().getPlayerList().getSimulationDistance());
        long now = level.getGameTime();
        State old = STATES.get(player.getUUID());
        if (old != null && old.level != level) {
            remove(old);
            old = null;
        }
        Vec3 pos = player.position();
        CubeFace home = geometry.faceAt(pos.x, pos.z);
        boolean blind = player.isSpectator() && !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SPECTATORSGENERATECHUNKS);
        List<CubeTrackingView.Virtual> wanted = blind ? List.of() : virtuals(geometry, pos, viewDistance);

        // What the player already has: its own square, its neighbour squares and the lingering ones.
        List<CubeTrackingView.Virtual> had = new ArrayList<>();
        CubeTrackingView.Virtual oldHome = homeSquare(geometry, player.getChunkTrackingView());
        if (oldHome != null) had.add(oldHome);
        if (old != null) {
            had.addAll(old.virtuals);
            had.addAll(old.lingeringSquares());
        }

        // A neighbour square overlapping one the player has moves freely. A new one waits for the player's own face
        // to load first.
        List<CubeTrackingView.Virtual> virtuals = new ArrayList<>(wanted.size());
        Boolean loaded = null;
        for (CubeTrackingView.Virtual virtual : wanted) {
            if (had.stream().noneMatch(square -> overlap(square, virtual, viewDistance))) {
                if (loaded == null) loaded = homeLoaded(level, player, viewDistance);
                if (!loaded) continue;
            }
            virtuals.add(virtual);
        }

        // Squares a change of face took out of view linger while their face is still near: home, or a neighbour in reach.
        Set<CubeFace> near = EnumSet.noneOf(CubeFace.class);
        if (home != null) near.add(home);
        wanted.forEach(virtual -> near.add(virtual.face()));
        // Squares on the player's own face linger too: vanilla puts its own tickets on after a delay.
        java.util.function.Predicate<CubeTrackingView.Virtual> keep = square -> near.contains(square.face()) && !virtuals.contains(square);
        List<Lingering> lingering = new ArrayList<>();
        if (old != null) {
            for (Lingering square : old.lingering) {
                if (square.until > now && keep.test(square.square)) lingering.add(square);
            }
        }
        int linger = AlphaOmegaConfig.lingerTicks();
        if (linger > 0 && oldHome != null && oldHome.face() != home) {
            List<CubeTrackingView.Virtual> left = new ArrayList<>();
            left.add(oldHome);
            if (old != null) left.addAll(old.virtuals);
            for (CubeTrackingView.Virtual square : left) {
                if (keep.test(square) && lingering.stream().noneMatch(kept -> kept.square.equals(square))) lingering.add(new Lingering(square, now + linger));
            }
        }

        State state = new State(level, player.getId(), List.copyOf(virtuals), List.copyOf(lingering), loadingLevel, tickingLevel);
        if (state.equals(old)) return old;
        // New tickets go on before old ones come off, so a chunk in both never sees its level drop.
        add(state);
        if (old != null) remove(old, state);
        STATES.put(player.getUUID(), state);
        return state;
    }

    /** Whether two squares of the player's view distance, on the same face, share any chunks. */
    private static boolean overlap(CubeTrackingView.Virtual a, CubeTrackingView.Virtual b, int viewDistance) {
        return a.face() == b.face() && Math.max(Math.abs(a.center().x - b.center().x), Math.abs(a.center().z - b.center().z)) <= 2 * viewDistance;
    }

    /**
     * After a player's view changed without it moving: re-checks, for that player, only the entities in chunks that
     * came into or went out of its view. Elsewhere nothing an entity's tracking depends on has changed.
     */
    private static void recheckEntities(ChunkMap chunkMap, ServerPlayer player, ChunkTrackingView before, ChunkTrackingView after) {
        LongOpenHashSet changed = new LongOpenHashSet();
        ChunkTrackingView.difference(before, after, pos -> changed.add(pos.toLong()), pos -> changed.add(pos.toLong()));
        if (changed.isEmpty()) return;
        for (Object tracker : ((ChunkMapAccessor) chunkMap).alpha_omega$entityMap().values()) {
            TrackedEntityAccessor tracked = (TrackedEntityAccessor) tracker;
            Entity entity = tracked.alpha_omega$entity();
            if (entity != player && changed.contains(entity.chunkPosition().toLong())) tracked.alpha_omega$updatePlayer(player);
        }
    }

    private static void add(State state) {
        DistanceManager distances = state.level.getChunkSource().chunkMap.getDistanceManager();
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            distances.addTicket(LOADING, virtual.center(), state.loadingLevel, state.id);
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().addTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
        for (Lingering square : state.lingering) distances.addTicket(LINGERING, square.square.center(), state.loadingLevel, state.id);
    }

    private static void remove(State state) {
        remove(state, null);
    }

    /** Takes off a state's tickets, except those {@code next} holds too. */
    private static void remove(State state, @Nullable State next) {
        DistanceManager distances = state.level.getChunkSource().chunkMap.getDistanceManager();
        boolean sameLevels = next != null && next.level == state.level && next.loadingLevel == state.loadingLevel && next.tickingLevel == state.tickingLevel;
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            if (sameLevels && next.virtuals.contains(virtual)) continue;
            distances.removeTicket(LOADING, virtual.center(), state.loadingLevel, state.id);
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().removeTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
        for (Lingering square : state.lingering) {
            if (sameLevels && next.lingeringSquares().contains(square.square)) continue;
            distances.removeTicket(LINGERING, square.square.center(), state.loadingLevel, state.id);
        }
    }

    /**
     * Where a player is, measured from an entity on a neighbouring face: in that face's storage, its virtual position
     * (where its chunk view there is centred) or its true position, whichever is nearer the entity. Near the ground
     * the two agree; high over an edge only the true one is close to what the player can actually see.
     */
    public static Vec3 playerPositionFor(ServerLevel level, Vec3 player, Vec3 entity) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return player;
        CubeFace home = geometry.faceAt(player.x, player.z);
        CubeFace there = geometry.faceAt(entity.x, entity.z);
        if (home == null || there == null || home == there || !home.isNeighbour(there)) return player;
        double[] v = geometry.unfold(home, there, player.x, player.y, player.z);
        double[] t = geometry.transform(home, there, player.x, player.y, player.z);
        double unfolded = Mth.square(v[0] - entity.x) + Mth.square(v[2] - entity.z);
        double transformed = Mth.square(t[0] - entity.x) + Mth.square(t[2] - entity.z);
        return unfolded <= transformed ? new Vec3(v[0], v[1], v[2]) : new Vec3(t[0], t[1], t[2]);
    }

    public static void clear() {
        STATES.clear();
    }
}
