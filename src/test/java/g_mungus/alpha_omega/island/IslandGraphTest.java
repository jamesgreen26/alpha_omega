package g_mungus.alpha_omega.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IslandGraphTest {

    private static final IslandGraph.Weights NO_WEIGHTS = id -> 0;

    @Test
    void singleChunkSeedsItsOwnIsland() {
        IslandGraph graph = new IslandGraph(16);
        graph.join(3, 4, (x, z) -> IslandGraph.packLaps(2, -1), NO_WEIGHTS);
        long laps = graph.laps(3, 4);
        assertEquals(2, IslandGraph.lapX(laps));
        assertEquals(-1, IslandGraph.lapZ(laps));
    }

    @Test
    void neighborsAcrossTheSeamAreLiftedContiguously() {
        IslandGraph graph = new IslandGraph(16);
        graph.join(15, 0, (x, z) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        graph.join(0, 0, (x, z) -> IslandGraph.packLaps(5, 5), NO_WEIGHTS);
        // Chunk 0 continues chunk 15 eastwards: lifted x 16, i.e. lap 1.
        assertEquals(1, IslandGraph.lapX(graph.laps(0, 0)));
        assertEquals(0, IslandGraph.lapZ(graph.laps(0, 0)));
        assertEquals(graph.islandOf(15, 0), graph.islandOf(0, 0));
    }

    @Test
    void aCenteredWindowPutsTheSeamAtHalfThePeriod() {
        IslandGraph graph = new IslandGraph(16, -8);
        // Chunks -1 and 0 are ordinary neighbors in lap 0; 7 and -8 meet across the seam.
        graph.join(-1, 0, (x, z) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        graph.join(0, 0, (x, z) -> IslandGraph.packLaps(5, 5), NO_WEIGHTS);
        assertEquals(0, IslandGraph.lapX(graph.laps(0, 0)));
        graph.join(7, 3, (x, z) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        graph.join(-8, 3, (x, z) -> IslandGraph.packLaps(5, 5), NO_WEIGHTS);
        assertEquals(1, IslandGraph.lapX(graph.laps(-8, 3)), "chunk -8 continues chunk 7 eastwards, at lifted 8");
        assertEquals(-8, graph.canon(8));
        assertEquals(7, graph.canon(-9));
        assertInvariants(graph);
    }

    @Test
    void disagreeingIslandsStayApartAndReportTheShift() {
        IslandGraph graph = new IslandGraph(16);
        graph.join(2, 2, (x, z) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        graph.join(4, 2, (x, z) -> IslandGraph.packLaps(1, 0), NO_WEIGHTS);
        graph.join(3, 2, (x, z) -> IslandGraph.packLaps(9, 9), NO_WEIGHTS);
        assertNotEquals(graph.islandOf(2, 2), graph.islandOf(4, 2));
        List<IslandGraph.PendingMerge> merges = graph.disagreements();
        assertEquals(1, merges.size());
        IslandGraph.PendingMerge merge = merges.get(0);
        graph.shift(merge.from(), merge.lapDX(), merge.lapDZ());
        graph.merge(merge.from(), merge.into());
        assertEquals(1, graph.islands().size());
        assertInvariants(graph);
    }

    @Test
    void randomJoinLeaveSequencesKeepInvariants() {
        Random random = new Random(1234);
        for (int run = 0; run < 200; run++) {
            int n = 8 + random.nextInt(24);
            // Both canonical windows: [0, n) and centered on 0.
            int origin = run % 2 == 0 ? 0 : -(n >> 1);
            IslandGraph graph = new IslandGraph(n, origin);
            List<long[]> present = new ArrayList<>();
            int steps = 50 + random.nextInt(400);
            for (int step = 0; step < steps; step++) {
                if (present.isEmpty() || random.nextInt(3) != 0) {
                    // Join near an existing chunk most of the time, so islands grow and meet.
                    int x, z;
                    if (!present.isEmpty() && random.nextInt(4) != 0) {
                        long[] base = present.get(random.nextInt(present.size()));
                        x = graph.canon((int) base[0] + random.nextInt(3) - 1);
                        z = graph.canon((int) base[1] + random.nextInt(3) - 1);
                    } else {
                        x = origin + random.nextInt(n);
                        z = origin + random.nextInt(n);
                    }
                    if (graph.islandOf(x, z) == 0) {
                        int lapX = random.nextInt(3) - 1;
                        int lapZ = random.nextInt(3) - 1;
                        graph.join(x, z, (cx, cz) -> IslandGraph.packLaps(lapX, lapZ), NO_WEIGHTS);
                        present.add(new long[] {x, z});
                    }
                } else {
                    long[] gone = present.remove(random.nextInt(present.size()));
                    graph.leave((int) gone[0], (int) gone[1]);
                }
                if (random.nextInt(10) == 0) {
                    graph.processSplits();
                    assertInvariants(graph);
                }
            }
            graph.processSplits();
            assertInvariants(graph);
            assertEquals(present.size(), graph.chunkCount());
        }
    }

    @Test
    void aBandAroundTheWorldLoopsUntilCut() {
        int n = 16;
        IslandGraph graph = new IslandGraph(n);
        for (int x = 0; x < n; x++) graph.join(x, 3, (cx, cz) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        assertEquals(1, graph.loops().size(), "a closed band must be flagged as a loop");

        graph.addCut(true, 5);
        graph.relayout(component -> IslandGraph.key(0, 3));
        assertTrue(graph.loops().isEmpty(), "cut band still loops");
        assertEquals(1, graph.islands().size());
        assertTrue(graph.islands().iterator().next().extentX() == n, "the band is unrolled into one strip");
        assertEquals(0, IslandGraph.lapX(graph.laps(0, 3)), "anchor kept its lap");
        assertInvariants(graph);

        // Moving the cut re-lifts the strip around the same anchor.
        graph.removeCut(true, 5);
        graph.addCut(true, 11);
        graph.relayout(component -> IslandGraph.key(0, 3));
        assertTrue(graph.loops().isEmpty());
        assertEquals(0, IslandGraph.lapX(graph.laps(0, 3)));
        assertInvariants(graph);
    }

    @Test
    void aFullyLoadedTorusNeedsACutOnEachAxis() {
        int n = 10;
        IslandGraph graph = new IslandGraph(n);
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) graph.join(x, z, (cx, cz) -> IslandGraph.packLaps(0, 0), NO_WEIGHTS);
        assertTrue(!graph.loops().isEmpty());
        graph.addCut(true, 0);
        graph.relayout(component -> component.iterator().nextLong());
        assertTrue(!graph.loops().isEmpty(), "still wraps along z");
        graph.addCut(false, 0);
        graph.relayout(component -> component.iterator().nextLong());
        assertTrue(graph.loops().isEmpty());
        assertInvariants(graph);
    }

    /** I1, I2 (except in islands flagged as looping), connectivity, exact bounding boxes, and disagreements. */
    private static void assertInvariants(IslandGraph graph) {
        int n = graph.period();
        int total = 0;
        for (IslandGraph.Island island : graph.islands()) {
            total += island.size();
            assertTrue(island.size() > 0, "empty island");
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            LongIterator it = island.chunks().iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int x = IslandGraph.keyX(key);
                int z = IslandGraph.keyZ(key);
                assertEquals(island.id, graph.islandOf(x, z), "I1: chunk listed in an island it does not map to");
                long laps = graph.laps(x, z);
                int lx = x + IslandGraph.lapX(laps) * n;
                int lz = z + IslandGraph.lapZ(laps) * n;
                minX = Math.min(minX, lx);
                maxX = Math.max(maxX, lx);
                minZ = Math.min(minZ, lz);
                maxZ = Math.max(maxZ, lz);
                if (graph.loops().contains(island.id)) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (graph.severed(x, z, dx, dz)) continue;
                        int nx = graph.canon(x + dx);
                        int nz = graph.canon(z + dz);
                        if (graph.islandOf(nx, nz) != island.id) continue;
                        long neighborLaps = graph.laps(nx, nz);
                        assertEquals(lx + dx, nx + IslandGraph.lapX(neighborLaps) * n, "I2: x not contiguous");
                        assertEquals(lz + dz, nz + IslandGraph.lapZ(neighborLaps) * n, "I2: z not contiguous");
                    }
                }
            }
            assertEquals(maxX - minX + 1, island.extentX(), "bounding box x");
            assertEquals(maxZ - minZ + 1, island.extentZ(), "bounding box z");
            assertTrue(connected(graph, island, n), "island not connected after splits");
        }
        assertEquals(graph.chunkCount(), total, "I1: chunk count mismatch");
        for (IslandGraph.PendingMerge merge : graph.disagreements()) {
            assertNotEquals(merge.from(), merge.into());
            assertTrue(merge.lapDX() != 0 || merge.lapDZ() != 0, "touching islands in the same frame should have merged");
        }
    }

    private static boolean connected(IslandGraph graph, IslandGraph.Island island, int n) {
        LongOpenHashSet remaining = new LongOpenHashSet(island.chunks());
        long start = remaining.iterator().nextLong();
        remaining.remove(start);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        queue.enqueue(start);
        while (!queue.isEmpty()) {
            long key = queue.dequeueLong();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (graph.severed(IslandGraph.keyX(key), IslandGraph.keyZ(key), dx, dz)) continue;
                    long neighbor = IslandGraph.key(graph.canon(IslandGraph.keyX(key) + dx), graph.canon(IslandGraph.keyZ(key) + dz));
                    if (remaining.remove(neighbor)) queue.enqueue(neighbor);
                }
            }
        }
        return remaining.isEmpty();
    }
}
