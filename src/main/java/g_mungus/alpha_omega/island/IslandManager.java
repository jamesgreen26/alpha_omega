package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.mixin.server.entity.PersistentEntitySectionManagerAccessor;
import g_mungus.alpha_omega.mixin.server.ServerLevelAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySectionStorage;

/**
 * The islands of one {@link ServerLevel} (design doc §5): which loaded chunks form connected regions, and the lap
 * each chunk is lifted by. Fed by chunk load and unload; keeps entities in their island's frame (I4).
 */
public final class IslandManager {

    /** Duck interface on {@code ServerLevel}. */
    public interface Holder {
        IslandManager alpha_omega$islands();
    }

    private final ServerLevel level;
    private final Wrap wrap;
    /** Null for a level that does not wrap: the manager is then inert. */
    private final IslandGraph graph;
    /** Entities whose frame must be checked at the end of the tick (they moved into another section or lap). */
    private final ReferenceLinkedOpenHashSet<Entity> frameChecks = new ReferenceLinkedOpenHashSet<>();
    /** Chunks that joined this tick, whose entities must be brought into the chunk's frame. */
    private final LongLinkedOpenHashSet joinedChunks = new LongLinkedOpenHashSet();

    public IslandManager(ServerLevel level) {
        this.level = level;
        this.wrap = Wrap.of(level);
        this.graph = this.wrap.enabled() ? new IslandGraph(this.wrap.chunkPeriod, this.wrap.minChunk) : null;
    }

    public static IslandManager of(ServerLevel level) {
        return ((Holder) level).alpha_omega$islands();
    }

    public IslandGraph graph() {
        return this.graph;
    }

    /** The lap pair of a chunk (any image), or {@link IslandGraph#ABSENT} if it is not loaded or off the torus. */
    public long laps(int chunkX, int chunkZ) {
        if (this.graph == null || Wrap.offTorusChunk(chunkX, chunkZ)) return IslandGraph.ABSENT;
        return this.graph.laps(this.wrap.canonChunk(chunkX), this.wrap.canonChunk(chunkZ));
    }

    // ---- chunk lifecycle (server thread) ----

    /** Off-torus chunks (other mods' far-away storage) never join an island. */
    public void onChunkLoaded(ChunkPos pos) {
        if (this.graph == null || Wrap.offTorusChunk(pos.x, pos.z)) return;
        int x = this.wrap.canonChunk(pos.x);
        int z = this.wrap.canonChunk(pos.z);
        this.graph.join(x, z, this::seedLaps, this::playerWeight);
        this.joinedChunks.add(IslandGraph.key(x, z));
    }

    public void onChunkUnloaded(ChunkPos pos) {
        if (this.graph == null || Wrap.offTorusChunk(pos.x, pos.z)) return;
        this.graph.leave(this.wrap.canonChunk(pos.x), this.wrap.canonChunk(pos.z));
    }

    // ---- entities ----

    public void queueFrameCheck(Entity entity) {
        if (this.graph == null) return;
        this.frameChecks.add(entity);
    }

    /** At most this many merges per tick; any left over continue next tick. */
    private static final int MAX_MERGES_PER_TICK = 64;
    /** How often cuts are re-evaluated and islands recentered. */
    private static final int MAINTENANCE_INTERVAL = 100;
    /** How far, in blocks, an island may drift from the origin before it is shifted back. */
    private static int recenterDistance = 5_000_000;

    /** Keeps islands closer to the origin, for mods with less coordinate precision (Sable's f32 physics). */
    public static void limitRecenterDistance(int blocks) {
        recenterDistance = Math.min(recenterDistance, blocks);
    }

    /** Islands drifting more than this many laps (about 5,000,000 blocks by default) from the origin are shifted back (§5.9). */
    public int recenterLaps() {
        return Math.max(1, recenterDistance / this.wrap.period);
    }

