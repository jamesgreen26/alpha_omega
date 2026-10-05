package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The copy check (RS §3.7 invariant): every cell of a loaded, filled band or skirt chunk equals its source cell,
 * turned; and (without block entity claims) no band cell holds a block entity of its own.
 */
public final class BandCheck {

    public record Result(int chunks, long cells, long mismatches, List<String> examples) {

        public boolean clean() {
            return this.mismatches == 0;
        }

        @Override
        public String toString() {
            return "copy check: " + this.chunks + " band chunks, " + this.cells + " cells, " + this.mismatches + " mismatches"
                + (this.examples.isEmpty() ? "" : " e.g. " + this.examples);
        }
    }

    private BandCheck() {
    }

    /** Checks the given chunks (tile chunks are skipped; their copies are checked from the band side). */
    public static Result check(ServerLevel level, Collection<ChunkPos> chunks) {
        OrbifoldGeometry geometry = Band.geometry(level);
        int checked = 0;
        long cells = 0, mismatches = 0;
        List<String> examples = new ArrayList<>();
        if (geometry == null) return new Result(0, 0, 0, examples);
        for (ChunkPos pos : chunks) {
            if (geometry.isTileChunk(pos.x, pos.z) || !geometry.inFootprintChunk(pos.x, pos.z)) continue;
            LevelChunk band = level.getChunkSource().getChunkNow(pos.x, pos.z);
            OrbifoldGeometry.Cell source = geometry.canonChunk(pos.x, pos.z);
            LevelChunk from = level.getChunkSource().getChunkNow(source.x(), source.z());
            if (band == null || from == null || !((BandChunk) band).alpha_omega$filled()) continue;
            checked++;
            Motion toSource = source.frame();
            Transform turn = Transform.of(toSource.inverse());
            int baseX = pos.getMinBlockX(), baseZ = pos.getMinBlockZ();
            LevelChunkSection[] to = band.getSections(), of = from.getSections();
            for (int i = 0; i < to.length; i++) {
                if (to[i].hasOnlyAir() && of[i].hasOnlyAir()) {
                    cells += 4096;
                    continue;
                }
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int sx = toSource.cellX(baseX + lx), sz = toSource.cellZ(baseZ + lz);
                        for (int ly = 0; ly < 16; ly++) {
                            cells++;
                            BlockState expected = turn.state(of[i].getBlockState(sx & 15, ly, sz & 15));
                            BlockState actual = to[i].getBlockState(lx, ly, lz);
                            if (expected != actual) {
                                mismatches++;
                                int y = level.getSectionYFromSectionIndex(i) * 16 + ly;
                                if (examples.size() < 8) examples.add(new BlockPos(baseX + lx, y, baseZ + lz).toShortString() + " " + actual
                                    + " vs source " + new BlockPos(sx, y, sz).toShortString() + " " + expected);
                            }
                        }
                    }
                }
            }
            if (!Band.blockEntityClaims) {
                for (BlockPos at : band.getBlockEntities().keySet()) {
                    mismatches++;
                    if (examples.size() < 8) examples.add("block entity at copy " + at.toShortString() + " " + band.getBlockEntities().get(at));
                }
            }
        }
        return new Result(checked, cells, mismatches, examples);
    }

    /** Checks every band chunk within {@code radius} chunks of {@code center}. */
    public static Result check(ServerLevel level, ChunkPos center, int radius) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) chunks.add(new ChunkPos(center.x + dx, center.z + dz));
        }
        return check(level, chunks);
    }
}
