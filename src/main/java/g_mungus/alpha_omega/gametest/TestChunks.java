package g_mungus.alpha_omega.gametest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Forcing chunks for tests that run side by side: a chunk stays forced until every test that forced it has released
 * it. Chunks are generated, with the area around them, before they are forced: gametest ticks run back to back,
 * faster than chunks generate around a ticket, and a chunk only ticks once its neighbours are loaded. Counted per
 * level, since the Nether's tests force chunks too.
 */
final class TestChunks {

    private record Key(ResourceKey<Level> level, long chunk) {
    }

    private static final Map<Key, Integer> FORCED = new HashMap<>();

    private TestChunks() {
    }

    static synchronized void force(ServerLevel level, ChunkPos chunk) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
        }
        if (FORCED.merge(new Key(level.dimension(), chunk.toLong()), 1, Integer::sum) == 1) level.setChunkForced(chunk.x, chunk.z, true);
    }

    static synchronized void release(ServerLevel level, ChunkPos chunk) {
        Key key = new Key(level.dimension(), chunk.toLong());
        int count = FORCED.getOrDefault(key, 0);
        if (count <= 1) {
            FORCED.remove(key);
            level.setChunkForced(chunk.x, chunk.z, false);
        } else {
            FORCED.put(key, count - 1);
        }
    }

    static void release(ServerLevel level, Set<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) release(level, chunk);
    }
}
