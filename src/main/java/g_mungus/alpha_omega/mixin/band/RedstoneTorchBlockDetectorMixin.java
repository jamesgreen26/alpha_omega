package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detector: counts {@code RedstoneTorchBlock.neighborChanged} reaching a non-owner copy (a path past the band hooks). */
@Mixin(RedstoneTorchBlock.class)
abstract class RedstoneTorchBlockDetectorMixin {

    @Inject(method = "neighborChanged", at = @At("HEAD"))
    private void alpha_omega$detect(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving, CallbackInfo ci) {
        BandCounters.reaction(level, pos, "RedstoneTorchBlock.neighborChanged");
    }
}
