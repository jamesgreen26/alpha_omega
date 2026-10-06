package g_mungus.alpha_omega.transfer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Transfers by kind since the server started, for {@code /orbifold scan} and gametests (main thread). */
public final class TransferCounters {

    public enum Kind {
        /** A client's claim accepted. */
        PLAYER_CLAIM,
        /** A claim the server refused (and teleported the player back). */
        REJECTED_CLAIM,
        /** A claim made in a frame the server had already moved the player out of, dropped. */
        STALE_CLAIM,
        /** The server moved a player at a seam (a vehicle it moves, or a client that did not claim). */
        PLAYER_SERVER,
        /** Anything else crossing at {@code H}. */
        SEAM,
        /** A player moved to its group's frame. */
        GROUP,
        /** An entity joined a nearby player's frame. */
        FOLLOWER,
        /** A player's group moved to the frame of a block's owner it used. */
        PULL,
        /** A Sable sub-level crossing. */
        SUB_LEVEL,
        /** An entity other code put into another frame, anchored there (not a transfer). */
        ANCHORED
    }

    private static final Map<Kind, Integer> COUNTS = new EnumMap<>(Kind.class);

    private TransferCounters() {
    }

    public static synchronized void count(Kind kind) {
        COUNTS.merge(kind, 1, Integer::sum);
    }

    public static synchronized int get(Kind kind) {
        return COUNTS.getOrDefault(kind, 0);
    }

    public static synchronized List<String> lines() {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder("Transfers:");
        for (Kind kind : Kind.values()) line.append(' ').append(kind.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(get(kind));
        lines.add(line.toString());
        return lines;
    }
}
