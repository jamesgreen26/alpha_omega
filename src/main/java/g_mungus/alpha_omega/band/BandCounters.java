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

    public static void reset() {
        mirroredWrites = mirrorsMissed = copyPackets = 0;
        neighbourForwarded = shapeForwarded = comparatorForwarded = forwardsDropped = poiRedirected = capabilityRedirects = blockEntityRedirects = replicasSent = 0;
        randomTicksSkipped = precipitationSkipped = tickersSkipped = scheduledTicksDeduped = poiScanSkipped = 0;
        claims = releases = staleBlockEntitiesRemoved = ownershipUnresolved = 0;
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

    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Writes: mirrored %d (missed %d), packets for copies %d", mirroredWrites, mirrorsMissed, copyPackets));
        lines.add(String.format(Locale.ROOT, "Forwarded to owners: neighbour %d, shape %d, comparator %d (dropped %d); POI %d; capabilities %d; block entity lookups %d; client replicas %d",
            neighbourForwarded, shapeForwarded, comparatorForwarded, forwardsDropped, poiRedirected, capabilityRedirects, blockEntityRedirects, replicasSent));
        lines.add(String.format(Locale.ROOT, "Skipped at non-owners: random ticks %d, precipitation %d, tickers %d, POI scans %d; scheduled ticks deduped %d",
            randomTicksSkipped, precipitationSkipped, tickersSkipped, poiScanSkipped, scheduledTicksDeduped));
        lines.add(String.format(Locale.ROOT, "Ownership: claims %d, releases %d, stale block entities removed %d, unresolved %d",
            claims, releases, staleBlockEntitiesRemoved, ownershipUnresolved));
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