    /** End of the server tick: lazy splits, merges (shifting the lighter island), then entity frame checks. */
    public void tick() {
        if (this.graph == null) return;
        this.graph.processSplits();
        this.resolveMerges();
        this.resolveLoops();
        if (this.level.getGameTime() % MAINTENANCE_INTERVAL == 0) {
            this.maintainCuts();
            this.recenter();
        }

        if (!this.joinedChunks.isEmpty()) {
            EntitySectionStorage<Entity> sections = this.sections();
            for (long key : this.joinedChunks) {
                sections.getExistingSectionsInChunk(ChunkPos.asLong(IslandGraph.keyX(key), IslandGraph.keyZ(key)))
                    .forEach(section -> section.getEntities().forEach(this.frameChecks::add));
            }
            this.joinedChunks.clear();
        }

        while (!this.frameChecks.isEmpty()) {
            Entity entity = this.frameChecks.removeFirst();
            if (!entity.isRemoved() && entity.level() == this.level) EntityFrames.reframe(entity);
        }
        FrameParticipants.checkFrames(this.level);
    }

    // ---- merges and shifts (§6.5) ----

    private void resolveMerges() {
        if (!this.graph.takeDisagreementFlag()) return;
        for (int i = 0; i < MAX_MERGES_PER_TICK; i++) {
            List<IslandGraph.PendingMerge> merges = this.graph.disagreements();
            if (merges.isEmpty()) return;
            IslandGraph.PendingMerge merge = merges.get(0);
            // The island with fewer players (then fewer chunks) moves, so the shift touches the fewest entities.
            if (this.lighter(merge.from(), merge.into())) {
                this.shift(merge.from(), merge.lapDX(), merge.lapDZ());
                this.graph.merge(merge.from(), merge.into());
            } else {
                this.shift(merge.into(), -merge.lapDX(), -merge.lapDZ());
                this.graph.merge(merge.into(), merge.from());
            }
        }
        this.graph.markDisagreementPending();
    }

    private boolean lighter(int a, int b) {
        long pa = this.playerWeight(a);
        long pb = this.playerWeight(b);
        if (pa != pb) return pa < pb;
        return this.graph.island(a).size() <= this.graph.island(b).size();
    }

    /**
     * Translates a whole island by whole laps: its lift table, and every entity in it with all the absolute
     * positions they hold. Block-side state is canonical and needs nothing; clients see nothing (R6).
     */
    public void shift(int island, int lapDX, int lapDZ) {
        if (this.graph == null) return;
        IslandGraph.Island target = this.graph.island(island);
        if (target == null || (lapDX == 0 && lapDZ == 0)) return;
        ReferenceLinkedOpenHashSet<Entity> roots = new ReferenceLinkedOpenHashSet<>();
        EntitySectionStorage<Entity> sections = this.sections();
        for (long key : target.chunks()) {
            sections.getExistingSectionsInChunk(ChunkPos.asLong(IslandGraph.keyX(key), IslandGraph.keyZ(key)))
                .forEach(section -> section.getEntities().forEach(entity -> roots.add(entity.getRootVehicle())));
        }
        this.graph.shift(island, lapDX, lapDZ);
        double dx = (double) lapDX * this.wrap.period;
        double dz = (double) lapDZ * this.wrap.period;
        for (Entity root : roots) EntityFrames.translate(root, dx, dz);
        Long2LongOpenHashMap changes = new Long2LongOpenHashMap(target.chunks().size());
        long laps = IslandGraph.packLaps(lapDX, lapDZ);
        for (long key : target.chunks()) changes.put(key, laps);
        FrameParticipants.translate(this.level, changes);
    }

    private EntitySectionStorage<Entity> sections() {
        return ((PersistentEntitySectionManagerAccessor) ((ServerLevelAccessor) this.level).alpha_omega$getEntityManager()).alpha_omega$getSectionStorage();
    }

    // ---- cuts (§5.7) ----

    /** Every island about to wrap all the way around the world gets a cut, placed where it disturbs least. */
    private void resolveLoops() {
        for (int guard = 0; guard < 4 && !this.graph.loops().isEmpty(); guard++) {
            int island = this.graph.loops().iterator().nextInt();
            boolean xAxis = this.graph.wrapsAlongX(island);
            this.graph.addCut(xAxis, this.quietestBoundary(xAxis));
            this.applyLapChanges(this.graph.relayout(this::anchor));
        }
    }

