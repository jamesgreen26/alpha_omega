package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owned reactions (RS §3.5) at the block state: a neighbour update for a non-owner copy is forwarded to the owner. Every
 * dispatched neighbour update ({@code NeighborUpdater}) and NeoForge's comparator path end in
 * {@code handleNeighborChanged}.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseBandMixin {

    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$ownedNeighbourUpdate(Level level, BlockPos pos, Block block, BlockPos from, boolean moving, CallbackInfo ci) {
        if (!level.isClientSide && BandReactions.neighbourChanged(level, pos, block, from, moving)) ci.cancel();
    }
}
