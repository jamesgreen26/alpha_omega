package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.frame.Frames;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * R4: block entity tickers run at the lifted position. Most tickers are static and take {@code pos}; ones that
 * read {@code worldPosition} directly still see the canonical position and rely on the bridges.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class LevelChunkBoundTickingBlockEntityMixin {

    @Shadow
    @Final
    LevelChunk this$0;

    @ModifyArg(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"),
        index = 1)
    private BlockPos alpha_omega$liftTicker(BlockPos pos) {
        return this.this$0.getLevel() instanceof ServerLevel level ? Frames.lift(level, pos) : pos;
    }
}
