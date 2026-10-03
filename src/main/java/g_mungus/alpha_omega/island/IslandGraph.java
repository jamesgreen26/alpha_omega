package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.wrap.WrapMath;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Islands and lift tables (design doc §5), independent of Minecraft so it can be property tested.
 * <p>
 * Chunks are canonical coordinates in {@code [0, n)}. Every present chunk belongs to exactly one island (I1) and
 * has a lap pair; its lifted position is {@code canonical + lap * n}. Adjacent chunks of one island are lifted
 * contiguously (I2). Islands that touch with disagreeing frames are kept apart (see {@link #disagreements()});
 * islands about to wrap all the way around are reported as {@link #loops()}.
 */
public final class IslandGraph {

    /** Returned by lookups for chunks that are not present. */
    public static final long ABSENT = Long.MIN_VALUE;
    /** An island wraps (needs a cut) once its lifted extent comes within this many chunks of the period. */
    public static final int EXTENT_MARGIN = 2;

    /** Decides which island a joining chunk is added to: the heaviest (players, then entities, then size). */
    public interface Weights {
        long weight(int islandId);
    }

    /** Lap pair for a chunk that starts a new island (§5.8), packed with {@link #packLaps}. */
    public interface Seeds {
        long seedLaps(int x, int z);
    }

    /** Island {@code from} touches {@code into}; shifting {@code from} by the lap delta makes them agree. */
    public record PendingMerge(int from, int into, int lapDX, int lapDZ) {
    }

    public static final class Island {
        public final int id;
        final LongOpenHashSet chunks = new LongOpenHashSet();
        int minX, maxX, minZ, maxZ; // lifted bounding box, in chunks
        boolean splitPending;

        Island(int id) {
            this.id = id;
        }

        public int size() {
            return this.chunks.size();
        }

        public LongOpenHashSet chunks() {
            return this.chunks;
        }

        public int extentX() {
            return this.chunks.isEmpty() ? 0 : this.maxX - this.minX + 1;
        }

        public int extentZ() {
            return this.chunks.isEmpty() ? 0 : this.maxZ - this.minZ + 1;
        }
    }

    private final int n;
    /** canonical chunk key -> (islandId:32 | lapX:16 | lapZ:16) */
    private final Long2LongOpenHashMap info = new Long2LongOpenHashMap();
    private final Int2ObjectOpenHashMap<Island> islands = new Int2ObjectOpenHashMap<>();
    private final IntSet loops = new IntOpenHashSet();
    private int nextId = 1;

    public IslandGraph(int n) {
        this.n = n;
        this.info.defaultReturnValue(ABSENT);
    }

    // ---- keys and packing ----

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }

    public static long packLaps(int lapX, int lapZ) {
        return ((long) lapX << 32) | (lapZ & 0xFFFFFFFFL);
    }

    public static int lapX(long laps) {
        return (int) (laps >> 32);
    }

    public static int lapZ(long laps) {
        return (int) laps;
    }

    private static long packInfo(int id, int lapX, int lapZ) {
        return ((long) id << 32) | ((lapX & 0xFFFFL) << 16) | (lapZ & 0xFFFFL);
    }

    private static int infoId(long info) {
        return (int) (info >> 32);
    }

    private static int infoLapX(long info) {
        return (short) (info >> 16);
    }

    private static int infoLapZ(long info) {
        return (short) info;
    }

    // ---- queries ----

    public int period() {
        return this.n;
    }

    /** The chunk's lap pair (see {@link #lapX(long)}), or {@link #ABSENT}. */
    public long laps(int x, int z) {
        long info = this.info.get(key(x, z));
        return info == ABSENT ? ABSENT : packLaps(infoLapX(info), infoLapZ(info));
    }

    /** The chunk's island id, or 0. */
    public int islandOf(int x, int z) {
        long info = this.info.get(key(x, z));
        return info == ABSENT ? 0 : infoId(info);
    }

    public Island island(int id) {
        return this.islands.get(id);
    }

    public Collection<Island> islands() {
        return this.islands.values();
    }

    public int chunkCount() {
        return this.info.size();
    }

    /**
     * Every pair of different islands that touch, with the lap delta that would bring {@code from} into
     * {@code into}'s frame across the contact. Islands only touch when their frames disagree (agreeing ones merge
     * on contact). Scans every chunk, so call it sparingly.
     */
    public List<PendingMerge> disagreements() {
        List<PendingMerge> result = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (Long2LongMap.Entry entry : this.info.long2LongEntrySet()) {
            long key = entry.getLongKey();
            long info = entry.getLongValue();
            int x = keyX(key);
            int z = keyZ(key);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = WrapMath.canon(x + dx, this.n);
                    int nz = WrapMath.canon(z + dz, this.n);
                    long neighbor = this.info.get(key(nx, nz));
                    if (neighbor == ABSENT || infoId(neighbor) == infoId(info)) continue;
                    long pair = key(Math.min(infoId(neighbor), infoId(info)), Math.max(infoId(neighbor), infoId(info)));
                    if (!seen.add(pair)) continue;
                    // Where this chunk's island puts the neighbor, versus where the neighbor's island puts it.
                    int expectedX = x + infoLapX(info) * this.n + dx;
                    int expectedZ = z + infoLapZ(info) * this.n + dz;
                    int dlx = Math.floorDiv(expectedX - (nx + infoLapX(neighbor) * this.n), this.n);
                    int dlz = Math.floorDiv(expectedZ - (nz + infoLapZ(neighbor) * this.n), this.n);
                    result.add(new PendingMerge(infoId(neighbor), infoId(info), dlx, dlz));
                }
            }
        }
        return result;
    }

    public IntSet loops() {
        return this.loops;
    }

    // ---- join / leave ----

    public void join(int x, int z, Seeds seeds, Weights weights) {
        long key = key(x, z);
        if (this.info.containsKey(key)) return;

        // Each neighbor proposes a lap pair for the new chunk; tally the proposals per neighboring island.
        Int2ObjectOpenHashMap<Long2IntOpenHashMap> votes = new Int2ObjectOpenHashMap<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = WrapMath.canon(x + dx, this.n);
                int nz = WrapMath.canon(z + dz, this.n);
                long neighbor = this.info.get(key(nx, nz));
                if (neighbor == ABSENT) continue;
                // The neighbor is lifted to (nx, nz) + lap * n, and the new chunk sits at (-dx, -dz) from it.
                int liftedX = nx + infoLapX(neighbor) * this.n - dx;
                int liftedZ = nz + infoLapZ(neighbor) * this.n - dz;
                long laps = packLaps(Math.floorDiv(liftedX - x, this.n), Math.floorDiv(liftedZ - z, this.n));
                votes.computeIfAbsent(infoId(neighbor), id -> new Long2IntOpenHashMap()).addTo(laps, 1);
            }
        }

        if (votes.isEmpty()) {
            long laps = seeds.seedLaps(x, z);
            this.add(this.newIsland(), x, z, lapX(laps), lapZ(laps));
            return;
        }

        // One proposal per island. Two different proposals from one island mean it is closing a loop (R7).
        Int2LongOpenHashMap chosen = new Int2LongOpenHashMap();
        for (Int2ObjectMap.Entry<Long2IntOpenHashMap> entry : votes.int2ObjectEntrySet()) {
            Long2IntOpenHashMap counts = entry.getValue();
            if (counts.size() > 1) this.loops.add(entry.getIntKey());
            long best = 0;
            int bestCount = -1;
            for (Long2IntMap.Entry vote : counts.long2IntEntrySet()) {
                if (vote.getIntValue() > bestCount) {
                    best = vote.getLongKey();
                    bestCount = vote.getIntValue();
                }
            }
            chosen.put(entry.getIntKey(), best);
        }

        int target = 0;
        for (int id : chosen.keySet()) {
            if (target == 0 || this.heavier(id, target, weights)) target = id;
        }
        long targetLaps = chosen.get(target);
        Island targetIsland = this.islands.get(target);
        this.add(targetIsland, x, z, lapX(targetLaps), lapZ(targetLaps));

        // Islands already in the same frame merge by relabeling (§5.6). The rest stay separate until shifted.
        for (Int2LongMap.Entry entry : chosen.int2LongEntrySet()) {
            if (entry.getIntKey() != target && entry.getLongValue() == targetLaps) {
                this.absorb(this.islands.get(entry.getIntKey()), targetIsland);
            }
        }
        this.checkExtent(targetIsland);
    }

    public void leave(int x, int z) {
        long key = key(x, z);
        long info = this.info.remove(key);
        if (info == ABSENT) return;
        Island island = this.islands.get(infoId(info));
        island.chunks.remove(key);
        if (island.chunks.isEmpty()) {
            this.removeIsland(island.id);
        } else {
            island.splitPending = true;
        }
    }

    /** Splits islands that may have become disconnected (§5.5). Splits never change laps. */
    public void processSplits() {
        List<Island> pending = new ArrayList<>();
        for (Island island : this.islands.values()) {
            if (island.splitPending) pending.add(island);
        }
        for (Island island : pending) {
            island.splitPending = false;
            this.split(island);
        }
    }

    // ---- shifting and merging (phase 4) ----

    /** Translates an island's whole frame by whole laps (§6.5); only the lift table changes. */
    public void shift(int id, int lapDX, int lapDZ) {
        Island island = this.islands.get(id);
        if (island == null || (lapDX == 0 && lapDZ == 0)) return;
        LongIterator it = island.chunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            long info = this.info.get(key);
            this.info.put(key, packInfo(id, infoLapX(info) + lapDX, infoLapZ(info) + lapDZ));
        }
        island.minX += lapDX * this.n;
        island.maxX += lapDX * this.n;
        island.minZ += lapDZ * this.n;
        island.maxZ += lapDZ * this.n;
    }

    /** Absorbs {@code from} into {@code into}; their frames must already agree. */
    public void merge(int from, int into) {
        Island source = this.islands.get(from);
        Island target = this.islands.get(into);
        if (source == null || target == null || source == target) return;
        this.absorb(source, target);
        this.checkExtent(target);
    }

    // ---- internals ----

    private boolean heavier(int a, int b, Weights weights) {
        long wa = weights.weight(a);
        long wb = weights.weight(b);
        if (wa != wb) return wa > wb;
        int sa = this.islands.get(a).size();
        int sb = this.islands.get(b).size();
        if (sa != sb) return sa > sb;
        return a < b;
    }

    private Island newIsland() {
        Island island = new Island(this.nextId++);
        this.islands.put(island.id, island);
        return island;
    }

    private void add(Island island, int x, int z, int lapX, int lapZ) {
        long key = key(x, z);
        this.info.put(key, packInfo(island.id, lapX, lapZ));
        int lx = x + lapX * this.n;
        int lz = z + lapZ * this.n;
        if (island.chunks.isEmpty()) {
            island.minX = island.maxX = lx;
            island.minZ = island.maxZ = lz;
        } else {
            island.minX = Math.min(island.minX, lx);
            island.maxX = Math.max(island.maxX, lx);
            island.minZ = Math.min(island.minZ, lz);
            island.maxZ = Math.max(island.maxZ, lz);
        }
        island.chunks.add(key);
    }

    private void absorb(Island source, Island target) {
        LongIterator it = source.chunks.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            long info = this.info.get(key);
            this.add(target, keyX(key), keyZ(key), infoLapX(info), infoLapZ(info));
        }
        target.splitPending |= source.splitPending;
        if (this.loops.remove(source.id)) this.loops.add(target.id);
        this.islands.remove(source.id);
    }

    private void removeIsland(int id) {
        this.islands.remove(id);
        this.loops.remove(id);
    }

    private void checkExtent(Island island) {
        if (island.extentX() > this.n - EXTENT_MARGIN || island.extentZ() > this.n - EXTENT_MARGIN) this.loops.add(island.id);
    }

    private void split(Island island) {
        LongOpenHashSet remaining = new LongOpenHashSet(island.chunks);
        List<LongOpenHashSet> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            long start = remaining.iterator().nextLong();
            LongOpenHashSet component = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            queue.enqueue(start);
            remaining.remove(start);
            component.add(start);
            while (!queue.isEmpty()) {
                long key = queue.dequeueLong();
                int x = keyX(key);
                int z = keyZ(key);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long neighbor = key(WrapMath.canon(x + dx, this.n), WrapMath.canon(z + dz, this.n));
                        if (remaining.remove(neighbor)) {
                            component.add(neighbor);
                            queue.enqueue(neighbor);
                        }
                    }
                }
            }
            components.add(component);
        }

        // The largest component keeps the id; the rest become new islands with the same laps. Extents only shrink
        // here, so whether each part still wraps is re-evaluated.
        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        island.chunks.clear();
        this.loops.remove(island.id);
        for (int i = 0; i < components.size(); i++) {
            Island target = i == 0 ? island : this.newIsland();
            LongIterator it = components.get(i).iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                long info = this.info.get(key);
                this.add(target, keyX(key), keyZ(key), infoLapX(info), infoLapZ(info));
            }
            this.checkExtent(target);
        }
    }
}
