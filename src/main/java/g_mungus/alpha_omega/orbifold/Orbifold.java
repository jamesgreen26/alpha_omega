package g_mungus.alpha_omega.orbifold;

import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a level's orbifold geometry. Only the overworld of an orbifold world has one: on the server it comes from the
 * overworld's generator, on the client from the server during configuration ({@code OrbifoldPayload}).
 */
public final class Orbifold {

    @Nullable
    private static volatile OrbifoldGeometry client;

    private Orbifold() {
    }

    @Nullable
    public static OrbifoldGeometry of(Level level) {
        if (level.dimension() != Level.OVERWORLD) return null;
        if (level instanceof ServerLevel server) {
            return server.getChunkSource().getGenerator() instanceof OrbifoldChunkGenerator generator ? generator.geometry() : null;
        }
        return client;
    }

    public static void setClient(@Nullable OrbifoldGeometry geometry) {
        client = geometry;
    }
}
