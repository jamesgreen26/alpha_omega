package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.mixin.band.ChunkAccessAccessor;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.ticks.LevelChunkTicks;

/**
 * Filling the band (RS §6.3, §3.7): when a band or skirt chunk is promoted to a full chunk, its cells are made equal to
 * their owners' (the source's, unless a copy claims them), turned. Whatever the generator put there is discarded; for a
 * chunk loaded from disk this only repairs what changed while it was away. Light is rechecked at every changed cell,
 * heightmaps are primed, and block entities, generation ticks and post-processing at cells the chunk does not own are
 * dropped.
 *
 * <p>Ownership masks are reconciled first: if the stamps of a pair differ (a crash between two saves), the newer chunk's
 * masks win (RS §3.7 "Recovery"). Then non-owner cells take the owner's content, which in the normal case is "the band
 * copy takes the source's".
 *
 * <p>The same refresh runs when a tile chunk loads while some of its copies are still loaded.
 */
public final class BandFill {

    private static final Set<Heightmap.Types> HEIGHTMAPS = EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE);
    /** Band chunks the gate let through unfilled (its fallback), to fill once their source is loaded; per level. */
    private static final Map<ServerLevel, LongLinkedOpenHashSet> PENDING = new WeakHashMap<>();

    private BandFill() {
    }

    /** From {@code LevelChunk.registerAllBlockEntitiesAfterLevelLoad}: the chunk is joining its level. Main thread. */
    public static void promote(ServerLevel level, LevelChunk chunk) {
        CopyLinks links = Band.links(chunk);
        if (links.isEmpty()) return;
        BandChunk band = (BandChunk) chunk;
        if (!links.band) {
            band.alpha_omega$setFilled(true);
            for (CopyLinks.Link link : links.links) {
                LevelChunk copy = link.chunk(level);
                if (copy != null && Band.filled(copy)) {
                    BandCounters.liveRefreshes++;
                    refresh(level, copy, false, true);
                }
            }
            return;
        }
        BandGate.release(level, chunk.getPos());
        CopyLinks.Link source = links.source();
        if (source == null || source.chunk(level) == null) {
            band.alpha_omega$setFilled(false);
            PENDING.computeIfAbsent(level, l -> new LongLinkedOpenHashSet()).add(chunk.getPos().toLong());
            BandCounters.gateFallbacks++;
            return;
        }
        long start = System.nanoTime();
        refresh(level, chunk, band.alpha_omega$fresh(), false);
        band.alpha_omega$setFilled(true);
        BandCounters.gateFills++;
        BandCounters.fillNanos += System.nanoTime() - start;
    }

    /** Fills chunks the gate let through unfilled, once their sources are loaded. Each level tick. */
    public static void flush(ServerLevel level) {
        LongLinkedOpenHashSet pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) return;
        LongIterator it = pending.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null || Band.links(chunk).isEmpty()) {
                it.remove();
                continue;
            }
            CopyLinks.Link source = Band.links(chunk).source();
            if (source == null || source.chunk(level) == null) continue;
            refresh(level, chunk, false, true);
            ((BandChunk) chunk).alpha_omega$setFilled(true);
            BandCounters.lateFills++;
            it.remove();
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    /**
     * Brings band chunk {@code chunk} in step with its copies. {@code fresh}: its content came from the generator, so its
     * scheduled ticks go too. {@code live}: it is already in its level, so changed cells are sent to players.
     */
    public static void refresh(ServerLevel level, LevelChunk chunk, boolean fresh, boolean live) {
        CopyLinks links = Band.links(chunk);
        CopyLinks.Link sourceLink = links.source();
        if (sourceLink == null) return;
        LevelChunk source = sourceLink.chunk(level);
        if (source == null) return;
        BandData data = ((BandChunk) chunk).alpha_omega$data(true);
        long self = chunk.getPos().toLong();

        // 1. Masks and stamps, pair by pair.
        boolean sourceNewer = false;
        for (CopyLinks.Link link : links.links) {
            LevelChunk copy = link.chunk(level);
            if (copy == null) continue;
            BandData other = ((BandChunk) copy).alpha_omega$data(true);
            long mine = data.stamp(link.key), theirs = other.stamp(self);
            if (mine != theirs) {
                BandCounters.stampMismatches++;
                mergeMasks(level, chunk, data, links, link, copy, other, mine > theirs);
                if (link.toTile && theirs > mine) sourceNewer = true;
            }
            long stamp = Math.max(mine, theirs);
            data.setStamp(link.key, stamp);
            other.setStamp(self, stamp);
            copy.setUnsaved(true);
        }

        // 2. Cells: non-owner cells take their owner's content. A cell this chunk owns pushes its content to the copies,
        // unless the source is newer (this chunk missed writes before a crash): every write was mirrored, so the source
        // has the cell's latest content, and the owner takes it.
        BandData sourceData = ((BandChunk) source).alpha_omega$data(true);
        LevelChunkSection[] to = chunk.getSections(), from = source.getSections();
        boolean turned = sourceLink.turned;
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LongArrayList lightChecks = new LongArrayList();
        int changed = 0;
        for (int i = 0; i < to.length; i++) {
            LevelChunkSection target = to[i], origin = from[i];
            boolean bulk = !data.hasFlips(i) && !sourceData.hasFlips(i);
            if (bulk && target.hasOnlyAir() && origin.hasOnlyAir()) continue;
            boolean wasEmpty = target.hasOnlyAir();
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        pos.set(baseX + lx, baseY + ly, baseZ + lz);
                        BlockState want;
                        if (bulk) {
                            want = Band.turn(origin.getBlockState(sourceLink.local(lx), ly, sourceLink.local(lz)), turned);
                        } else {
                            int cell = BandData.cell(lx, ly, lz);
                            if (data.flip(i, cell) && !sourceNewer) {
                                pushOwned(level, chunk, links, pos, target.getBlockState(lx, ly, lz));
                                continue;
                            }
                            want = data.flip(i, cell)
                                ? Band.turn(origin.getBlockState(sourceLink.local(lx), ly, sourceLink.local(lz)), turned)
                                : ownersState(level, links, sourceLink, source, sourceData, i, lx, ly, lz);
                        }
                        BlockState have = target.getBlockState(lx, ly, lz);
                        if (want == have) continue;
                        target.setBlockState(lx, ly, lz, want, false);
                        changed++;
                        if (LightEngine.hasDifferentLightProperties(chunk, pos, have, want)) lightChecks.add(pos.asLong());
                        if (live) level.getChunkSource().blockChanged(pos);
                    }
                }
            }
            if (wasEmpty != target.hasOnlyAir()) {
                level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(i)), target.hasOnlyAir());
            }
        }
        if (changed > 0) {
            Heightmap.primeHeightmaps(chunk, HEIGHTMAPS);
            chunk.getSkyLightSources().fillFrom(chunk);
            for (int i = 0; i < lightChecks.size(); i++) level.getChunkSource().getLightEngine().checkBlock(BlockPos.of(lightChecks.getLong(i)));
            chunk.setUnsaved(true);
            BandCounters.cellsFilled += changed;
            BandCounters.lightChecks += lightChecks.size();
        }

        // 3. Block entities only at owners; generation leftovers go.
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos at : chunk.getBlockEntities().keySet()) {
            if (!Ownership.isOwner(chunk, at)) stale.add(at);
        }
        for (BlockPos at : stale) chunk.removeBlockEntity(at);
        ((ChunkAccessAccessor) chunk).alpha_omega$pendingBlockEntities().keySet().removeIf(at -> !Ownership.isOwner(chunk, at));
        BandCounters.staleBlockEntitiesRemoved += stale.size();
        for (ShortList list : chunk.getPostProcessing()) {
            if (list != null) list.clear();
        }
        if (fresh) {
            if (chunk.getBlockTicks() instanceof LevelChunkTicks<?> ticks) ticks.removeIf(tick -> true);
            if (chunk.getFluidTicks() instanceof LevelChunkTicks<?> ticks) ticks.removeIf(tick -> true);
        }
    }

    /** The state a non-owned cell of a band chunk should hold: its owner's, turned. */
    private static BlockState ownersState(ServerLevel level, CopyLinks links, CopyLinks.Link sourceLink, LevelChunk source, BandData sourceData, int section,
        int lx, int ly, int lz) {
        int sx = sourceLink.local(lx), sz = sourceLink.local(lz);
        if (sourceData.flip(section, BandData.cell(sx, ly, sz))) {
            // The source is flipped: a sibling copy owns the cell, if one is loaded.
            for (CopyLinks.Link link : links.links) {
                if (link.toTile) continue;
                LevelChunk copy = link.chunk(level);
                if (copy == null) continue;
                BandData copyData = ((BandChunk) copy).alpha_omega$data(false);
                int cx = link.local(lx), cz = link.local(lz);
                if (copyData != null && copyData.flip(section, BandData.cell(cx, ly, cz))) {
                    return Band.turn(copy.getSections()[section].getBlockState(cx, ly, cz), link.turned);
                }
            }
        }
        return Band.turn(source.getSections()[section].getBlockState(sx, ly, sz), sourceLink.turned);
    }

    /** A cell the band chunk owns: its content goes to every loaded copy that differs. */
    private static void pushOwned(ServerLevel level, LevelChunk chunk, CopyLinks links, BlockPos pos, BlockState state) {
        for (CopyLinks.Link link : links.links) {
            LevelChunk copy = link.chunk(level);
            if (copy == null) continue;
            BlockPos at = link.map(pos);
            BlockState want = Band.turn(state, link.turned);
            if (copy.getBlockState(at) == want) continue;
            BandWrites.asMirror(() -> copy.setBlockState(at, want, false));
            level.getChunkSource().blockChanged(at);
            BandCounters.cellsFilled++;
        }
    }

    /**
     * Merges the masks of a pair whose stamps differ: the newer chunk's claims win. {@code chunk} is the band chunk;
     * {@code copy} its source or a sibling band copy.
     */
    private static void mergeMasks(ServerLevel level, LevelChunk chunk, BandData data, CopyLinks links, CopyLinks.Link link, LevelChunk copy, BandData other,
        boolean chunkNewer) {
        int sections = Math.max(data.sections(), other.sections());
        for (int i = 0; i < sections; i++) {
            if (!data.hasFlips(i) && !other.hasFlips(i)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int cell = 0; cell < 4096; cell++) {
                int lx = cell & 15, lz = cell >> 4 & 15, ly = cell >> 8;
                int otherCell = BandData.cell(link.local(lx), ly, link.local(lz));
                boolean mine = data.flip(i, cell), theirs = other.flip(i, otherCell);
                if (link.toTile) {
                    // theirs: the source is flipped away (some copy owns).
                    if (chunkNewer) {
                        if (mine && !theirs) {
                            other.setFlip(i, otherCell, true);
                            removeBlockEntity(copy, link.map(new BlockPos(chunk.getPos().getMinBlockX() + lx, baseY + ly, chunk.getPos().getMinBlockZ() + lz)));
                        } else if (!mine && theirs && !siblingClaims(level, links, i, lx, ly, lz)) {
                            other.setFlip(i, otherCell, false);
                        }
                    } else if (mine && !theirs) {
                        data.setFlip(i, cell, false);
                    }
                } else if (mine && theirs) {
                    // Two band copies both claim: the newer keeps it.
                    if (chunkNewer) {
                        other.setFlip(i, otherCell, false);
                        removeBlockEntity(copy, link.map(new BlockPos(chunk.getPos().getMinBlockX() + lx, baseY + ly, chunk.getPos().getMinBlockZ() + lz)));
                    } else {
                        data.setFlip(i, cell, false);
                    }
                }
            }
        }
    }

    private static boolean siblingClaims(ServerLevel level, CopyLinks links, int section, int lx, int ly, int lz) {
        for (CopyLinks.Link link : links.links) {
            if (link.toTile) continue;
            LevelChunk copy = link.chunk(level);
            BandData data = copy == null ? null : ((BandChunk) copy).alpha_omega$data(false);
            if (data != null && data.flip(section, BandData.cell(link.local(lx), ly, link.local(lz)))) return true;
        }
        return false;
    }

    private static void removeBlockEntity(LevelChunk chunk, BlockPos pos) {
        if (chunk.getBlockEntities().containsKey(pos)) {
            chunk.removeBlockEntity(pos);
            BandCounters.staleBlockEntitiesRemoved++;
        }
        ((ChunkAccessAccessor) chunk).alpha_omega$pendingBlockEntities().remove(pos);
    }
}
