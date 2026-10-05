package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks {@code Block.updateFromNeighbourShapes}: a writer computing the state it is about to place (pistons landing a
 * block, generation post-processing, commands). Its {@code updateShape} calls are not reactions, so the detector
 * counts them apart.
 */
@Mixin(Block.class)
abstract class BlockPlacementShapeMixin {

    @Inject(method = "updateFromNeighbourShapes", at = @At("HEAD"))
    private static void alpha_omega$enter(BlockState state, LevelAccessor level, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BandCounters.placementDepth++;
    }

    @Inject(method = "updateFromNeighbourShapes", at = @At("RETURN"))
    private static void alpha_omega$leave(BlockState state, LevelAccessor level, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BandCounters.placementDepth--;
    }
}
