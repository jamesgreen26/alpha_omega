package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.DistanceManagerAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
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
 * edge. There the player's chunk tracking view covers the same square as at home, the server keeps those chunks
 * ticking to simulation distance, and it loads them with the same per-chunk tickets vanilla gives a player's own
 * chunks, out to the server's view distance.
 *
 * <p>Chunks load nearest first, measured along the cube's surface, with the player's own face {@link #HOME_LEAD}
 * chunks ahead: a neighbour chunk {@code d} chunks from the player's virtual position is asked for once every chunk of
 * the player's own face within {@code d + HOME_LEAD} has loaded (or straight away if it is loaded already). Once asked
 * for, it stays asked for while it is in view. When the player changes face, what it saw before stays in view: the old
 * face becomes a neighbour in the same update, and squares that drop out linger for a while
 * ({@code transfer-retention.md}).
 */
public final class NeighbourViews {

    /** How many chunks further out the player's own face loads than its neighbours. */
    public static final int HOME_LEAD = 2;
    /** Vanilla's level for a player's own chunks: entity ticking, fading to unloaded over two rings beyond. */
    private static final int LOADING_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    /** Per chunk and player (the value is the player's entity id), so players never share a ticket. */
    private static final TicketType<Integer> LOADING = TicketType.create("alpha_omega_neighbour", Integer::compare);
    private static final TicketType<Integer> TICKING = TicketType.create("alpha_omega_neighbour_ticking", Integer::compare);

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** A square a change of face took out of view, kept until game time {@code until}. */
    private record Lingering(CubeTrackingView.Virtual square, long until) {
    }

    /**
     * What a player has: its neighbour and lingering squares (its view), the chunks there it holds loading tickets
     * for, and how far around it its own face has loaded (in chunks, -1 for not even its own).
     */
    private record State(ServerLevel level, int id, List<CubeTrackingView.Virtual> virtuals, List<Lingering> lingering, LongOpenHashSet held,
                         int tickingLevel, int homeLoaded) {

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
     * loaded (its ticket level says so) has finished.
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

    /** The neighbour chunks a player holds loading tickets for. */
    public static LongOpenHashSet held(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? new LongOpenHashSet() : state.held;
    }

    /** How far around a player (in chunks) its own face had loaded when its neighbour chunks were last asked for. */
    public static int homeLoadedTo(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? -1 : state.homeLoaded;
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
        int loadDistance = ((ChunkMapAccessor) chunkMap).alpha_omega$serverViewDistance();
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
        List<CubeTrackingView.Virtual> virtuals = blind ? List.of() : virtuals(geometry, pos, viewDistance);
        CubeTrackingView.Virtual oldHome = homeSquare(geometry, player.getChunkTrackingView());

        // Squares a change of face took out of view linger while their face is still near: home, or a neighbour in
        // reach. Squares on the player's own face linger too: vanilla puts its own tickets on after a delay.
        Set<CubeFace> near = EnumSet.noneOf(CubeFace.class);
        if (home != null) near.add(home);
        virtuals.forEach(virtual -> near.add(virtual.face()));
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

        // Which chunks to hold, nearest first: neighbour chunks once the player's own face has loaded HOME_LEAD
        // further out (or already loaded, or already held); lingering chunks while they stay loaded.
        LongOpenHashSet before = old == null ? new LongOpenHashSet() : old.held;
        int homeLoaded = homeLoadedTo(chunkMap, player.chunkPosition(), loadDistance);
        boolean homeDone = homeLoaded >= loadDistance;
        LongOpenHashSet held = new LongOpenHashSet();
        LongArrayList added = new LongArrayList();
        for (int ring = 0; ring <= loadDistance; ring++) {
            boolean allowed = homeDone || ring + HOME_LEAD <= homeLoaded;
            for (CubeTrackingView.Virtual virtual : virtuals) {
                forRing(geometry, virtual, ring, chunk -> {
                    if (before.contains(chunk) || allowed || loaded(chunkMap, chunk)) {
                        if (held.add(chunk) && !before.contains(chunk)) added.add(chunk);
                    }
                });
            }
        }
        for (Lingering square : lingering) {
            for (int ring = 0; ring <= loadDistance; ring++) {
                forRing(geometry, square.square, ring, chunk -> {
                    if ((before.contains(chunk) || loaded(chunkMap, chunk)) && held.add(chunk) && !before.contains(chunk)) added.add(chunk);
                });
            }
        }

        State state = new State(level, player.getId(), List.copyOf(virtuals), List.copyOf(lingering), held, tickingLevel, homeLoaded);
        if (old != null && old.virtuals.equals(state.virtuals) && old.lingering.equals(state.lingering) && old.held.equals(held)
            && old.tickingLevel == tickingLevel) {
            // Nothing a player can see changed; just remember how far home has loaded.
            if (old.homeLoaded != homeLoaded) {
                state = new State(level, old.id, old.virtuals, old.lingering, old.held, old.tickingLevel, homeLoaded);
                STATES.put(player.getUUID(), state);
            }
            return old;
        }
        // New tickets go on (nearest first) before old ones come off, so a chunk in both never sees its level drop.
        DistanceManager distances = chunkMap.getDistanceManager();
        for (int i = 0; i < added.size(); i++) distances.addTicket(LOADING, new ChunkPos(added.getLong(i)), LOADING_LEVEL, state.id);
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().addTicket(TICKING, virtual.center(), tickingLevel, state.id);
        }
        if (old != null) remove(old, state);
        STATES.put(player.getUUID(), state);
        return state;
    }

    /** Calls {@code action} for each chunk of a square's ring (Chebyshev distance {@code ring}) on its own face. */
    private static void forRing(CubeGeometry geometry, CubeTrackingView.Virtual square, int ring, LongConsumer action) {
        ring(square.center(), ring, chunk -> {
            int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
            if (geometry.faceAtChunk(x, z) == square.face() && geometry.inFootprint(x, z)) action.accept(chunk);
        });
    }

    /** Calls {@code action} for each chunk at Chebyshev distance {@code ring} from {@code center}. */
    private static void ring(ChunkPos center, int ring, LongConsumer action) {
        if (ring == 0) {
            action.accept(center.toLong());
            return;
        }
        for (int d = -ring; d <= ring; d++) {
            action.accept(ChunkPos.asLong(center.x + d, center.z - ring));
            action.accept(ChunkPos.asLong(center.x + d, center.z + ring));
        }
        for (int d = -ring + 1; d < ring; d++) {
            action.accept(ChunkPos.asLong(center.x - ring, center.z + d));
            action.accept(ChunkPos.asLong(center.x + ring, center.z + d));
        }
    }

    /** Whether a chunk has finished loading. */
    private static boolean loaded(ChunkMap chunkMap, long chunk) {
        net.minecraft.server.level.ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunk);
        return holder != null && holder.getFullChunkFuture().getNow(net.minecraft.server.level.ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess();
    }

    /** How many rings around {@code center} have entirely loaded, up to {@code max}; -1 if not even the centre. */
    private static int homeLoadedTo(ChunkMap chunkMap, ChunkPos center, int max) {
        boolean[] whole = {true};
        for (int r = 0; r <= max; r++) {
            ring(center, r, chunk -> whole[0] &= loaded(chunkMap, chunk));
            if (!whole[0]) return r - 1;
        }
        return max;
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

    private static void remove(State state) {
        remove(state, null);
    }

    /** Takes off a state's tickets, except those {@code next} holds too. */
    private static void remove(State state, @Nullable State next) {
        DistanceManager distances = state.level.getChunkSource().chunkMap.getDistanceManager();
        boolean sameLevel = next != null && next.level == state.level;
        LongIterator chunks = state.held.iterator();
        while (chunks.hasNext()) {
            long chunk = chunks.nextLong();
            if (sameLevel && next.held.contains(chunk)) continue;
            distances.removeTicket(LOADING, new ChunkPos(chunk), LOADING_LEVEL, state.id);
        }
        for (CubeTrackingView.Virtual virtual : state.virtuals) {
            if (sameLevel && next.tickingLevel == state.tickingLevel && next.virtuals.contains(virtual)) continue;
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().removeTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
    }

    /**
     * How soon a chunk should be sent to a player, smaller first: its squared distance in chunks from the player, along
     * the cube's surface; a neighbour chunk's is from the player's virtual position there, {@link #HOME_LEAD} further.
     */
    public static double sendPriority(ServerPlayer player, long chunk) {
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        ChunkPos own = player.chunkPosition();
        CubeGeometry geometry = Cube.of(player.level());
        CubeFace face = geometry == null ? null : geometry.faceAtChunk(x, z);
        if (face == null || face == geometry.faceAtChunk(own.x, own.z)) return Mth.square(x - own.x) + Mth.square(z - own.z);
        State state = STATES.get(player.getUUID());
        if (state != null) {
            for (CubeTrackingView.Virtual square : state.virtuals) {
                if (square.face() == face) return Mth.square(Math.sqrt(Mth.square(x - square.center().x) + Mth.square(z - square.center().z)) + HOME_LEAD);
            }
            for (Lingering square : state.lingering) {
                ChunkPos center = square.square.center();
                if (square.square.face() == face) return Mth.square(Math.sqrt(Mth.square(x - center.x) + Mth.square(z - center.z)) + HOME_LEAD);
            }
        }
        return Double.MAX_VALUE;
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
