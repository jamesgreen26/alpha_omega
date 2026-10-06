package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.mixin.band.ChunkMapBandAccessor;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
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
    private static final long STALE_MILLIS = 120_000;

    /** Band chunks let through the gate, whose next {@code FULL} step runs. Any thread. */
    private static final Set<Long> PASSING = ConcurrentHashMap.newKeySet();
    /** Band chunks holding a gate ticket on their source. Main thread. */
    private static final Long2ObjectOpenHashMap<Waiting> WAITING = new Long2ObjectOpenHashMap<>();

    private record Waiting(ServerLevel level, ChunkPos source, long since) {
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
        if (PASSING.remove(pos.toLong())) return null;
        CompletableFuture<Void> ready = new CompletableFuture<>();
        ((ChunkMapBandAccessor) map).alpha_omega$mainThreadExecutor().execute(() -> waitForSource(level, geometry, pos, ready, 0));
        return ready.thenCompose(v -> {
            PASSING.add(pos.toLong());
            return map.applyStep(holder, step, cache);
        });
    }

    /** Main thread: completes {@code ready} once the source of {@code band} is a full chunk. */
    private static void waitForSource(ServerLevel level, OrbifoldGeometry geometry, ChunkPos band, CompletableFuture<Void> ready, int attempt) {
        OrbifoldGeometry.Cell cell = geometry.canonChunk(band.x, band.z);
        ChunkPos source = new ChunkPos(cell.x(), cell.z());
        if (attempt == 0) {
            BandCounters.gateWaits++;
            if (!WAITING.containsKey(band.toLong())) {
                level.getChunkSource().addRegionTicket(GATE, source, GATE_LEVEL_DISTANCE, band);
                WAITING.put(band.toLong(), new Waiting(level, source, System.currentTimeMillis()));
            }
        }
        if (level.getChunkSource().getChunkNow(source.x, source.z) != null) {
            ready.complete(null);
            return;
        }
        BandCounters.gateHeld++;
        level.getChunkSource().getChunkFuture(source.x, source.z, ChunkStatus.FULL, true).whenComplete((result, error) ->
            ((ChunkMapBandAccessor) level.getChunkSource().chunkMap).alpha_omega$mainThreadExecutor().execute(() -> {
                if (error == null && result.isSuccess()) {
                    ready.complete(null);
                } else if (attempt + 1 < MAX_ATTEMPTS) {
                    waitForSource(level, geometry, band, ready, attempt + 1);
                } else {
                    AlphaOmegaMod.LOGGER.warn("Band chunk {}: its source {} did not load; letting it through unfilled", band, source);
                    ready.complete(null);
                }
            }));
    }

    /** The band chunk has been filled (or let through): its source no longer needs holding. Main thread. */
    public static void release(ServerLevel level, ChunkPos band) {
        Waiting waiting = WAITING.remove(band.toLong());
        if (waiting != null) waiting.level.getChunkSource().removeRegionTicket(GATE, waiting.source, GATE_LEVEL_DISTANCE, band);
    }

    /** Drops gate tickets whose band chunk never got promoted (its load was abandoned). Main thread, each tick. */
    public static void tick() {
        if (WAITING.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = WAITING.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Waiting> entry = it.next();
            Waiting waiting = entry.getValue();
            if (now - waiting.since < STALE_MILLIS) continue;
            waiting.level.getChunkSource().removeRegionTicket(GATE, waiting.source, GATE_LEVEL_DISTANCE, new ChunkPos(entry.getLongKey()));
            it.remove();
        }
    }

    public static int waiting() {
        return WAITING.size();
    }

    public static void clear() {
        WAITING.clear();
        PASSING.clear();
    }
}
