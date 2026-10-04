package g_mungus.alpha_omega.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.util.StaticCache2D;

/** Helpers for the compat mixins on C2ME's chunk system. */
public final class C2meChunkSystem {

    private C2meChunkSystem() {
    }

    /** Neighborhood caches C2ME builds resolve images with their level's period, like vanilla's. */
    public static StaticCache2D<?> stamp(StaticCache2D<?> cache, ChunkLoadingContext context) {
        WrapHolder.set(cache, Wrap.of(((ChunkMapAccessor) context.tacs()).alpha_omega$getLevel()));
        return cache;
    }
}
