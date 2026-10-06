package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.mixin.band.ChunkMapBandAccessor;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.jetbrains.annotations.Nullable;

/**
 * The promotion gate (RS §3.7, orbifold plan phase 4): a band or skirt chunk does not become a full chunk until its
 * source is one. The gate sits in front of the {@code FULL} generation step ({@code ChunkMap.applyStep}), the step
 * that turns a proto chunk into a {@code LevelChunk}: until it has run, the chunk is not in its level, so nothing can
 * write to it, tick it or send it. The step is held until the source's {@code FULL} step is done (a gate ticket makes
 * sure it loads), and the fill ({@link BandFill#promote}) runs inside the step, before the chunk's block entities and
 * ticks are registered and before {@code ChunkEvent.Load}.
 *
 * <p>Why there and not earlier: waiting at an earlier step (say {@code INITIALIZE_LIGHT}, so light would be computed
 * from the filled content) deadlocks. A source's {@code FULL} needs its neighbours at {@code INITIALIZE_LIGHT}, and an
 * edge source's neighbours include band chunks of the opposite seam, waiting in turn for sources next to the first
 * band chunk. Nothing waits on a chunk's {@code FULL} step except its own promotion and its neighbours' ticking, so
 * holding it creates no cycle. The cost is that light is rechecked at every cell the fill changes.
 */
public final class BandGate {

    /** Holds a band chunk's source at full status while the band chunk waits for it. Keyed by the band chunk. */
    public static final TicketType<ChunkPos> GATE = TicketType.create("alpha_omega_band_gate", Comparator.comparingLong(ChunkPos::toLong));
    private static final int GATE_LEVEL_DISTANCE = 0;
    private static final int MAX_ATTEMPTS = 8;

    /** Band chunks let through the gate, whose next {@code FULL} step runs, by level. Any thread. */
    private static final Set<Key> PASSING = ConcurrentHashMap.newKeySet();
    /**
     * Band chunks holding a gate ticket on their source, by level, with how many promotions are in flight for each.
     * Main thread. A band chunk can have more than one: its level can drop and rise again (a one-tick ticket, a test
     * releasing and re-forcing it) while an earlier promotion still waits, and every promotion that was started runs.
     *
     * <p>Holds never time out. A promotion whose wait is over can sit in the main thread's chunk queue for as long as the
     * band chunk's level stays too low to run it (minutes, in a long test run), and runs as soon as the level rises
     * again. If its source had been let go meanwhile, it would join its level unfilled: the source is held until then.
     */
    private static final Map<Key, Waiting> WAITING = new HashMap<>();

    /** A band chunk of a level: the overworld and the Nether each have their own. */
    private record Key(ResourceKey<Level> level, long chunk) {
    }

    private static final class Waiting {
        final ServerLevel level;
        final ChunkPos source;
        int count;
        /** When a waiter's wait last ended (its promotion queued), for {@link BandCounters#gateLongestQueueMillis}. */
        long opened;

        Waiting(ServerLevel level, ChunkPos source) {
            this.level = level;
            this.source = source;
        }
    }

    private BandGate() {
    }

    /**
     * From {@code ChunkMap.applyStep} (worldgen threads): for the {@code FULL} step of a band or skirt chunk, a future
     * that runs the step once the source is full; null to run it now.
     */
    @Nullable
    public static CompletableFuture<ChunkAccess> gate(ChunkMap map, ServerLevel level, GenerationChunkHolder holder, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache) {
        if (step.targetStatus() != ChunkStatus.FULL) return null;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return null;
        ChunkPos pos = holder.getPos();
        if (geometry.isTileChunk(pos.x, pos.z) || !geometry.inFootprintChunk(pos.x, pos.z)) return null;
        Key key = new Key(level.dimension(), pos.toLong());
        if (PASSING.remove(key)) return null;
        CompletableFuture<Void> ready = new CompletableFuture<>();
        ((ChunkMapBandAccessor) map).alpha_omega$mainThreadExecutor().execute(() -> waitForSource(level, geometry, pos, ready, 0));
        return ready.thenCompose(v -> {
            PASSING.add(key);
            return map.applyStep(holder, step, cache);
        });
    }

