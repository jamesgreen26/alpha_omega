package g_mungus.alpha_omega.band;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Counters for {@code /orbifold scan} and gametests. Server thread only. Two must stay 0: {@link #ranAtNonOwner}
 * (reactions that reached a non-owner copy past every hook, counted only with detectors on) and
 * {@link #gateViolations} (a band chunk that was full, ticking or sent before it was filled).
 */
public final class BandCounters {

    // Writes
    public static long mirroredWrites;
    /** A write whose copy's chunk was not loaded (or not filled): the copy is refreshed when it next loads. */
    public static long mirrorsMissed;
    /** {@link #mirrorsMissed} by level (the overworld and the Nether each have a band). */
    public static final Map<String, Long> mirrorsMissedIn = new java.util.concurrent.ConcurrentHashMap<>();
    public static long copyPackets;
    // Forwarding
    public static long neighbourForwarded;
    public static long shapeForwarded;
    public static long comparatorForwarded;
    /** An update for a non-owner copy whose owner's chunk was not loaded. */
    public static long forwardsDropped;
    public static long poiRedirected;
    public static long capabilityRedirects;
    public static long blockEntityRedirects;
    public static long replicasSent;
    // Skipped at non-owners
    public static long randomTicksSkipped;
    public static long precipitationSkipped;
    public static long tickersSkipped;
    public static long scheduledTicksDeduped;
    public static long poiScanSkipped;
    // Ownership
    public static long claims;
    public static long releases;
    /** Placements in a claim zone, those that claimed a band cell, and those that took a cell back for the tile copy. */
    public static long placements;
    public static long placementClaims;
    public static long placementReturns;
    /** Placements at a band copy beyond {@code C}: the owner is unchanged. */
    public static long placementsBeyondClaim;
    /** Block entities a write at a non-owner copy would have created there, created at the owner instead. */
    public static long blockEntitiesAtOwner;
    /** Block entities set explicitly at a non-owner copy, which claimed the cell. */
    public static long blockEntityClaims;
    public static long staleBlockEntitiesRemoved;
    public static long ownershipUnresolved;
    // Promotion
    public static long gateWaits;
    public static long gateHeld;
    public static long gateFills;
    public static long gateFallbacks;
    public static long lateFills;
    public static long liveRefreshes;
    public static long cellsFilled;
    public static long lightChecks;
    public static long fillNanos;
    public static long stampMismatches;
    public static long pairedTicketsAdded;
    /** Promotion gate breaches, by kind: a band chunk loaded, made full, ticked or sent while unfilled. Must stay empty. */
    public static final Map<String, Long> gateViolations = new TreeMap<>();
    // Detectors (dev)
    /** Shape updates computed by {@code Block.updateFromNeighbourShapes} at a non-owner: the writer working out the state it places. */
    public static long placementShapes;
    /** Depth of {@code Block.updateFromNeighbourShapes} calls in progress. */
    public static int placementDepth;
    /** Reactions that ran at a non-owner copy, by kind. Must stay empty. */
    public static final Map<String, Long> ranAtNonOwner = new TreeMap<>();
    /** The first few places they ran, for the report. */
    public static final List<String> ranAtNonOwnerWhere = new ArrayList<>();

    private BandCounters() {
    }

    /** A write at {@code level} whose copy was not loaded. */
    public static void mirrorMissed(net.minecraft.world.level.Level level) {
        mirrorsMissed++;
        mirrorsMissedIn.merge(level.dimension().location().toString(), 1L, Long::sum);
    }

    /** {@link #mirrorsMissed} in one level. */
    public static long mirrorsMissedIn(net.minecraft.world.level.Level level) {
        return mirrorsMissedIn.getOrDefault(level.dimension().location().toString(), 0L);
    }

    public static void reset() {
        mirroredWrites = mirrorsMissed = copyPackets = 0;
        mirrorsMissedIn.clear();
        neighbourForwarded = shapeForwarded = comparatorForwarded = forwardsDropped = poiRedirected = capabilityRedirects = blockEntityRedirects = replicasSent = 0;
        randomTicksSkipped = precipitationSkipped = tickersSkipped = scheduledTicksDeduped = poiScanSkipped = 0;
        claims = releases = staleBlockEntitiesRemoved = ownershipUnresolved = 0;
        placements = placementClaims = placementReturns = placementsBeyondClaim = blockEntitiesAtOwner = blockEntityClaims = 0;
        gateWaits = gateHeld = gateFills = gateFallbacks = lateFills = liveRefreshes = cellsFilled = lightChecks = fillNanos = stampMismatches = 0;
        pairedTicketsAdded = placementShapes = 0;
        gateViolations.clear();
        ranAtNonOwner.clear();
        ranAtNonOwnerWhere.clear();
    }

    public static long ranAtNonOwnerTotal() {
        return ranAtNonOwner.values().stream().mapToLong(Long::longValue).sum();
    }

    public static long gateViolationTotal() {
        return gateViolations.values().stream().mapToLong(Long::longValue).sum();
    }

    public static void gateViolation(String kind) {
        gateViolations.merge(kind, 1L, Long::sum);
    }

    /** Detector: a reaction of {@code kind} is about to run at {@code pos}; counts it if that is a non-owner copy. */
    public static void reaction(Level level, BlockPos pos, String kind) {
        if (level.isClientSide || Band.geometry(level) == null) return;
        if (Ownership.isOwner(level, pos)) return;
        if (placementDepth > 0 && kind.equals("updateShape")) {
            placementShapes++;
            return;
        }
        ranAtNonOwner.merge(kind, 1L, Long::sum);
        if (ranAtNonOwnerWhere.size() < 20) {
            ranAtNonOwnerWhere.add(kind + " at " + pos.toShortString() + " (tick " + level.getGameTime() + ")" + (ranAtNonOwnerWhere.size() < 3 ? " via " + caller() : ""));
        }
    }

    /** The vanilla frames that led here, innermost first, without our own. */
    private static String caller() {
        List<String> frames = new ArrayList<>();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("java.") || name.startsWith("g_mungus.alpha_omega.band")) continue;
            frames.add(name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName());
            if (frames.size() >= 8) break;
        }
        return String.join(" < ", frames);
    }

    /** Claims held in the loaded band chunks: cells owned by a band copy, and of those, how many beyond {@code C}. */
    public static String claimedCells(net.minecraft.server.level.ServerLevel level) {
        g_mungus.alpha_omega.orbifold.OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return "Claimed cells: not an orbifold";
        long cells = 0, beyond = 0;
        int chunks = 0;
        for (net.minecraft.server.level.ChunkHolder holder : ((g_mungus.alpha_omega.mixin.server.ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$visibleChunks()) {
            net.minecraft.world.level.chunk.LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) chunk = level.getChunkSource().getChunkNow(holder.getPos().x, holder.getPos().z);
            if (chunk == null || !Band.links(chunk).band) continue;
            BandData data = ((BandChunk) chunk).alpha_omega$data(false);
            if (data == null || data.flipCount() == 0) continue;
            chunks++;
            cells += data.flipCount();
            for (int i = 0; i < data.sections(); i++) {
                if (!data.hasFlips(i)) continue;
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int cell = 0; cell < 4096; cell++) {
                    if (!data.flip(i, cell)) continue;
                    BlockPos at = new BlockPos(chunk.getPos().getMinBlockX() + (cell & 15), baseY + (cell >> 8), chunk.getPos().getMinBlockZ() + (cell >> 4 & 15));
                    if (!Claims.inClaimZone(geometry, chunk, at)) beyond++;
                }
            }
        }
        return String.format(Locale.ROOT, "Claimed cells in loaded band chunks: %d in %d chunks (%d beyond C, held by block entities)", cells, chunks, beyond);
    }

    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Writes: mirrored %d (missed %d), packets for copies %d", mirroredWrites, mirrorsMissed, copyPackets));
        lines.add(String.format(Locale.ROOT, "Forwarded to owners: neighbour %d, shape %d, comparator %d (dropped %d); POI %d; capabilities %d; block entity lookups %d; client replicas %d",
            neighbourForwarded, shapeForwarded, comparatorForwarded, forwardsDropped, poiRedirected, capabilityRedirects, blockEntityRedirects, replicasSent));
        lines.add(String.format(Locale.ROOT, "Skipped at non-owners: random ticks %d, precipitation %d, tickers %d, POI scans %d; scheduled ticks deduped %d",
            randomTicksSkipped, precipitationSkipped, tickersSkipped, poiScanSkipped, scheduledTicksDeduped));
        lines.add(String.format(Locale.ROOT, "Ownership: claims %d, releases %d, stale block entities removed %d, unresolved %d",
            claims, releases, staleBlockEntitiesRemoved, ownershipUnresolved));
        lines.add(String.format(Locale.ROOT, "Claims: placements %d (claimed for a band copy %d, taken back for the tile %d), beyond C %d; block entities created at the owner %d, claims by block entity %d",
            placements, placementClaims, placementReturns, placementsBeyondClaim, blockEntitiesAtOwner, blockEntityClaims));
        lines.add(String.format(Locale.ROOT, "Promotion: gate waits %d (held %d), fills %d (%.1f ms total), fallbacks %d, late fills %d, live refreshes %d; cells filled %d, light checks %d; stamp mismatches %d; paired tickets added %d",
            gateWaits, gateHeld, gateFills, fillNanos / 1e6, gateFallbacks, lateFills, liveRefreshes, cellsFilled, lightChecks, stampMismatches, pairedTicketsAdded));
        lines.add("Gate violations: " + gateViolationTotal() + " " + gateViolations);
        if (Band.DETECTORS) {
            lines.add("Placement shape computations at non-owners (not reactions): " + placementShapes);
            lines.add("Reactions that ran at a non-owner: " + ranAtNonOwnerTotal() + " " + ranAtNonOwner
                + (ranAtNonOwnerWhere.isEmpty() ? "" : " e.g. " + ranAtNonOwnerWhere.subList(0, Math.min(5, ranAtNonOwnerWhere.size()))));
        } else {
            lines.add("Reaction detectors off (-Dalpha_omega.band.detectors=true to count reactions at non-owners)");
        }
        return lines;
    }
}
