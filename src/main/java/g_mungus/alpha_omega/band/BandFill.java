package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.mixin.band.ChunkAccessAccessor;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Filling the band (RS §6.3): a band or skirt chunk is overwritten from its source chunk, turned, whenever it loads with
 * its source loaded, or its source loads while it is loaded. Whatever the generator put there is discarded: block
 * entities, pending block entities and scheduled ticks with it (the owner holds those).
 *
 * <p>Spike limits: light is not recomputed in the filled chunk, and players already tracking it are not re-sent it.
 */
public final class BandFill {

    /** Chunks (band or tile) that loaded and may have a fill to do. */
    private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();

    private BandFill() {
    }

    /** From the chunk load event: remember the chunk, and fill it (or its copies) at the next flush. */
    public static void loaded(ServerLevel level, LevelChunk chunk) {
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || !((BandChunk) chunk).alpha_omega$linked()) return;
        ChunkPos pos = chunk.getPos();
        if (geometry.isTileChunk(pos.x, pos.z)) {
            for (OrbifoldGeometry.Cell copy : geometry.copiesChunk(pos.x, pos.z)) PENDING.add(ChunkPos.asLong(copy.x(), copy.z()));
        } else {
            ((BandChunk) chunk).alpha_omega$setFilled(false);
            PENDING.add(pos.toLong());
        }
    }

    /** Fills every pending band chunk whose source is loaded. Runs at the start of each level tick, and from tests. */
    public static void flush(ServerLevel level) {
        if (PENDING.isEmpty()) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        List<Long> done = new ArrayList<>();
        for (long key : PENDING) {
            int x = ChunkPos.getX(key), z = ChunkPos.getZ(key);
            LevelChunk band = level.getChunkSource().getChunkNow(x, z);
            if (band == null) {
                done.add(key);
                continue;
            }
            OrbifoldGeometry.Cell source = geometry.canonChunk(x, z);
            LevelChunk from = level.getChunkSource().getChunkNow(source.x(), source.z());
            if (from == null) continue;
            fill(level, geometry, band, from, source.frame());
            done.add(key);
        }
        done.forEach(PENDING::remove);
    }

    /** Fills a band or skirt chunk from its source now, if both are loaded. */
    public static boolean fillNow(ServerLevel level, ChunkPos pos) {
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || geometry.isTileChunk(pos.x, pos.z) || !geometry.inFootprintChunk(pos.x, pos.z)) return false;
        LevelChunk band = level.getChunkSource().getChunkNow(pos.x, pos.z);
        OrbifoldGeometry.Cell source = geometry.canonChunk(pos.x, pos.z);
        LevelChunk from = level.getChunkSource().getChunkNow(source.x(), source.z());
        if (band == null || from == null) return false;
        fill(level, geometry, band, from, source.frame());
        PENDING.remove(pos.toLong());
        return true;
    }

    private static void fill(ServerLevel level, OrbifoldGeometry geometry, LevelChunk band, LevelChunk source, Motion toSource) {
        Transform turn = Transform.of(toSource.inverse());
        int baseX = band.getPos().getMinBlockX(), baseZ = band.getPos().getMinBlockZ();
        LevelChunkSection[] to = band.getSections(), from = source.getSections();
        for (int i = 0; i < to.length; i++) {
            LevelChunkSection target = to[i], origin = from[i];
            if (origin.hasOnlyAir() && target.hasOnlyAir()) continue;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int sx = toSource.cellX(baseX + lx) & 15, sz = toSource.cellZ(baseZ + lz) & 15;
                    for (int ly = 0; ly < 16; ly++) {
                        BlockState state = origin.hasOnlyAir() ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : origin.getBlockState(sx, ly, sz);
                        target.setBlockState(lx, ly, lz, turn.state(state), false);
                    }
                }
            }
            target.recalcBlockCounts();
        }
        Heightmap.primeHeightmaps(band, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        for (BlockPos pos : new ArrayList<>(band.getBlockEntities().keySet())) band.removeBlockEntity(pos);
        ((ChunkAccessAccessor) band).alpha_omega$pendingBlockEntities().clear();
        // Generation's post-processing (shape fixes and fluid ticks at promotion) belongs to the discarded content: left
        // in place it would run on the copied cells, and its writes would mirror into the source.
        for (var list : band.getPostProcessing()) {
            if (list != null) list.clear();
        }
        BoundingBox box = new BoundingBox(baseX, level.getMinBuildHeight(), baseZ, baseX + 15, level.getMaxBuildHeight() - 1, baseZ + 15);
        level.getBlockTicks().clearArea(box);
        level.getFluidTicks().clearArea(box);
        band.setUnsaved(true);
        ((BandChunk) band).alpha_omega$setFilled(true);
        BandCounters.bandFills++;
    }
}
