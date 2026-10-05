package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.DistanceManagerAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * Players see and simulate the images of the tile near them ({@code orbifold-implementation.md} phase 6, "image
 * views"). Each player has an image position {@code g⁻¹(pos)}, in the same storage, for every image {@code g} its view
 * needs ({@link ImageGeometry#images}: at most four). There the player's chunk tracking view covers the same square as
 * at home, kept to live storage (tile and band, {@link #shows}); the server keeps those chunks ticking to simulation
 * distance, and loads them with the same per-chunk tickets vanilla gives a player's own chunks, out to the server's
 * view distance.
 *
 * <p>Chunks load nearest first, with the player's own square {@link #HOME_LEAD} chunks ahead: an image chunk {@code d}
 * chunks from the player's image position is asked for once every chunk of the player's own square within
 * {@code d + HOME_LEAD} has loaded (or straight away if it is loaded already). Once asked for, it stays asked for
 * while it is in view.
 *
 * <p>Squares that leave the view all at once linger for a while ({@code transfer-retention.md} §3.3): an image the
 * player no longer needs, and any square whose centre jumps (a crossing by an element of {@code Γ}, or a teleport),
 * including the player's own. They stay tracked and loaded, but not ticking, for {@link AlphaOmegaConfig#lingerTicks}
 * while the player stays within {@link #LINGER_REACH} chunks of them at one of its image positions, so walking to and
 * fro where an image starts, or crossing and coming back, costs nothing. A crossing by {@code h} needs no lingering
 * of its own: the square the player left is the image square of {@code h} from where it arrives, so it hands over in
 * the same update ({@link #view}).
 */
public final class ImageViews {

    /** How many chunks further out the player's own square loads than its images. */
    public static final int HOME_LEAD = 2;
    /** A square whose centre moves further than this (chunks, Chebyshev) between updates has jumped: what it leaves lingers. */
    public static final int JUMP = 2;
    /** A lingering square ends once the player is further than this (chunks) from its centre at every image position. */
    public static final int LINGER_REACH = 4;
    /** Vanilla's level for a player's own chunks: entity ticking, fading to unloaded over two rings beyond. */
    private static final int LOADING_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    /** Per chunk and player (the value is the player's entity id), so players never share a ticket. */
    private static final TicketType<Integer> LOADING = TicketType.create("alpha_omega_image", Integer::compare);
    private static final TicketType<Integer> TICKING = TicketType.create("alpha_omega_image_ticking", Integer::compare);

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** A square that left the view all at once, kept until game time {@code until}. */
    private record Lingering(ImageTrackingView.Virtual square, long until) {
    }

    /**
     * What a player has: the chunk it was in, its image and lingering squares (its view), the chunks there it holds
     * loading tickets for, and how far around it its own square has loaded (in chunks, -1 for not even its own).
     */
    private record State(ServerLevel level, int id, ChunkPos home, List<ImageTrackingView.Virtual> virtuals, List<Lingering> lingering,
                         LongOpenHashSet held, int tickingLevel, int homeLoaded) {

        List<ImageTrackingView.Virtual> lingeringSquares() {
            return this.lingering.stream().map(Lingering::square).toList();
        }
    }

    private ImageViews() {
    }

    /** Whether image views run in a level: in the overworld of an orbifold world. */
    public static boolean active(ServerLevel level) {
        return Orbifold.of(level) != null;
    }

    /** A player's image positions, standing in chunk {@code center}: one per image its view needs, in a fixed order. */
    public static List<ImageTrackingView.Virtual> virtuals(OrbifoldGeometry geometry, ChunkPos center, int viewDistance) {
        List<Motion> images = ImageGeometry.images(geometry, center.x, center.z, viewDistance);
        if (images.isEmpty()) return List.of();
        List<ImageTrackingView.Virtual> virtuals = new ArrayList<>(images.size());
        for (Motion g : images) {
            Motion back = g.inverse();
            virtuals.add(new ImageTrackingView.Virtual(g, new ChunkPos(back.chunkX(center.x), back.chunkZ(center.z))));
        }
        return virtuals;
    }

    /** As {@link #virtuals(OrbifoldGeometry, ChunkPos, int)}, for a point. */
    public static List<ImageTrackingView.Virtual> virtuals(OrbifoldGeometry geometry, Vec3 pos, int viewDistance) {
        return virtuals(geometry, new ChunkPos(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4), viewDistance);
    }

    /**
     * Whether an image's (or lingering) square holds a chunk: live storage, the tile and band. Tile chunks are what
     * images draw (and the band around them what meshing reads); band chunks hold entities standing past a seam, which
     * are physically in the tile on the far side.
     */
    public static boolean shows(OrbifoldGeometry geometry, ImageTrackingView.Virtual square, int chunkX, int chunkZ) {
        return ImageGeometry.live(geometry, chunkX, chunkZ);
    }

    private static boolean jumped(ChunkPos from, ChunkPos to) {
        return Math.max(Math.abs(from.x - to.x), Math.abs(from.z - to.z)) > JUMP;
    }

    /**
     * The tracking view for a player: vanilla's square plus its image and lingering squares. When the player's own
     * square has jumped since its images were worked out (a crossing, a teleport), they are worked out again here, in
     * the same update as its own square, so nothing that stays in view drops out in between.
     */
    public static ImageTrackingView view(ServerLevel level, ServerPlayer player, ChunkPos center, int viewDistance) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        State state = STATES.get(player.getUUID());
        if (state != null && state.level == level && jumped(state.home, center)) state = update(level, geometry, player);
        boolean here = state != null && state.level == level;
        return new ImageTrackingView(new ChunkTrackingView.Positioned(center, viewDistance),
            here ? state.virtuals : List.of(), here ? state.lingeringSquares() : List.of(), geometry);
    }

    /**
     * Whether a player's own square has loaded around it: every chunk in its view square that is meant to be fully
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

    /** The image positions a player currently has tickets and a view for. */
    public static List<ImageTrackingView.Virtual> current(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? List.of() : state.virtuals;
    }

    /** The image chunks a player holds loading tickets for. */
    public static LongOpenHashSet held(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? new LongOpenHashSet() : state.held;
    }

    /** How far around a player (in chunks) its own square had loaded when its image chunks were last asked for. */
    public static int homeLoadedTo(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? -1 : state.homeLoaded;
    }

    /** The squares that left a player's view all at once that it still keeps. */
    public static List<ImageTrackingView.Virtual> lingering(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? List.of() : state.lingeringSquares();
    }

    /** Once per level tick: move each player's image tickets after it, and its tracking view with them. */
    public static void tick(ServerLevel level) {
        if (!active(level)) return;
        OrbifoldGeometry geometry = Orbifold.of(level);
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
     * Whether a lingering square is still worth keeping: it is not one of the player's squares again, and the player
     * is within {@link #LINGER_REACH} chunks of its centre at its own position or one of its image positions.
     */
    private static boolean keep(OrbifoldGeometry geometry, ImageTrackingView.Virtual square, ChunkPos home, List<ImageTrackingView.Virtual> virtuals) {
        if (virtuals.contains(square) || square.image().isIdentity() && square.center().equals(home)) return false;
        ChunkPos center = square.center();
        if (near(center, home)) return true;
        for (Motion k : ImageGeometry.candidates(geometry)) {
            if (near(center, new ChunkPos(k.chunkX(home.x), k.chunkZ(home.z)))) return true;
        }
        return false;
    }

    private static boolean near(ChunkPos a, ChunkPos b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z)) <= LINGER_REACH;
    }

    /**
     * Works out a player's image and lingering squares where it is now, moves their tickets and stores them. Returns
     * the stored state itself when nothing changed.
     */
    private static State update(ServerLevel level, OrbifoldGeometry geometry, ServerPlayer player) {
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
        ChunkPos home = player.chunkPosition();
        boolean blind = player.isSpectator() && !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SPECTATORSGENERATECHUNKS);
        List<ImageTrackingView.Virtual> virtuals = blind ? List.of() : virtuals(geometry, home, viewDistance);

        // Lingering squares stay while their time lasts and the player is near them.
        List<Lingering> lingering = new ArrayList<>();
        if (old != null) {
            for (Lingering square : old.lingering) {
                if (square.until > now && keep(geometry, square.square, home, virtuals)) lingering.add(square);
            }
        }
        // Squares that left the view all at once start lingering: the player's own if it jumped, and each image's
        // that is no longer needed or whose centre jumped. A square that only moved along with the player does not.
        int linger = AlphaOmegaConfig.lingerTicks();
        if (old != null && linger > 0) {
            List<ImageTrackingView.Virtual> left = new ArrayList<>();
            if (jumped(old.home, home)) left.add(new ImageTrackingView.Virtual(Motion.IDENTITY, old.home));
            for (ImageTrackingView.Virtual square : old.virtuals) {
                ImageTrackingView.Virtual current = virtuals.stream().filter(v -> v.image().equals(square.image())).findFirst().orElse(null);
                if (current == null || jumped(square.center(), current.center())) left.add(square);
            }
            for (ImageTrackingView.Virtual square : left) {
                if (keep(geometry, square, home, virtuals) && lingering.stream().noneMatch(kept -> kept.square.equals(square))) {
                    lingering.add(new Lingering(square, now + linger));
                }
            }
        }

        // Which chunks to hold, nearest first: image chunks once the player's own square has loaded HOME_LEAD
        // further out (or already loaded, or already held); lingering chunks while they stay loaded.
        LongOpenHashSet before = old == null ? new LongOpenHashSet() : old.held;
        int homeLoaded = homeLoadedTo(chunkMap, home, loadDistance);
        boolean homeDone = homeLoaded >= loadDistance;
        LongOpenHashSet held = new LongOpenHashSet();
        LongArrayList added = new LongArrayList();
        for (int ring = 0; ring <= loadDistance; ring++) {
            boolean allowed = homeDone || ring + HOME_LEAD <= homeLoaded;
            for (ImageTrackingView.Virtual virtual : virtuals) {
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

        State state = new State(level, player.getId(), home, List.copyOf(virtuals), List.copyOf(lingering), held, tickingLevel, homeLoaded);
        if (old != null && old.home.equals(home) && old.virtuals.equals(state.virtuals) && old.lingering.equals(state.lingering)
            && old.held.equals(held) && old.tickingLevel == tickingLevel) {
            // Nothing a player can see changed; just remember how far home has loaded.
            if (old.homeLoaded != homeLoaded) {
                state = new State(level, old.id, old.home, old.virtuals, old.lingering, old.held, old.tickingLevel, homeLoaded);
                STATES.put(player.getUUID(), state);
            }
            return old;
        }
        // New tickets go on (nearest first) before old ones come off, so a chunk in both never sees its level drop.
        DistanceManager distances = chunkMap.getDistanceManager();
        for (int i = 0; i < added.size(); i++) distances.addTicket(LOADING, new ChunkPos(added.getLong(i)), LOADING_LEVEL, state.id);
        for (ImageTrackingView.Virtual virtual : state.virtuals) {
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().addTicket(TICKING, virtual.center(), tickingLevel, state.id);
        }
        if (old != null) remove(old, state);
        STATES.put(player.getUUID(), state);
        return state;
    }

    /** Calls {@code action} for each chunk of a square's ring (Chebyshev distance {@code ring}) that it holds. */
    private static void forRing(OrbifoldGeometry geometry, ImageTrackingView.Virtual square, int ring, LongConsumer action) {
        ring(square.center(), ring, chunk -> {
            if (shows(geometry, square, ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) action.accept(chunk);
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
        for (ImageTrackingView.Virtual virtual : state.virtuals) {
            if (sameLevel && next.tickingLevel == state.tickingLevel && next.virtuals.contains(virtual)) continue;
            ((DistanceManagerAccessor) distances).alpha_omega$tickingTracker().removeTicket(TICKING, virtual.center(), state.tickingLevel, state.id);
        }
    }

    /**
     * How soon a chunk should be sent to a player, smaller first: its squared distance in chunks from the player; a
     * chunk only an image square holds counts from the player's image position there, {@link #HOME_LEAD} further.
     */
    public static double sendPriority(ServerPlayer player, long chunk) {
        int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
        ChunkPos own = player.chunkPosition();
        double home = Mth.square(x - own.x) + Mth.square(z - own.z);
        if (!(player.getChunkTrackingView() instanceof ImageTrackingView view) || view.home().contains(x, z, true)) return home;
        List<ImageTrackingView.Virtual> squares = new ArrayList<>(view.virtuals());
        squares.addAll(view.lingering());
        for (ImageTrackingView.Virtual square : squares) {
            ChunkPos center = square.center();
            if (!ChunkTrackingView.isWithinDistance(center.x, center.z, view.viewDistance(), x, z, true)) continue;
            return Mth.square(Math.sqrt(Mth.square(x - center.x) + Mth.square(z - center.z)) + HOME_LEAD);
        }
        return home;
    }

    /**
     * Where a player is, measured from an entity ({@code TrackedEntityMixin}): the nearest to it of the player's real
     * position and its images under every element near the tile. That is the distance between the two in the world
     * itself (for distances well under the tile's size). Whether the entity's chunk is in the player's view is checked
     * separately, as in vanilla.
     */
    public static Vec3 playerPositionFor(ServerLevel level, Vec3 player, Vec3 entity) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return player;
        Vec3 best = player;
        double bestDistance = Mth.square(player.x - entity.x) + Mth.square(player.z - entity.z);
        for (Motion k : ImageGeometry.candidates(geometry)) {
            double x = k.pointX(player.x), z = k.pointZ(player.z);
            double distance = Mth.square(x - entity.x) + Mth.square(z - entity.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Vec3(x, player.y, z);
            }
        }
        return best;
    }

    public static void clear() {
        STATES.clear();
    }
}
