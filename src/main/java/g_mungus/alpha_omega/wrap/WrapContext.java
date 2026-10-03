package g_mungus.alpha_omega.wrap;

import java.util.function.Supplier;

/**
 * The dimension being constructed. Everything a {@code ServerLevel} builds in its constructor (chunk map, ticket
 * trackers, tick containers, entity storage, light engines, POI storage, noise) captures the level's {@link Wrap}
 * from here, since those objects have no route back to their level afterwards.
 */
public final class WrapContext {

    private static final ThreadLocal<Wrap> CURRENT = new ThreadLocal<>();

    private WrapContext() {
    }

    /** The wrap of the level under construction on this thread, or {@link Wrap#NONE}. */
    public static Wrap current() {
        Wrap wrap = CURRENT.get();
        return wrap == null ? Wrap.NONE : wrap;
    }

    /** Like {@link #current()}, but the Overworld's outside level construction (e.g. noise built by tests). */
    public static Wrap currentOrOverworld() {
        Wrap wrap = CURRENT.get();
        return wrap == null ? Wraps.overworld() : wrap;
    }

    public static <T> T with(Wrap wrap, Supplier<T> action) {
        Wrap previous = CURRENT.get();
        CURRENT.set(wrap);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