    /** Cuts move away from approaching players, and disappear once nothing is loaded across them. */
    private void maintainCuts() {
        for (boolean xAxis : new boolean[] {true, false}) {
            for (int boundary : this.graph.cuts(xAxis).toIntArray()) {
                if (!this.graph.cutInUse(xAxis, boundary)) {
                    this.graph.removeCut(xAxis, boundary);
                } else if (this.playerNear(xAxis, boundary)) {
                    int moved = this.quietestBoundary(xAxis);
                    if (moved == boundary) continue;
                    this.graph.removeCut(xAxis, boundary);
                    this.graph.addCut(xAxis, moved);
                    this.applyLapChanges(this.graph.relayout(this::anchor));
                }
            }
        }
    }

    /** The boundary with no player within view distance and the fewest entities around it. */
    private int quietestBoundary(boolean xAxis) {
        int n = this.wrap.chunkPeriod;
        int min = this.wrap.minChunk;
        // Indexed by canonical chunk minus the window's first chunk.
        long[] entities = new long[n];
        for (Entity entity : this.level.getAllEntities()) {
            int chunk = xAxis ? entity.chunkPosition().x : entity.chunkPosition().z;
            if (!Wrap.offTorusChunk(chunk)) entities[this.wrap.canonChunk(chunk) - min]++;
        }
        FrameParticipants.weighColumns(this.level, xAxis, entities);
        int best = min;
        long bestScore = Long.MAX_VALUE;
        for (int boundary = min; boundary < min + n; boundary++) {
            long score = this.playerNear(xAxis, boundary) ? 1L << 40 : 0;
            for (int d = -2; d <= 1; d++) score += entities[this.wrap.canonChunk(boundary + d) - min];
            if (score < bestScore) {
                bestScore = score;
                best = boundary;
            }
        }
        return best;
    }

    private boolean playerNear(boolean xAxis, int boundary) {
        int range = this.level.getServer().getPlayerList().getViewDistance() + 2;
        for (ServerPlayer player : this.level.players()) {
            int chunk = xAxis ? player.chunkPosition().x : player.chunkPosition().z;
            if (Math.abs(this.wrap.minChunkDelta(chunk, boundary)) <= range || Math.abs(this.wrap.minChunkDelta(chunk, boundary - 1)) <= range) return true;
        }
        return false;
    }

    /** When a component is re-lifted, a chunk with a player keeps its lift, so players never jump frames. */
    private long anchor(LongOpenHashSet component) {
        for (ServerPlayer player : this.level.players()) {
            long key = IslandGraph.key(this.wrap.canonChunk(player.chunkPosition().x), this.wrap.canonChunk(player.chunkPosition().z));
            if (component.contains(key)) return key;
        }
        return component.iterator().nextLong();
    }

    /** Moves the entities of every re-lifted chunk by its lap change. */
    private void applyLapChanges(Long2LongOpenHashMap changes) {
        if (changes.isEmpty()) return;
        EntitySectionStorage<Entity> sections = this.sections();
        Reference2LongOpenHashMap<Entity> roots = new Reference2LongOpenHashMap<>();
        for (Long2LongMap.Entry change : changes.long2LongEntrySet()) {
            long key = change.getLongKey();
            sections.getExistingSectionsInChunk(ChunkPos.asLong(IslandGraph.keyX(key), IslandGraph.keyZ(key)))
                .forEach(section -> section.getEntities().forEach(entity -> roots.putIfAbsent(entity.getRootVehicle(), change.getLongValue())));
        }
        for (Reference2LongMap.Entry<Entity> entry : roots.reference2LongEntrySet()) {
            long laps = entry.getLongValue();
            EntityFrames.translate(entry.getKey(), (double) IslandGraph.lapX(laps) * this.wrap.period, (double) IslandGraph.lapZ(laps) * this.wrap.period);
        }
        FrameParticipants.translate(this.level, changes);
    }

    // ---- recentering (§5.9) ----

