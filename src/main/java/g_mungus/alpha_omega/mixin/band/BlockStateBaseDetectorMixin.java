package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Detector (dev only): shape updates and random ticks that reach a non-owner copy anyway. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseDetectorMixin {

    @Inject(method = "updateShape", at = @At("HEAD"))
    private void alpha_omega$detectShape(Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos,
        CallbackInfoReturnable<BlockState> cir) {
        if (level instanceof ServerLevel server) BandCounters.reaction(server, pos, "updateShape");
    }

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void alpha_omega$detectRandomTick(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BandCounters.reaction(level, pos, "randomTick");
    }
}
