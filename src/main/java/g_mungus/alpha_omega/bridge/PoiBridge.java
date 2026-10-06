package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.CopyLinks;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * POIs (RS §5): a POI is recorded once, at the copy that owns its cell (phase 4). Two kinds of query need to see it from
 * its other copies:
 * <ul>
 * <li><b>Area queries</b> ({@code getInSquare}, under {@code getInRange}, {@code find*}, {@code take},
 * {@code getRandom} and the counts): a square near an edge also searches its images, and each record found there is
 * returned at the copy of its cell in the querier's frame ({@link ImagePoiRecord}), if that copy is valid storage. Its
 * tickets are the owner record's, so a claim through it is the one claim.</li>
 * <li><b>Point queries</b> ({@code exists}, {@code getType}, {@code release}, {@code getFreeTickets}) at a copy look up
 * the owner's cell, so a villager's home or job site held at a band copy passes vanilla's validation
 * ({@code ValidateNearbyPoi}, {@code AcquirePoi}, profession checks).</li>
 * </ul>
 * Away from the edges both cost four comparisons.
 */
public final class PoiBridge {

    private PoiBridge() {
    }

    /** The cell that holds the POI record for {@code pos}'s cell: its owner copy, or {@code pos} itself. */
    public static BlockPos owner(LevelHeightAccessor heights, BlockPos pos) {
        if (!(heights instanceof ServerLevel level)) return pos;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null || !Band.isLinked(geometry, pos.getX(), pos.getZ())) return pos;
        BlockPos owner;
        LevelChunk chunk = Band.loadedChunk(level, pos);
        if (chunk == null) {
            // Not loaded: nominally the tile copy owns.
            OrbifoldGeometry.Cell source = geometry.canon(pos.getX(), pos.getZ());
            owner = new BlockPos(source.x(), pos.getY(), source.z());
        } else {
            CopyLinks.Link link = Ownership.owner(level, chunk, pos);
            owner = link == null ? pos : link.map(pos);
        }
        if (!owner.equals(pos)) BridgeCounters.run(BridgeCounters.Kind.POI_OWNER, pos.getX(), pos.getZ(), 0.0);
        return owner;
    }

    /**
     * {@code getInSquare}'s records plus those found in the square's images, mapped into its frame. Unchanged away from
     * the edges.
     */
    public static Stream<PoiRecord> withImages(PoiManager manager, LevelHeightAccessor heights, Stream<PoiRecord> direct, Predicate<Holder<PoiType>> type,
        BlockPos center, int radius, PoiManager.Occupancy occupancy) {
        if (!(heights instanceof ServerLevel level)) return direct;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return direct;
        List<Motion> images = Images.around(geometry, center.getX() + 0.5, center.getZ() + 0.5, radius + 1.0);
        if (images.isEmpty()) return direct;
        BridgeCounters.run(BridgeCounters.Kind.POI, center.getX(), center.getZ(), radius);
        List<PoiRecord> records = direct.collect(Collectors.toCollection(java.util.ArrayList::new));
        Set<PoiRecord> owners = new ReferenceOpenHashSet<>(records);
        long found = 0;
        for (Motion g : images) {
            BlockPos at = Transform.of(g).block(center);
            Transform back = Transform.of(g.inverse());
            int chunks = Math.floorDiv(radius, 16) + 1;
            for (ChunkPos chunk : (Iterable<ChunkPos>) ChunkPos.rangeClosed(new ChunkPos(at), chunks)::iterator) {
                for (PoiRecord record : (Iterable<PoiRecord>) manager.getInChunk(type, chunk, occupancy)::iterator) {
                    BlockPos pos = record.getPos();
                    if (Math.abs(pos.getX() - at.getX()) > radius || Math.abs(pos.getZ() - at.getZ()) > radius) continue;
                    BlockPos mapped = back.block(pos);
                    // Only where the querier's frame holds the cell (tile or band), so it can walk there.
                    if (geometry.cellDepth(mapped.getX(), mapped.getZ()) > geometry.band) continue;
                    if (!owners.add(record)) continue;
                    records.add(new ImagePoiRecord(record, mapped));
                    found++;
                }
            }
        }
        BridgeCounters.found(BridgeCounters.Kind.POI, found);
        return records.stream();
    }
}
