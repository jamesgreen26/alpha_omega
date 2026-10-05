package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * State is shared (RS §3.2): a write to one copy of a cell is applied to every loaded copy, turned. The mirrored write
 * updates sections, heightmaps and light, but runs no {@code onPlace}/{@code onRemove} and (except at the owner)
 * creates no block entity. Server thread only.
 */
public final class BandWrites {

    /** True while a mirrored write runs: its chunk write must not mirror again or run block callbacks. */
    private static boolean mirroring;
    /** Copies whose {@code markAndNotifyBlock} we are running (SKIP mode): they must not notify copies in turn. */
    private static final Deque<BlockPos> notifying = new ArrayDeque<>();

    private BandWrites() {
    }

    public static boolean mirroring() {
        return mirroring;
    }

    /**
     * Called from {@code LevelChunk.setBlockState} right after the section changed at {@code pos}, before the block's
     * own callbacks run, so writes those callbacks make land in the copies after this one.
     */
    public static void mirror(Level level, LevelChunk chunk, BlockPos pos, BlockState state, boolean moving) {
        if (mirroring || !((BandChunk) chunk).alpha_omega$filled()) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        for (Band.Link link : Band.copies(geometry, pos)) {
            LevelChunk copy = Band.loadedChunk(level, link.pos());
            if (copy == null) {
                BandCounters.mirrorsMissed++;
                continue;
            }
            mirroring = true;
            try {
                copy.setBlockState(link.pos(), link.transform().state(state), moving);
            } finally {
                mirroring = false;
            }
            BandCounters.mirroredWrites++;
        }
    }

    /**
     * After a (non-mirrored) chunk write: a block entity left at a copy whose block no longer allows it is removed. The
     * write at {@code pos} ran the block's {@code onRemove}, which removes the block entity at the owner through the
     * redirect; this catches blocks whose {@code onRemove} does not.
     */
    public static void afterWrite(Level level, BlockPos pos) {
        if (mirroring) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        for (Band.Link link : Band.copies(geometry, pos)) {
            LevelChunk copy = Band.loadedChunk(level, link.pos());
            if (copy == null) continue;
            BlockEntity entity = copy.getBlockEntities().get(link.pos());
            if (entity != null && !entity.getType().isValid(copy.getBlockState(link.pos()))) copy.removeBlockEntity(link.pos());
        }
    }

    /**
     * After {@code Level.markAndNotifyBlock} at {@code pos}: tell each copy's storage. In SKIP mode (RS as written) this
     * is the copy's own {@code markAndNotifyBlock}, with its neighbour and shape updates; in FORWARD mode it is only the
     * block update packet, because every update already reached its owner.
     */
    public static void notifyCopies(Level level, BlockPos pos, BlockState old, BlockState state, int flags, int recursionLeft) {
        if (mirroring || (!notifying.isEmpty() && notifying.peek() == pos)) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        for (Band.Link link : Band.copies(geometry, pos)) {
            LevelChunk copy = Band.loadedChunk(level, link.pos());
            if (copy == null) continue;
            BlockState copyState = link.transform().state(state);
            if (copy.getBlockState(link.pos()) != copyState) continue;
            BlockState copyOld = link.transform().state(old);
            BandCounters.copyNotifications++;
            if (Band.mode == Band.Mode.SKIP) {
                BlockPos at = link.pos();
                notifying.push(at);
                try {
                    level.markAndNotifyBlock(at, copy, copyOld, copyState, flags, recursionLeft);
                } finally {
                    notifying.pop();
                }
            } else if ((flags & 2) != 0) {
                level.sendBlockUpdated(link.pos(), copyOld, copyState, flags);
            }
        }
    }
}
