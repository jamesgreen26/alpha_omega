package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.cube.Cube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The write guard during generation: features and structure pieces stop at the barrier. */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$guardFaces(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!Cube.canWrite(((WorldGenRegion) (Object) this).getLevel(), pos)) cir.setReturnValue(false);
    }
}
