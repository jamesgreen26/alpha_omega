package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 4 spike (band copies, {@code rotated-seams.md} §3): which copy of a band cell owns its reactions, and where
 * its other copies are. Server only; a client level has no band rules.
 *
 * <p>Every band or skirt cell is a copy of a tile cell (its source). In the spike the tile copy is the owner, with no
 * claims ({@link #blockEntityClaims} is an experiment that relaxes this for cells holding a block entity).
 */
public final class Band {

    /** How a neighbour or shape update that reaches a non-owner copy is handled. */
    public enum Mode {
        /**
         * RS §3.5 as written: the update is skipped at the non-owner, and mirrored writes send their own neighbour and
         * shape updates around each copy, so the owner hears of the change from its own storage.
         */
        SKIP,
        /**
         * The update is forwarded to the owner copy (target, source position and direction moved by the motion between
         * them), and mirrored writes send no updates of their own, only packets. Every update is delivered exactly once,
         * at the owner, in vanilla's order.
         */
        FORWARD,
        /**
         * No ownership of neighbour or shape updates: every copy reacts. Only to show that the detectors see a reaction
         * at a non-owner when the gates are off.
         */
        NONE
    }

    public static volatile Mode mode = Mode.FORWARD;
    /**
     * Experiment, off by default: a block entity lives at the copy where it was created, and that copy owns the cell
     * while it does (a minimal claim, RS §3.3). Needed for pistons pushing across a seam.
     */
    public static volatile boolean blockEntityClaims = false;

    private Band() {
    }

    /** A copy of a cell: its position, and the transform from the asking cell's frame to it. */
    public record Link(BlockPos pos, Transform transform) {
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

    /** Whether a cell may be linked to other cells: within the footprint, and outside the tile or near its edge. */
    public static boolean isLinked(OrbifoldGeometry geometry, int x, int z) {
        int reach = geometry.reach;
        if (!geometry.inFootprint(x, z)) return false;
        return x < geometry.minX + reach || x >= geometry.maxX - reach || z < geometry.northRow + reach || z >= geometry.southRow - reach;
    }

    /** Every other copy of {@code pos}'s cell, loaded or not, with the transform from {@code pos}'s frame to each. */
    public static List<Link> copies(OrbifoldGeometry geometry, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        if (!isLinked(geometry, x, z)) return List.of();
        OrbifoldGeometry.Cell canon = geometry.canon(x, z);
        Motion toCanon = canon.frame();
        List<OrbifoldGeometry.Cell> others = geometry.copies(canon.x(), canon.z());
        if (toCanon.isIdentity() && others.isEmpty()) return List.of();
        List<Link> links = new ArrayList<>(others.size() + 1);
        if (!toCanon.isIdentity()) links.add(new Link(new BlockPos(canon.x(), pos.getY(), canon.z()), Transform.of(toCanon)));
        for (OrbifoldGeometry.Cell copy : others) {
            if (copy.x() == x && copy.z() == z) continue;
            links.add(new Link(new BlockPos(copy.x(), pos.getY(), copy.z()), Transform.of(toCanon.then(copy.frame().inverse()))));
        }
        return links;
    }

    /**
     * The owner copy of {@code pos}'s cell if it is elsewhere, or null if {@code pos} is the owner (or has no copies).
     */
    @Nullable
    public static Link owner(Level level, BlockPos pos) {
        OrbifoldGeometry geometry = geometry(level);
        return geometry == null ? null : owner(level, geometry, pos);
    }

    @Nullable
    public static Link owner(Level level, OrbifoldGeometry geometry, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        if (blockEntityClaims) return claimedOwner(level, geometry, pos);
        if (geometry.isTile(x, z) || !geometry.inFootprint(x, z)) return null;
        OrbifoldGeometry.Cell canon = geometry.canon(x, z);
        return new Link(new BlockPos(canon.x(), pos.getY(), canon.z()), Transform.of(canon.frame()));
    }

    /** With block entity claims: the copy holding a block entity owns the cell; otherwise the tile copy does. */
    @Nullable
    private static Link claimedOwner(Level level, OrbifoldGeometry geometry, BlockPos pos) {
        int x = pos.getX(), z = pos.getZ();
        if (!isLinked(geometry, x, z)) return null;
        if (holdsBlockEntity(level, pos)) return null;
        List<Link> copies = copies(geometry, pos);
        for (Link link : copies) {
            if (holdsBlockEntity(level, link.pos())) return link;
        }
        if (geometry.isTile(x, z)) return null;
        for (Link link : copies) {
            if (geometry.isTile(link.pos().getX(), link.pos().getZ())) return link;
        }
        return null;
    }

    private static boolean holdsBlockEntity(Level level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        return chunk != null && chunk.getBlockEntities().containsKey(pos);
    }

    public static boolean isOwner(Level level, BlockPos pos) {
        return owner(level, pos) == null;
    }

    /** The loaded full chunk at a position, or null; never loads. */
    @Nullable
    public static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        return server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
