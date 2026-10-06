package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The copy check (RS §3.7 invariant): for every loaded, filled band or skirt chunk and each loaded chunk it links to,
 * every cell equals its copy turned; each cell has exactly one owner among its loaded copies, and the tile copy's mask
 * agrees; block entities sit only at owners; and no air cell is claimed, nor any cell beyond {@code C} without a block
 * entity (RS §3.3).
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

    private static final class Tally {
        int chunks;
        long cells;
        long mismatches;
        final List<String> examples = new ArrayList<>();

        void wrong(String what) {
            this.mismatches++;
            if (this.examples.size() < 8) this.examples.add(what);
        }
    }

    /** Checks the given chunks (tile chunks are checked from their copies' side). */
    public static Result check(ServerLevel level, Collection<ChunkPos> chunks) {
        OrbifoldGeometry geometry = Band.geometry(level);
        Tally tally = new Tally();
        if (geometry == null) return new Result(0, 0, 0, tally.examples);
        for (ChunkPos pos : chunks) {
            if (geometry.isTileChunk(pos.x, pos.z) || !geometry.inFootprintChunk(pos.x, pos.z)) continue;
            LevelChunk band = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (band == null || !Band.filled(band)) continue;
            checkChunk(level, geometry, band, tally);
        }
        return new Result(tally.chunks, tally.cells, tally.mismatches, tally.examples);
    }

    /** Checks every band chunk within {@code radius} chunks of {@code center}. */
    public static Result check(ServerLevel level, ChunkPos center, int radius) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) chunks.add(new ChunkPos(center.x + dx, center.z + dz));
        }
        return check(level, chunks);
    }

    /** Checks every loaded band chunk. */
    public static Result checkLoaded(ServerLevel level) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (ChunkHolder holder : ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$visibleChunks()) chunks.add(holder.getPos());
        return check(level, chunks);
    }

    private static void checkChunk(ServerLevel level, OrbifoldGeometry geometry, LevelChunk band, Tally tally) {
        CopyLinks links = Band.links(band);
        tally.chunks++;
        int baseX = band.getPos().getMinBlockX(), baseZ = band.getPos().getMinBlockZ();
        BandData data = ((BandChunk) band).alpha_omega$data(false);
        LevelChunkSection[] sections = band.getSections();
        for (CopyLinks.Link link : links.links) {
            LevelChunk copy = link.chunk(level);
            if (copy == null || !Band.filled(copy)) continue;
            LevelChunkSection[] others = copy.getSections();
            for (int i = 0; i < sections.length; i++) {
                if (sections[i].hasOnlyAir() && others[i].hasOnlyAir()) {
                    tally.cells += 4096;
                    continue;
                }
                int baseY = band.getSectionYFromSectionIndex(i) << 4;
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            tally.cells++;
                            BlockState here = sections[i].getBlockState(lx, ly, lz);
                            BlockState there = others[i].getBlockState(link.local(lx), ly, link.local(lz));
                            if (Band.turn(here, link.turned) != there) {
                                BlockPos at = new BlockPos(baseX + lx, baseY + ly, baseZ + lz);
                                tally.wrong(at.toShortString() + " " + here + " vs copy " + link.map(at).toShortString() + " " + there);
                            }
                        }
                    }
                }
            }
        }
        // Ownership: exactly one owner among the loaded copies of every cell with any flip; the source's mask agrees.
        CopyLinks.Link sourceLink = links.source();
        LevelChunk source = sourceLink == null ? null : sourceLink.chunk(level);
        BandData sourceData = source == null ? null : ((BandChunk) source).alpha_omega$data(false);
        int sectionCount = Math.max(data == null ? 0 : data.sections(), sourceData == null ? 0 : sourceData.sections());
        for (int i = 0; i < sectionCount; i++) {
            if ((data == null || !data.hasFlips(i)) && (sourceData == null || !sourceData.hasFlips(i))) continue;
            int baseY = band.getSectionYFromSectionIndex(i) << 4;
            for (int cell = 0; cell < 4096; cell++) {
                int lx = cell & 15, lz = cell >> 4 & 15, ly = cell >> 8;
                BlockPos at = new BlockPos(baseX + lx, baseY + ly, baseZ + lz);
                int owners = 0;
                boolean allLoaded = true, siblingClaims = false;
                if (data != null && data.flip(i, cell)) owners++;
                for (CopyLinks.Link link : links.links) {
                    LevelChunk copy = link.chunk(level);
                    if (copy == null) {
                        allLoaded = false;
                        continue;
                    }
                    if (link.toTile) {
                        if (Ownership.isOwner(copy, link.map(at))) owners++;
                    } else if (Ownership.isOwner(copy, link.map(at))) {
                        owners++;
                        siblingClaims = true;
                    }
                }
                boolean claimed = (data != null && data.flip(i, cell)) || siblingClaims;
                if (owners > 1 || (allLoaded && owners != 1)) tally.wrong(at.toShortString() + " has " + owners + " owners");
                if (source != null && allLoaded && Ownership.isOwner(source, sourceLink.map(at)) == claimed) {
                    tally.wrong(at.toShortString() + ": the source's mask disagrees with the copies' claims");
                }
                // Claims (RS §3.3): air is always nominal; past C only a block entity holds a claim.
                if (data != null && data.flip(i, cell)) {
                    if (band.getBlockState(at).isAir()) tally.wrong(at.toShortString() + " is air but claimed");
                    else if (!Claims.inClaimZone(geometry, band, at) && !band.getBlockEntities().containsKey(at)) {
                        tally.wrong(at.toShortString() + " is claimed beyond C without a block entity");
                    }
                }
            }
        }
        // Block entities only at owners, at this chunk and at its source.
        for (BlockPos at : band.getBlockEntities().keySet()) {
            if (!Ownership.isOwner(band, at)) tally.wrong("block entity at non-owner copy " + at.toShortString() + " " + band.getBlockEntities().get(at));
        }
        if (source != null) {
            for (BlockPos at : source.getBlockEntities().keySet()) {
                if (!Ownership.isOwner(source, at)) tally.wrong("block entity at non-owner source cell " + at.toShortString());
            }
        }
    }
}
