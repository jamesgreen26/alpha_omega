package g_mungus.alpha_omega.wrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void centeredWindowStraddlesTheOrigin() {
        Wrap centered = new Wrap(W, true);
        assertEquals(-W / 2, centered.minBlock);
        assertEquals(-N / 2, centered.minChunk);
        // Spawn is lap 0 on both sides of the origin; the seam is at +-W/2.
        BlockPos spawn = new BlockPos(-5, 64, 7);
        assertSame(spawn, centered.canon(spawn));
        assertEquals(0, centered.lap(-5));
        assertEquals(0, centered.lap(W / 2 - 1));
        assertEquals(1, centered.lap(W / 2));
        assertEquals(-1, centered.lap(-W / 2 - 1));
        assertEquals(-W / 2, centered.canonBlock(W / 2));
        assertEquals(W / 2 - 1, centered.canonBlock(-W / 2 - 1));
        assertEquals(-N / 2, centered.canonChunk(N / 2));
        assertEquals(1, centered.chunkLap(N / 2));
        assertEquals(-W / 2 + 0.25, centered.canon(W / 2 + 0.25));
        assertEquals(W / 2 - 0.75, centered.canon(-W / 2 - 0.75));
        long key = centered.canonChunkKey(ChunkPos.asLong(N / 2 + 3, -N / 2 - 1));
        assertEquals(-N / 2 + 3, ChunkPos.getX(key));
        assertEquals(N / 2 - 1, ChunkPos.getZ(key));
        // Canonical position plus lap times the period gets back the original, as in the old window.
        for (int x = -3 * W; x <= 3 * W; x += 997) {
            assertEquals(x, centered.canonBlock(x) + centered.lap(x) * W);
        }
        // Nearest images do not depend on the window.
        assertEquals(WRAP.nearestBlock(2, W - 3), centered.nearestBlock(2, W - 3));
    }

    @Test
    void settingsRecordTheWindow(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(WorldWrapSettings.FILE_NAME);
        WorldWrapSettings settings = new WorldWrapSettings(12288, true, false, true);
        settings.write(file);
        assertEquals(settings, WorldWrapSettings.read(file));
        assertEquals(-768, new Wrap(settings.periodFor(Level.NETHER), settings.centered()).minBlock);
        // Worlds recorded before the window could be centered keep [0, W).
        Files.writeString(file, "{\"period\":12288,\"nether\":true,\"end\":false}");
        assertEquals(new WorldWrapSettings(12288, true, false, false), WorldWrapSettings.read(file));
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
    void offTorusCoordinatesAreLeftAlone() {
        // Sable's plot grid: chunks [1,280,000, 1,296,384).
        int plotChunk = 1_280_000;
        int plotBlock = plotChunk << 4;
        BlockPos plot = new BlockPos(plotBlock + 5, 70, plotBlock + 9);
        assertEquals(new BlockPos(Math.floorMod(plotBlock + 5, W), 70, Math.floorMod(plotBlock + 9, W)), WRAP.canon(plot), "nothing is excluded by default");
        Wrap.excludeFromTorus(plotChunk, plotChunk + 128 * 128);
        try {
            assertSame(plot, WRAP.canon(plot));
            assertEquals(ChunkPos.asLong(plotChunk, plotChunk + 3), WRAP.canonChunkKey(ChunkPos.asLong(plotChunk, plotChunk + 3)));
            assertEquals(SectionPos.asLong(plotChunk, 4, plotChunk), WRAP.canonSectionKey(SectionPos.asLong(plotChunk, 4, plotChunk)));
            assertEquals(BlockPos.asLong(plotBlock, 4, plotBlock), WRAP.canonBlockKey(BlockPos.asLong(plotBlock, 4, plotBlock)));
            assertEquals(plotBlock + 0.5, WRAP.canon(plotBlock + 0.5));
            assertEquals(0, WRAP.lap(plotBlock));
            // Neither side of a bridge moves when either is off the torus.
            assertEquals(plotBlock, WRAP.nearestBlock(plotBlock, 100));
            assertEquals(100, WRAP.nearestBlock(100, plotBlock));
            assertEquals(plotChunk, WRAP.nearestChunk(plotChunk, 3));
            assertEquals(plotBlock + 0.5, WRAP.nearest(plotBlock + 0.5, 3.0));
            assertEquals(plotBlock - 3.0, WRAP.minDelta(plotBlock, 3.0));
            assertEquals(plotChunk - 3, WRAP.minChunkDelta(plotChunk, 3));
            assertEquals(0, WRAP.lapOffset(10, plotBlock));
            // Just outside the range still wraps.
            assertEquals(Math.floorMod(plotBlock - 1, W), WRAP.canonBlock(plotBlock - 1));
            assertEquals(Math.floorMod(-plotBlock, W), WRAP.canonBlock(-plotBlock));
        } finally {
            Wrap.excludeFromTorus(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
    }

    @Test
    void settingsGiveEachDimensionItsPeriod() {
        WorldWrapSettings settings = new WorldWrapSettings(12288, true, false, true);
        assertEquals(12288, settings.periodFor(Level.OVERWORLD));
        assertEquals(1536, settings.periodFor(Level.NETHER));
        assertEquals(0, settings.periodFor(Level.END));
        assertEquals(0, WorldWrapSettings.DISABLED.periodFor(Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> new WorldWrapSettings(1000, true, false, true));
    }
}
