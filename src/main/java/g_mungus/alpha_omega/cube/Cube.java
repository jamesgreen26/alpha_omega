package g_mungus.alpha_omega.cube;

import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
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

    /** Tests for positions outside every face that another system owns, and guards itself (Sable's plots). */
    private static final List<BiPredicate<Level, BlockPos>> OUTSIDERS = new CopyOnWriteArrayList<>();

    /**
     * Registers a test for positions outside every face's storage that belong to another system: the cube's write guard
     * leaves them alone. Sable registers its plots, where sub-levels' blocks are kept.
     */
    public static void registerOutsider(BiPredicate<Level, BlockPos> owns) {
        OUTSIDERS.add(owns);
    }

    /**
     * Whether a block may be written at {@code pos}: anywhere outside a cube world, only owned cells inside one. The
     * barrier and other faces' cells are fixed once generated (design §3). Positions outside every face that another
     * system owns ({@link #registerOutsider}) are that system's business.
     */
    public static boolean canWrite(Level level, BlockPos pos) {
        CubeGeometry geometry = of(level);
        if (geometry == null || canWrite(geometry, pos)) return true;
        if (geometry.faceAt(pos.getX(), pos.getZ()) != null) return false;
        for (BiPredicate<Level, BlockPos> owns : OUTSIDERS) {
            if (owns.test(level, pos)) return true;
        }
        return false;
    }

    public static boolean canWrite(CubeGeometry geometry, BlockPos pos) {
        CubeFace face = geometry.faceAt(pos.getX(), pos.getZ());
        return face != null && geometry.isOwned(face, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void setClient(@Nullable CubeGeometry geometry) {
        client = geometry;
    }
}
