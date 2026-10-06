package g_mungus.alpha_omega.band;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Reactions are owned (RS §3.5): a neighbour or shape update aimed at a copy that does not own its cell is forwarded to
 * the owner, with target, source and direction moved by the motion between them. Each returns whether it took the
 * update (the caller then skips vanilla's handling at the non-owner).
 */
public final class BandReactions {

    private BandReactions() {
    }

    /** The link to the owner if {@code pos} is a non-owner copy, else null. Cheap for cells away from the edges. */
    @Nullable
    private static CopyLinks.Link nonOwner(Level level, BlockPos pos) {
        if (Band.ignoreOwnership) return null;
        LevelChunk chunk = Band.linkedChunk(level, pos);
        return chunk == null ? null : Ownership.owner(level, chunk, pos);
    }

    /** {@code BlockStateBase.handleNeighborChanged} at {@code pos}. */
    public static boolean neighbourChanged(Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        CopyLinks.Link owner = nonOwner(level, pos);
        if (owner == null) return false;
        LevelChunk chunk = owner.chunk(level);
        if (chunk == null) {
            BandCounters.forwardsDropped++;
            return true;
        }
        BandCounters.neighbourForwarded++;
        BlockPos at = owner.map(pos);
        chunk.getBlockState(at).handleNeighborChanged(level, at, block, owner.map(from), moving);
        return true;
    }

    /** {@code Level.neighborShapeChanged}: {@code pos} is the cell whose shape may change. */
    public static boolean shapeChanged(Level level, Direction direction, BlockState neighbour, BlockPos pos, BlockPos neighbourPos, int flags,
        int recursionLeft) {
        CopyLinks.Link owner = nonOwner(level, pos);
        if (owner == null) return false;
        if (owner.chunk(level) == null) {
            BandCounters.forwardsDropped++;
            return true;
        }
        BandCounters.shapeForwarded++;
        level.neighborShapeChanged(Band.turn(direction, owner.turned), Band.turn(neighbour, owner.turned), owner.map(pos), owner.map(neighbourPos),
            flags, recursionLeft);
        return true;
    }

    /**
     * {@code Level.updateNeighbourForOutputSignal}'s {@code onNeighborChange} (comparators) at {@code pos}: the owner's
     * position and neighbour position, or null if {@code pos} owns its cell. Returns {@link #DROPPED} if the owner is
     * not loaded.
     */
    @Nullable
    public static BlockPos[] outputSignalTarget(Level level, BlockPos pos, BlockPos neighbour) {
        CopyLinks.Link owner = nonOwner(level, pos);
        if (owner == null) return null;
        if (owner.chunk(level) == null) {
            BandCounters.forwardsDropped++;
            return DROPPED;
        }
        BandCounters.comparatorForwarded++;
        return new BlockPos[] {owner.map(pos), owner.map(neighbour)};
    }

    public static final BlockPos[] DROPPED = new BlockPos[0];

    /** POI changes at a non-owner copy go to the owner. Returns whether it took the change. */
    public static boolean poiChange(ServerLevel level, BlockPos pos, BlockState old, BlockState state) {
        if (!net.minecraft.world.entity.ai.village.poi.PoiTypes.hasPoi(old) && !net.minecraft.world.entity.ai.village.poi.PoiTypes.hasPoi(state)) return false;
        CopyLinks.Link owner = nonOwner(level, pos);
        if (owner == null) return false;
        if (owner.chunk(level) != null) {
            BandCounters.poiRedirected++;
            level.onBlockStateChange(owner.map(pos), Band.turn(old, owner.turned), Band.turn(state, owner.turned));
        }
        return true;
    }
}
