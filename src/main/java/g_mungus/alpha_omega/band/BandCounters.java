package g_mungus.alpha_omega.band;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Spike counters for {@code /orbifold scan} and gametests. Server thread only. {@link #ranAtNonOwner} is the one that
 * must stay 0: it counts reactions that reached a non-owner copy past every hook (a vanilla path the hooks miss).
 */
public final class BandCounters {

    public static long mirroredWrites;
    /** A mirrored write whose copy's chunk was not loaded: the copies now differ until the band is refilled. */
    public static long mirrorsMissed;
    public static long neighbourSkipped;
    public static long neighbourForwarded;
    public static long shapeSkipped;
    public static long shapeForwarded;
    /** An update for a non-owner copy whose owner's chunk was not loaded. */
    public static long forwardsDropped;
    public static long comparatorForwarded;
    public static long randomTicksSkipped;
    public static long precipitationSkipped;
    public static long tickersSkipped;
    public static long scheduledTicksDeduped;
    public static long blockEntityRedirects;
    public static long copyNotifications;
    public static long bandFills;
    /** Block entity ticks at a non-owner copy allowed by {@link Band#blockEntityClaims} (the cell is claimed). */
    public static long claimedTicks;
    /** Shape updates computed by {@code Block.updateFromNeighbourShapes} at a non-owner: the writer working out the state it places, not a reaction. */
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
        mirroredWrites = mirrorsMissed = neighbourSkipped = neighbourForwarded = shapeSkipped = shapeForwarded = 0;
        forwardsDropped = comparatorForwarded = randomTicksSkipped = precipitationSkipped = tickersSkipped = 0;
        scheduledTicksDeduped = blockEntityRedirects = copyNotifications = bandFills = claimedTicks = placementShapes = 0;
        ranAtNonOwner.clear();
        ranAtNonOwnerWhere.clear();
    }

    public static long ranAtNonOwnerTotal() {
        return ranAtNonOwner.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Detector: a reaction of {@code kind} is about to run at {@code pos}; counts it if that is a non-owner copy. */
    public static void reaction(Level level, BlockPos pos, String kind) {
        if (level.isClientSide || Band.geometry(level) == null) return;
        if (Band.owner(level, pos) == null) return;
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
            if (name.startsWith("java.") || name.startsWith("g_mungus.alpha_omega.band") || name.contains("BandCounters")) continue;
            frames.add(name.substring(name.lastIndexOf('.') + 1) + "." + frame.getMethodName());
            if (frames.size() >= 8) break;
        }
        return String.join(" < ", frames);
    }

    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "Band mode %s, block entity claims %s", Band.mode, Band.blockEntityClaims));
        lines.add(String.format(Locale.ROOT, "Mirrored writes %d (missed %d), copy notifications %d, band fills %d",
            mirroredWrites, mirrorsMissed, copyNotifications, bandFills));
        lines.add(String.format(Locale.ROOT, "Neighbour updates: skipped %d, forwarded %d; shape updates: skipped %d, forwarded %d; comparator %d; dropped %d",
            neighbourSkipped, neighbourForwarded, shapeSkipped, shapeForwarded, comparatorForwarded, forwardsDropped));
        lines.add(String.format(Locale.ROOT, "Skipped at non-owners: random ticks %d, precipitation %d, tickers %d; scheduled ticks deduped %d; block entity redirects %d; claimed ticks %d",
            randomTicksSkipped, precipitationSkipped, tickersSkipped, scheduledTicksDeduped, blockEntityRedirects, claimedTicks));
        lines.add("Placement shape computations at non-owners (not reactions): " + placementShapes);
        lines.add("Reactions that ran at a non-owner: " + ranAtNonOwnerTotal() + " " + ranAtNonOwner + (ranAtNonOwnerWhere.isEmpty() ? "" : " e.g. " + ranAtNonOwnerWhere.subList(0, Math.min(5, ranAtNonOwnerWhere.size()))));
        return lines;
    }
}
