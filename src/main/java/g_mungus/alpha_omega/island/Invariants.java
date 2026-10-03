package g_mungus.alpha_omega.island;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapMath;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Runtime checks of the design's invariants (§13.1), for tests and the {@code /wrap check} command. */
public final class Invariants {

    private Invariants() {
    }

    /** Violations of I1 (one island per chunk), I2 (contiguous lifts) and I4 (entities in frame); empty if none. */
    public static List<String> check(ServerLevel level) {
        List<String> violations = new ArrayList<>();
        IslandGraph graph = IslandManager.of(level).graph();
        if (graph == null) return violations;
        Wrap wrap = Wrap.of(level);
        int n = graph.period();
        int counted = 0;
        for (IslandGraph.Island island : graph.islands()) {
            counted += island.size();
            boolean looping = graph.loops().contains(island.id);
            LongIterator it = island.chunks().iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int x = IslandGraph.keyX(key);
                int z = IslandGraph.keyZ(key);
                if (graph.islandOf(x, z) != island.id) violations.add("I1: chunk " + x + "," + z + " listed in island " + island.id);
                if (looping) continue;
                long laps = graph.laps(x, z);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = WrapMath.canon(x + dx, n);
                        int nz = WrapMath.canon(z + dz, n);
                        if ((dx == 0 && dz == 0) || graph.severed(x, z, dx, dz) || graph.islandOf(nx, nz) != island.id) continue;
                        long neighbor = graph.laps(nx, nz);
                        if (x + IslandGraph.lapX(laps) * n + dx != nx + IslandGraph.lapX(neighbor) * n
                            || z + IslandGraph.lapZ(laps) * n + dz != nz + IslandGraph.lapZ(neighbor) * n) {
                            violations.add("I2: chunks " + x + "," + z + " and " + nx + "," + nz + " are not contiguous in island " + island.id);
                        }
                    }
                }
            }
        }
        if (counted != graph.chunkCount()) violations.add("I1: " + graph.chunkCount() + " chunks mapped but " + counted + " listed");

        for (Entity entity : level.getAllEntities()) {
            int chunkX = entity.chunkPosition().x;
            int chunkZ = entity.chunkPosition().z;
            long laps = graph.laps(wrap.canonChunk(chunkX), wrap.canonChunk(chunkZ));
            if (laps == IslandGraph.ABSENT) continue;
            if (Math.floorDiv(chunkX, n) != IslandGraph.lapX(laps) || Math.floorDiv(chunkZ, n) != IslandGraph.lapZ(laps)) {
                violations.add("I4: " + entity + " is in lap " + Math.floorDiv(chunkX, n) + "," + Math.floorDiv(chunkZ, n)
                    + " but its chunk's island is in lap " + IslandGraph.lapX(laps) + "," + IslandGraph.lapZ(laps));
            }
        }
        return violations;
    }
}
