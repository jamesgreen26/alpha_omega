package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Owned reactions (RS §3.5) at the block state: a neighbour update for a non-owner copy is skipped or forwarded to the
 * owner. Every dispatched neighbour update ({@code NeighborUpdater}) and NeoForge's comparator path end in
 * {@code handleNeighborChanged}. Shape updates and random ticks are gated upstream; here they are only counted if they
 * reach a non-owner copy anyway.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseBandMixin {

    @Inject(method = "handleNeighborChanged", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$ownedNeighbourUpdate(Level level, BlockPos pos, Block block, BlockPos from, boolean moving, CallbackInfo ci) {
        if (level.isClientSide) return;
        OrbifoldGeometry geometry = Band.geometry(level);
        if (geometry == null) return;
        Band.Link owner = Band.owner(level, geometry, pos);
        if (owner == null) return;
        ci.cancel();
        if (Band.mode == Band.Mode.SKIP) {
            BandCounters.neighbourSkipped++;
            return;
        }
        LevelChunk chunk = Band.loadedChunk(level, owner.pos());
        if (chunk == null) {
            BandCounters.forwardsDropped++;
            return;
        }
        BandCounters.neighbourForwarded++;
        chunk.getBlockState(owner.pos()).handleNeighborChanged(level, owner.pos(), block, owner.transform().block(from), moving);
    }

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
