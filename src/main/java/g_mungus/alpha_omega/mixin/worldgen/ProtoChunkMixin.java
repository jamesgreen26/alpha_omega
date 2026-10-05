package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carvers write straight into the chunk; in a cube world they leave the shared band by the barrier alone. */
@Mixin(ProtoChunk.class)
abstract class ProtoChunkMixin {

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$keepCarversOut(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (!CubeChunkGenerator.carverMayWrite(pos)) cir.setReturnValue(null);
    }
}