    /** Main thread: completes {@code ready} once the source of {@code band} is a full chunk. */
    private static void waitForSource(ServerLevel level, OrbifoldGeometry geometry, ChunkPos band, CompletableFuture<Void> ready, int attempt) {
        OrbifoldGeometry.Cell cell = geometry.canonChunk(band.x, band.z);
        ChunkPos source = new ChunkPos(cell.x(), cell.z());
        if (attempt == 0) {
            BandCounters.gateWaits++;
            hold(level, band, source);
        }
        if (level.getChunkSource().getChunkNow(source.x, source.z) != null) {
            open(level, band, ready);
            return;
        }
        BandCounters.gateHeld++;
        level.getChunkSource().getChunkFuture(source.x, source.z, ChunkStatus.FULL, true).whenComplete((result, error) ->
            ((ChunkMapBandAccessor) level.getChunkSource().chunkMap).alpha_omega$mainThreadExecutor().execute(() -> {
                if (error == null && result.isSuccess()) {
                    open(level, band, ready);
                } else if (attempt + 1 < MAX_ATTEMPTS) {
                    waitForSource(level, geometry, band, ready, attempt + 1);
                } else {
                    AlphaOmegaMod.LOGGER.warn("Band chunk {}: its source {} did not load; letting it through unfilled", band, source);
                    open(level, band, ready);
                }
            }));
    }

    private static void open(ServerLevel level, ChunkPos band, CompletableFuture<Void> ready) {
        Waiting waiting = WAITING.get(new Key(level.dimension(), band.toLong()));
        if (waiting != null) waiting.opened = System.currentTimeMillis();
        ready.complete(null);
    }

    /** A waiter for {@code band}'s promotion holds its source at full status until that promotion. Main thread. */
    static void hold(ServerLevel level, ChunkPos band, ChunkPos source) {
        Waiting waiting = WAITING.computeIfAbsent(new Key(level.dimension(), band.toLong()), key -> {
            level.getChunkSource().addRegionTicket(GATE, source, GATE_LEVEL_DISTANCE, band);
            return new Waiting(level, source);
        });
        if (waiting.count > 0) BandCounters.gateOverlaps++;
        waiting.count++;
    }

    /** For tests: what a gate waiter does first, for {@code band}. */
    public static void holdForTest(ServerLevel level, ChunkPos band) {
        OrbifoldGeometry.Cell cell = Band.geometry(level).canonChunk(band.x, band.z);
        hold(level, band, new ChunkPos(cell.x(), cell.z()));
    }

    /** One promotion of the band chunk has run (filled, or let through): when it was the last in flight, its source is let go. Main thread. */
    public static void release(ServerLevel level, ChunkPos band) {
        Key key = new Key(level.dimension(), band.toLong());
        Waiting waiting = WAITING.get(key);
        if (waiting == null) return;
        if (waiting.opened > 0) {
            long queued = System.currentTimeMillis() - waiting.opened;
            BandCounters.gateLongestQueueMillis = Math.max(BandCounters.gateLongestQueueMillis, queued);
            if (queued > 30_000) AlphaOmegaMod.LOGGER.info("Band chunk {} in {} was promoted {} s after its gate opened", band, level.dimension().location(), queued / 1000);
        }
        if (--waiting.count > 0) return;
        WAITING.remove(key);
        waiting.level.getChunkSource().removeRegionTicket(GATE, waiting.source, GATE_LEVEL_DISTANCE, band);
    }

    /** For tests: how many promotions of {@code band} are in flight (each holding its source). */
    public static int holds(ServerLevel level, ChunkPos band) {
        Waiting waiting = WAITING.get(new Key(level.dimension(), band.toLong()));
        return waiting == null ? 0 : waiting.count;
    }

    public static int waiting() {
        return WAITING.size();
    }

    public static void clear() {
        WAITING.clear();
        PASSING.clear();
    }
}
