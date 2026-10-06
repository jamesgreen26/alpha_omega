package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Band copies ({@code rotated-seams.md} §3, orbifold plan phase 4): every band or skirt cell is a copy of a tile cell
 * (its source). State is shared between the copies (mirrored writes, {@link BandWrites}); reactions run at one copy,
 * the owner ({@link Ownership}); block entities live at the owner. Server only: a client level has no band rules.
 *
 * <p>Where to look: {@link CopyLinks} (which chunks hold copies of a chunk's cells), {@link BandGate} and
 * {@link BandFill} (a band chunk only becomes a loaded chunk once it is filled from its source), {@link BandTickets}
 * (pairs load together), {@link BandReactions} (updates forwarded to the owner), {@link BandCheck} (the invariant).
 */
public final class Band {

    /** Dev: count reactions that reach a non-owner copy ({@code -Dalpha_omega.band.detectors=true}; on in gametests). */
    public static final boolean DETECTORS = Boolean.getBoolean("alpha_omega.band.detectors");
    /** Dev: run the copy check after every gametest ({@code -PcheckCopies}). */
    public static final boolean CHECK_COPIES = Boolean.getBoolean("alpha_omega.band.checkCopies");
    /**
     * Dev regression check only: every copy reacts to neighbour and shape updates, as if nothing were owned. Set only by
     * the detector test ({@code BandGameTests.noOwnershipIsDetected}), to show the detectors see reactions at non-owners.
     */
    public static boolean ignoreOwnership;

    private Band() {
    }

    /** The level's geometry if it has band rules (a server orbifold overworld), else null. Cached on the level. */
    @Nullable
    public static OrbifoldGeometry geometry(Level level) {
        return level.isClientSide ? null : ((BandLevel) level).alpha_omega$geometry();
    }

    @Nullable
    public static OrbifoldGeometry geometry(LevelAccessor level) {
        return level instanceof ServerLevel server ? geometry(server) : null;
    }

    /**
     * Whether a cell may have other copies: within the footprint, and outside the tile or within reach of its edge.
     * Pure arithmetic, so hot paths call it before looking at any chunk.
     */
    public static boolean isLinked(OrbifoldGeometry geometry, int x, int z) {
        int reach = geometry.reach;
        if (!geometry.inFootprint(x, z)) return false;
        return x < geometry.minX + reach || x >= geometry.maxX - reach || z < geometry.northRow + reach || z >= geometry.southRow - reach;
    }

    /** The loaded full chunk at a position, or null; never loads. Main thread. */
    @Nullable
    public static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        return server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** The loaded chunk holding {@code pos} if the cell may have copies there, else null. The hot-path entry point. */
    @Nullable
    public static LevelChunk linkedChunk(Level level, BlockPos pos) {
        if (level.isClientSide) return null;
        OrbifoldGeometry geometry = ((BandLevel) level).alpha_omega$geometry();
        if (geometry == null || !isLinked(geometry, pos.getX(), pos.getZ())) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        return chunk != null && ((BandChunk) chunk).alpha_omega$linked() ? chunk : null;
    }

    public static CopyLinks links(LevelChunk chunk) {
        return ((BandChunk) chunk).alpha_omega$links();
    }

    public static boolean filled(LevelChunk chunk) {
        return ((BandChunk) chunk).alpha_omega$filled();
    }

    // ---- Turning ----

    private static final java.util.concurrent.ConcurrentHashMap<BlockState, BlockState> HALF_TURNS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A state turned by 180° if {@code turned}; cached, since mirrored writes and fills turn the same states over and over. */
    public static BlockState turn(BlockState state, boolean turned) {
        if (!turned) return state;
        BlockState rotated = HALF_TURNS.get(state);
        if (rotated == null) {
            rotated = state.rotate(Rotation.CLOCKWISE_180);
            HALF_TURNS.put(state, rotated);
        }
        return rotated;
    }

    public static Direction turn(Direction direction, boolean turned) {
        return turned ? Rotation.CLOCKWISE_180.rotate(direction) : direction;
    }
}
