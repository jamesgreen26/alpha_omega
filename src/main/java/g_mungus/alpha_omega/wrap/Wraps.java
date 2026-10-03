package g_mungus.alpha_omega.wrap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The wrapping of every dimension of the world being played. Set when a server starts (from the world's
 * settings) and on clients when they connect (from the server); unwrapped otherwise.
 */
public final class Wraps {

    private static volatile WorldWrapSettings settings = WorldWrapSettings.DISABLED;
    private static final Map<ResourceKey<Level>, Wrap> CACHE = new ConcurrentHashMap<>();

    private Wraps() {
    }

    public static WorldWrapSettings settings() {
        return settings;
    }

    public static void configure(WorldWrapSettings newSettings) {
        settings = newSettings;
        CACHE.clear();
    }

    public static void reset() {
        configure(WorldWrapSettings.DISABLED);
    }

    public static Wrap of(ResourceKey<Level> dimension) {
        return CACHE.computeIfAbsent(dimension, dim -> {
            int period = settings.periodFor(dim);
            return period == 0 ? Wrap.NONE : new Wrap(period);
        });
    }

    public static Wrap overworld() {
        return of(Level.OVERWORLD);
    }

    /**
     * Chunk period that structure grids must divide in every wrapped dimension: the Nether's when it wraps (it
     * divides the Overworld's), else the Overworld's. 0 if nothing wraps.
     */
    public static int structureGridPeriod() {
        Wrap nether = of(Level.NETHER);
        return nether.enabled() ? nether.chunkPeriod : overworld().chunkPeriod;
    }
}
