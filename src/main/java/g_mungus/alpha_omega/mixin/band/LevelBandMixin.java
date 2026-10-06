package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.band.BandLevel;
import g_mungus.alpha_omega.band.BandReactions;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Band rules on {@code Level}: the cached geometry; shape updates for a non-owner copy forwarded to the owner (every
 * shape update goes through {@code neighborShapeChanged}); comparator-style {@code onNeighborChange} likewise.
 */
@Mixin(Level.class)
abstract class LevelBandMixin implements BandLevel {

    @Unique
    private boolean alpha_omega$geometryKnown;
    @Unique
    @Nullable
    private OrbifoldGeometry alpha_omega$geometry;

    @Override
    @Nullable
    public OrbifoldGeometry alpha_omega$geometry() {
        if (!this.alpha_omega$geometryKnown) {
            Level self = (Level) (Object) this;
            if (!(self instanceof ServerLevel server) || server.getChunkSource() == null) return null;
            this.alpha_omega$geometry = Orbifold.of(self);
            this.alpha_omega$geometryKnown = true;
        }
        return this.alpha_omega$geometry;
    }

    @Inject(method = "neighborShapeChanged", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$ownedShapeUpdate(Direction direction, BlockState neighbour, BlockPos pos, BlockPos neighbourPos, int flags, int recursionLeft,
        CallbackInfo ci) {
        if (this.alpha_omega$geometry() == null) return;
        if (BandReactions.shapeChanged((Level) (Object) this, direction, neighbour, pos, neighbourPos, flags, recursionLeft)) ci.cancel();
    }

    @WrapOperation(method = "updateNeighbourForOutputSignal", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onNeighborChange(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"))
    private void alpha_omega$ownedNeighbourChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbour, Operation<Void> original) {
        BlockPos[] target = this.alpha_omega$geometry() == null ? null : BandReactions.outputSignalTarget((Level) (Object) this, pos, neighbour);
        if (target == null) {
            original.call(state, level, pos, neighbour);
        } else if (target.length == 2) {
            original.call(((Level) (Object) this).getBlockState(target[0]), level, target[0], target[1]);
        }
    }
}
