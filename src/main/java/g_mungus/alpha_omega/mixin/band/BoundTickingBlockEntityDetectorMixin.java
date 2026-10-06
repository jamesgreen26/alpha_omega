package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandCounters;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detector (dev only): a block entity ticking at a copy that does not own its cell. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BoundTickingBlockEntityDetectorMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void alpha_omega$detect(CallbackInfo ci) {
        if (this.blockEntity.getLevel() == null || this.blockEntity.getLevel().isClientSide) return;
        BandCounters.reaction(this.blockEntity.getLevel(), this.blockEntity.getBlockPos(), "blockEntityTick");
    }
}
