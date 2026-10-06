package g_mungus.alpha_omega.orbifold;

import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a level's orbifold geometry. The overworld and the Nether of an orbifold world have one each (the Nether's is
 * the overworld's at 1:8); the End and every other dimension never do. On the server it comes from the level's
 * generator, on the client from the server during configuration ({@code OrbifoldPayload}), per dimension.
 */
public final class Orbifold {

    private static volatile Map<ResourceKey<Level>, OrbifoldGeometry> client = Map.of();

    private Orbifold() {
    }

    /** Whether a dimension can be an orbifold at all: the overworld and the Nether. */
    public static boolean mayBeOrbifold(ResourceKey<Level> dimension) {
        return dimension == Level.OVERWORLD || dimension == Level.NETHER;
    }

    @Nullable
    public static OrbifoldGeometry of(Level level) {
        if (!mayBeOrbifold(level.dimension())) return null;
        if (level instanceof ServerLevel server) {
            return server.getChunkSource().getGenerator() instanceof OrbifoldChunkGenerator generator ? generator.geometry() : null;
        }
        return client.get(level.dimension());
    }

    /** The client's geometries, by dimension (empty for a world that is not an orbifold, or on logging out). */
    public static void setClient(Map<ResourceKey<Level>, OrbifoldGeometry> geometries) {
        client = Map.copyOf(geometries);
    }
}
