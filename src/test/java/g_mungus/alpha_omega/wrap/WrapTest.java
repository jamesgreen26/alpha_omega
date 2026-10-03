package g_mungus.alpha_omega.wrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WrapTest {

    private static final Wrap WRAP = new Wrap(WorldWrapSettings.DEFAULT_PERIOD);
    private static final int W = WRAP.period;
    private static final int N = WRAP.chunkPeriod;

    @Test
    void defaultPeriodIsConsistent() {
        assertEquals(0, W % 3072, "clouds repeat every 3072 blocks");
        // The lowest temperature octave (quarter scale, 2^-10) must span whole lattice cells.
        assertEquals(0, W % 4096);
        assertEquals(W, N * 16);
    }

    @Test
    void chunkKeysCanonicalize() {
        for (int x = -2 * N; x <= 2 * N; x += 7) {
            for (int z = -2 * N; z <= 2 * N; z += 11) {
                long key = WRAP.canonChunkKey(ChunkPos.asLong(x, z));
                assertEquals(Math.floorMod(x, N), ChunkPos.getX(key));
                assertEquals(Math.floorMod(z, N), ChunkPos.getZ(key));
            }
        }
        assertEquals(ChunkPos.INVALID_CHUNK_POS, WRAP.canonChunkKey(ChunkPos.INVALID_CHUNK_POS));
    }

    @Test
    void sectionKeysKeepY() {
        for (int y = -4; y <= 19; y++) {
            long key = WRAP.canonSectionKey(SectionPos.asLong(-1, y, N + 3));
            assertEquals(N - 1, SectionPos.x(key));
            assertEquals(y, SectionPos.y(key));
            assertEquals(3, SectionPos.z(key));
        }
    }

    @Test
    void blockKeysKeepY() {
        long key = WRAP.canonBlockKey(BlockPos.asLong(-1, -64, W + 5));
        assertEquals(W - 1, BlockPos.getX(key));
        assertEquals(-64, BlockPos.getY(key));
        assertEquals(5, BlockPos.getZ(key));
    }

    @Test
    void canonicalPositionsAreReturnedUnchanged() {
        BlockPos pos = new BlockPos(5, 70, W - 1);
        assertSame(pos, WRAP.canon(pos));
        ChunkPos chunk = new ChunkPos(0, N - 1);
        assertSame(chunk, WRAP.canon(chunk));
    }

    @Test
    void nearestBlockAcrossSeam() {
        assertEquals(W + 2, WRAP.nearestBlock(2, W - 3));
        assertEquals(-3, WRAP.nearestBlock(W - 3, 2));
        assertEquals(N, WRAP.nearestChunk(0, N - 1));
    }

    @Test
    void unwrappedIsTheIdentity() {
        BlockPos pos = new BlockPos(-5_000_000, 64, 7_000_000);
        assertSame(pos, Wrap.NONE.canon(pos));
        assertEquals(-5_000_000, Wrap.NONE.nearestBlock(-5_000_000, 12));
        assertEquals(ChunkPos.asLong(-300, 900), Wrap.NONE.canonChunkKey(ChunkPos.asLong(-300, 900)));
        assertEquals(5.0, Wrap.NONE.minDelta(10.0, 5.0));
        assertEquals(0, Wrap.NONE.lap(-5_000_000));
    }

    @Test
    void settingsGiveEachDimensionItsPeriod() {
        WorldWrapSettings settings = new WorldWrapSettings(12288, true, false);
        assertEquals(12288, settings.periodFor(Level.OVERWORLD));
        assertEquals(1536, settings.periodFor(Level.NETHER));
        assertEquals(0, settings.periodFor(Level.END));
        assertEquals(0, WorldWrapSettings.DISABLED.periodFor(Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> new WorldWrapSettings(1000, true, false));
    }
}
