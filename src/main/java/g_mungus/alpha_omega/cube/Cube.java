package g_mungus.alpha_omega.cube;

import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Finds a level's cube geometry. Only the overworld of a cube world has one: on the server it comes from the
 * overworld's generator, on the client from the server during configuration.
 */
public final class Cube {

    @Nullable
    private static volatile CubeGeometry client;

    private Cube() {
    }

    @Nullable
    public static CubeGeometry of(Level level) {
        if (level.dimension() != Level.OVERWORLD) return null;
        if (level instanceof ServerLevel server) {
            return server.getChunkSource().getGenerator() instanceof CubeChunkGenerator generator ? generator.geometry(server) : null;
        }
        return client;
    }

    public static void setClient(@Nullable CubeGeometry geometry) {
        client = geometry;
    }
}
