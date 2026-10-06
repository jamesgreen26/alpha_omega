package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Claims (RS §3.3, orbifold plan phase 8): a placed block belongs to the frame that placed it. The rules, applied to
 * every original (not mirrored) write in a linked chunk, at the copy where the write was made:
 *
 * <ul>
 * <li>A <b>placement</b> ({@link #isPlacement}) claims the cell for the writing copy if the cell is in that copy's
 * <b>claim zone</b>: the tile, or the band out to {@code C}. Beyond it, the owner is unchanged. A placement in the
 * tile claims for the tile copy, which is the nominal owner. The claim is made before the write's callbacks and block
 * entity creation, so the block entity is created at the claimant.</li>
 * <li>A <b>removal</b> (the cell becomes air) returns the cell to its nominal owner, the tile copy. It runs after the
 * write, so the old block's {@code onRemove} still sees its owner's block entity.</li>
 * <li>Anything else (a change of state of the same block, or one block turned into another) keeps the owner.</li>
 * <li><b>Block entities live at the owner</b>: a block entity a write would create at a non-owner copy is created at
 * the owner instead (fresh, so it holds nothing frame-dependent yet). One set explicitly at a non-owner copy
 * ({@code setBlockEntity}, as pistons do) claims the cell, wherever it is; past the claim zone that claim lasts only as
 * long as the block entity, so a moving piston beyond {@code C} leaves its landed block with the nominal owner.</li>
 * </ul>
 *
 * <p>Moved blocks count as placements in the mover's frame: a write of a moving piston (whatever it replaces) and the
 * write that lands its block (replacing the moving piston) are both placements, at the positions the piston's logic
 * computed in its own frame.
 */
public final class Claims {

    private Claims() {
    }

    /**
     * Whether a write is a placement: the old state is air, a replaceable block, a bare fluid or a moving piston, and the
     * new state is a different block (not air). Writing a moving piston is always one: it carries a moved block.
     */
    public static boolean isPlacement(BlockState old, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.MOVING_PISTON)) return !old.is(Blocks.MOVING_PISTON);
        if (old.is(state.getBlock())) return false;
        return old.isAir() || old.canBeReplaced() || old.getBlock() instanceof LiquidBlock || old.is(Blocks.MOVING_PISTON);
    }

    /** Whether {@code chunk}'s copy of {@code pos} may claim it: any tile cell, or a band cell out to {@code C}. */
    public static boolean inClaimZone(OrbifoldGeometry geometry, LevelChunk chunk, BlockPos pos) {
        return !Band.links(chunk).band || geometry.cellDepth(pos.getX(), pos.getZ()) <= geometry.claim;
    }

    public static boolean inClaimZone(Level level, LevelChunk chunk, BlockPos pos) {
        OrbifoldGeometry geometry = Band.geometry(level);
        return geometry == null || inClaimZone(geometry, chunk, pos);
    }

    /**
     * From {@code LevelChunk.setBlockState}, right after the section changed and before the write is mirrored or runs
     * any callback: a placement claims the cell for this copy if it may.
     */
    public static void beforeEffects(Level level, LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        if (!isPlacement(old, state)) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        if (!inClaimZone(geometry, chunk, pos)) {
            BandCounters.placementsBeyondClaim++;
            return;
        }
        BandCounters.placements++;
        if (Ownership.isOwner(chunk, pos)) return;
        if (Band.links(chunk).band) BandCounters.placementClaims++;
        else BandCounters.placementReturns++;
        Ownership.claim(level, chunk, pos);
    }

    /** From the end of {@code LevelChunk.setBlockState}: a removal returns the cell to its nominal owner. */
    public static void afterWrite(Level level, LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        if (state.isAir() && !old.isAir()) Ownership.toNominal(level, chunk, pos);
    }

    /**
     * A block entity at this copy is being removed. Inside the claim zone ownership follows the block, not the block
     * entity; beyond it the only claims are block entity claims, which end with the block entity.
     */
    public static void blockEntityRemoved(Level level, LevelChunk chunk, BlockPos pos) {
        if (!inClaimZone(level, chunk, pos)) Ownership.release(level, chunk, pos);
    }
}
