package g_mungus.alpha_omega.wrap;

/**
 * Duck interface for vanilla objects that have no route back to their level (ticket trackers, tick containers,
 * entity sections, light storage, worldgen caches, ...): the dimension's {@link Wrap} is stamped on when they are
 * created. Client-side instances keep {@link Wrap#NONE}: the client works in its own unrolled frame.
 */
public interface WrapHolder {

    Wrap alpha_omega$wrap();

    void alpha_omega$setWrap(Wrap wrap);

    static Wrap of(Object holder) {
        return ((WrapHolder) holder).alpha_omega$wrap();
    }

    static void set(Object holder, Wrap wrap) {
        ((WrapHolder) holder).alpha_omega$setWrap(wrap);
    }
}
