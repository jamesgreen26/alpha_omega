package g_mungus.alpha_omega.gametest;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Forcing chunks for tests that run side by side: a chunk stays forced until every test that forced it has released
 * it. Chunks are generated, with the area around them, before they are forced: gametest ticks run back to back,
 * faster than chunks generate around a ticket, and a chunk only ticks once its neighbours are loaded.
 */
final class TestChunks {

    private static final Long2IntOpenHashMap FORCED = new Long2IntOpenHashMap();

    private TestChunks() {
    }

    static synchronized void force(ServerLevel level, ChunkPos chunk) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) level.getChunk(chunk.x + dx, chunk.z + dz);
        }
        if (FORCED.addTo(chunk.toLong(), 1) == 0) level.setChunkForced(chunk.x, chunk.z, true);
    }

    static synchronized void release(ServerLevel level, ChunkPos chunk) {
        int count = FORCED.addTo(chunk.toLong(), -1);
        if (count <= 1) {
            FORCED.remove(chunk.toLong());
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    static void release(ServerLevel level, Set<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) release(level, chunk);
    }
}
