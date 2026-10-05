package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detector: a block entity ticking at a non-owner copy (or, with claims, at a band copy it claims). */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract class BoundTickingBlockEntityDetectorMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void alpha_omega$detect(CallbackInfo ci) {
        if (this.blockEntity.getLevel() == null || this.blockEntity.getLevel().isClientSide) return;
        OrbifoldGeometry geometry = Band.geometry(this.blockEntity.getLevel());
        if (geometry == null) return;
        int x = this.blockEntity.getBlockPos().getX(), z = this.blockEntity.getBlockPos().getZ();
        if (Band.blockEntityClaims && !geometry.isTile(x, z) && geometry.inFootprint(x, z)
            && Band.owner(this.blockEntity.getLevel(), this.blockEntity.getBlockPos()) == null) {
            BandCounters.claimedTicks++;
            return;
        }
        BandCounters.reaction(this.blockEntity.getLevel(), this.blockEntity.getBlockPos(), "blockEntityTick");
    }
}
