package g_mungus.alpha_omega.wrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class WrapTest {

    private static final int W = Wrap.PERIOD;
    private static final int N = Wrap.CHUNK_PERIOD;

    @Test
    void periodsAreConsistent() {
        assertEquals(0, W % 3072);
        assertEquals(W, N * 16);
    }

    @Test
    void chunkKeysCanonicalize() {
        for (int x = -2 * N; x <= 2 * N; x += 7) {
            for (int z = -2 * N; z <= 2 * N; z += 11) {
                long key = Wrap.canonChunkKey(ChunkPos.asLong(x, z));
                assertEquals(Math.floorMod(x, N), ChunkPos.getX(key));
                assertEquals(Math.floorMod(z, N), ChunkPos.getZ(key));
            }
        }
        assertEquals(ChunkPos.INVALID_CHUNK_POS, Wrap.canonChunkKey(ChunkPos.INVALID_CHUNK_POS));
    }

    @Test
    void sectionKeysKeepY() {
        for (int y = -4; y <= 19; y++) {
            long key = Wrap.canonSectionKey(SectionPos.asLong(-1, y, N + 3));
            assertEquals(N - 1, SectionPos.x(key));
            assertEquals(y, SectionPos.y(key));
            assertEquals(3, SectionPos.z(key));
        }
    }

    @Test
    void blockKeysKeepY() {
        long key = Wrap.canonBlockKey(BlockPos.asLong(-1, -64, W + 5));
        assertEquals(W - 1, BlockPos.getX(key));
        assertEquals(-64, BlockPos.getY(key));
        assertEquals(5, BlockPos.getZ(key));
    }

    @Test
    void canonicalPositionsAreReturnedUnchanged() {
        BlockPos pos = new BlockPos(5, 70, W - 1);
        assertSame(pos, Wrap.canon(pos));
        ChunkPos chunk = new ChunkPos(0, N - 1);
        assertSame(chunk, Wrap.canon(chunk));
    }

    @Test
    void nearestBlockAcrossSeam() {
        assertEquals(W + 2, Wrap.nearestBlock(2, W - 3));
        assertEquals(-3, Wrap.nearestBlock(W - 3, 2));
        assertEquals(N, Wrap.nearestChunk(0, N - 1));
    }
}