    /**
     * Islands that have drifted many laps from the origin shift back, keeping coordinates far from the limits.
     * Islands far from every player go further: they shift so that most of their chunks are in lap 0, where
     * canonical and lifted positions coincide, so other mods' coordinate math works there (mod-compatibility §3.3).
     * No client can see them, so the shift is free; islands with players keep their frame, which is the client's.
     */
    public void recenter() {
        if (this.graph == null) return;
        int limit = this.recenterLaps();
        for (IslandGraph.Island island : List.copyOf(this.graph.islands())) {
            if (this.graph.island(island.id) == null) continue;
            if (!this.nearPlayer(island)) {
                int lapDX = -this.dominantLap(island, true);
                int lapDZ = -this.dominantLap(island, false);
                if (lapDX != 0 || lapDZ != 0) {
                    this.shift(island.id, lapDX, lapDZ);
                    // It may now agree with an island it touches; agreeing islands must merge.
                    this.graph.markDisagreementPending();
                }
                continue;
            }
            long key = island.chunks().iterator().nextLong();
            long laps = this.graph.laps(IslandGraph.keyX(key), IslandGraph.keyZ(key));
            int lx = IslandGraph.lapX(laps);
            int lz = IslandGraph.lapZ(laps);
            if (Math.abs(lx) > limit || Math.abs(lz) > limit) {
                this.shift(island.id, Math.abs(lx) > limit ? -lx : 0, Math.abs(lz) > limit ? -lz : 0);
            }
        }
    }

    /**
     * Whether any chunk of the island is within twice the seeding range (§5.8) of a player: such islands may soon
     * join the player's, in the player's frame, so moving them now would only mean moving them back.
     */
    private boolean nearPlayer(IslandGraph.Island island) {
        int range = 2 * (this.level.getServer().getPlayerList().getViewDistance() + 3);
        List<ServerPlayer> players = this.level.players();
        if (players.isEmpty()) return false;
        LongIterator it = island.chunks().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            for (ServerPlayer player : players) {
                int dx = Math.abs(this.wrap.minChunkDelta(IslandGraph.keyX(key), player.chunkPosition().x));
                int dz = Math.abs(this.wrap.minChunkDelta(IslandGraph.keyZ(key), player.chunkPosition().z));
                if (Math.max(dx, dz) <= range) return true;
            }
        }
        return false;
    }

    /**
     * The lap most of the island's chunks share on one axis, if moving it to 0 is worthwhile: 0 unless it holds at
     * least twice as many chunks as lap 0 does. The margin keeps an island straddling the seam from flipping back
     * and forth as chunks come and go on either side.
     */
    private int dominantLap(IslandGraph.Island island, boolean xAxis) {
        Int2IntOpenHashMap counts = new Int2IntOpenHashMap();
        LongIterator it = island.chunks().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            long laps = this.graph.laps(IslandGraph.keyX(key), IslandGraph.keyZ(key));
            counts.addTo(xAxis ? IslandGraph.lapX(laps) : IslandGraph.lapZ(laps), 1);
        }
        int best = 0;
        int bestCount = 0;
        for (Int2IntMap.Entry entry : counts.int2IntEntrySet()) {
            if (entry.getIntValue() > bestCount) {
                best = entry.getIntKey();
                bestCount = entry.getIntValue();
            }
        }
        return bestCount >= 2 * counts.get(0) ? best : 0;
    }

    // ---- join policy ----

    /**
     * A chunk with no loaded neighbors starts in the frame of whatever caused it to load (§5.8). Tickets do not
     * record their cause, so: the nearest player within loading range, else lap (0, 0).
     */
    private long seedLaps(int x, int z) {
        int range = this.level.getServer().getPlayerList().getViewDistance() + 3;
        ServerPlayer nearest = null;
        int best = Integer.MAX_VALUE;
        for (ServerPlayer player : this.level.players()) {
            int dx = Math.abs(this.wrap.minChunkDelta(x, SectionPos.blockToSectionCoord(player.getBlockX())));
            int dz = Math.abs(this.wrap.minChunkDelta(z, SectionPos.blockToSectionCoord(player.getBlockZ())));
            int distance = Math.max(dx, dz);
            if (distance <= range && distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) return IslandGraph.packLaps(0, 0);
        int liftedX = this.wrap.nearestChunk(x, SectionPos.blockToSectionCoord(nearest.getBlockX()));
        int liftedZ = this.wrap.nearestChunk(z, SectionPos.blockToSectionCoord(nearest.getBlockZ()));
        return IslandGraph.packLaps(this.wrap.chunkLap(liftedX), this.wrap.chunkLap(liftedZ));
    }

    /** Islands with players win joins and merges, so players are never the ones re-framed. */
    private long playerWeight(int island) {
        long players = 0;
        for (ServerPlayer player : this.level.players()) {
            if (this.graph.islandOf(this.wrap.canonChunk(player.chunkPosition().x), this.wrap.canonChunk(player.chunkPosition().z)) == island) players++;
        }
        return players;
    }
}
