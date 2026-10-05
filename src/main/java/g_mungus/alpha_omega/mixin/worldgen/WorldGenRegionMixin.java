package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The write guard during generation: in an orbifold world, features and structure pieces stop at the seams. A write
 * outside the tile is dropped ({@code rotated-seams.md} §6.4, version 1); the band is filled from its source instead.
 */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$guardTile(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (((WorldGenRegion) (Object) this).getLevel().getChunkSource().getGenerator() instanceof OrbifoldChunkGenerator orbifold
            && !orbifold.mayWrite(pos.getX(), pos.getZ())) {
            cir.setReturnValue(false);
        }
    }
}
