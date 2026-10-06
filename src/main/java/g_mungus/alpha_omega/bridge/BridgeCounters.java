package g_mungus.alpha_omega.bridge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Counters for {@code /orbifold scan} and gametests: how often each bridge did extra work (its query reached near an
 * edge and had images), and what it found there. Server thread only, like the band's.
 *
 * <p>A <b>watch</b> region counts, separately, the bridge work whose query touched it: the gametest for "away from the
 * edges nothing changes" watches its interior site and expects nothing.
 */
public final class BridgeCounters {

    /** The bridges (RS §5). */
    public enum Kind {
        ENTITIES("entity box queries"),
        PLAYERS("player proximity"),
        BROADCAST("broadcasts"),
        POI("POI queries"),
        POI_OWNER("POI lookups at a copy"),
        GAME_EVENTS("game events"),
        INTERACTION("interaction range"),
        STRUCTURES("structure lookups"),
        PUSHES("pushes across frames dropped");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static final Map<Kind, Long> RUNS = new EnumMap<>(Kind.class);
    /** What the runs found only through an image (entities, players, POIs, listeners). */
    private static final Map<Kind, Long> FOUND = new EnumMap<>(Kind.class);
    private static final Map<Kind, Long> WATCHED = new EnumMap<>(Kind.class);
    private static double[] watch;

    private BridgeCounters() {
    }

    /** A bridge did extra work for a query covering this region. */
    public static void run(Kind kind, double minX, double minZ, double maxX, double maxZ) {
        RUNS.merge(kind, 1L, Long::sum);
        double[] w = watch;
        if (w != null && maxX >= w[0] && minX <= w[2] && maxZ >= w[1] && minZ <= w[3]) WATCHED.merge(kind, 1L, Long::sum);
    }

    public static void run(Kind kind, double x, double z, double radius) {
        run(kind, x - radius, z - radius, x + radius, z + radius);
    }

    /** It found {@code count} things only through an image. */
    public static void found(Kind kind, long count) {
        if (count != 0) FOUND.merge(kind, count, Long::sum);
    }

    public static long runs(Kind kind) {
        return RUNS.getOrDefault(kind, 0L);
    }

    public static long found(Kind kind) {
        return FOUND.getOrDefault(kind, 0L);
    }

    /** Watches a region (block x, z bounds) from now on, with its counts cleared; null stops watching. */
    public static void watch(double minX, double minZ, double maxX, double maxZ) {
        watch = new double[] {minX, minZ, maxX, maxZ};
        WATCHED.clear();
    }

    public static void stopWatching() {
        watch = null;
    }

    /** The bridge work that touched the watched region since it was set, by kind. */
    public static Map<Kind, Long> watched() {
        Map<Kind, Long> copy = new EnumMap<>(Kind.class);
        copy.putAll(WATCHED);
        return copy;
    }

    public static void reset() {
        RUNS.clear();
        FOUND.clear();
        WATCHED.clear();
    }

    public static List<String> lines() {
        List<String> parts = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            parts.add(String.format(Locale.ROOT, "%s %d (found %d)", kind.label, runs(kind), found(kind)));
        }
        return List.of("Bridges near edges: " + String.join(", ", parts));
    }
}
