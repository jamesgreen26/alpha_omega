package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.cube.Cube;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The write guard (design §3): nothing writes to the barrier or another face's cells, on server or client. */
@Mixin(Level.class)
abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$guardFaces(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (!Cube.canWrite((Level) (Object) this, pos)) cir.setReturnValue(false);
    }
}
