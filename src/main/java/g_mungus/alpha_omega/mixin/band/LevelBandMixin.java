package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandChunk;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandLevel;
import g_mungus.alpha_omega.band.BandWrites;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Band rules on {@code Level}: the cached geometry; shape updates for a non-owner copy skipped or forwarded to the owner
 * (every shape update goes through {@code neighborShapeChanged}); comparator-style {@code onNeighborChange} likewise;
 * and after a block is marked and notified, its copies are notified too.
 */
@Mixin(Level.class)
abstract class LevelBandMixin implements BandLevel {

    @Shadow
    @Final
    public boolean isClientSide;

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
        OrbifoldGeometry geometry = this.alpha_omega$geometry();
        if (geometry == null || Band.mode == Band.Mode.NONE) return;
        Level self = (Level) (Object) this;
        Band.Link owner = Band.owner(self, geometry, pos);
        if (owner == null) return;
        ci.cancel();
        if (Band.mode == Band.Mode.SKIP) {
            BandCounters.shapeSkipped++;
            return;
        }
        if (Band.loadedChunk(self, owner.pos()) == null) {
            BandCounters.forwardsDropped++;
            return;
        }
        BandCounters.shapeForwarded++;
        Transform turn = owner.transform();
        self.neighborShapeChanged(turn.direction(direction), turn.state(neighbour), owner.pos(), turn.block(neighbourPos), flags, recursionLeft);
    }

    @WrapOperation(method = "updateNeighbourForOutputSignal", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onNeighborChange(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V"))
    private void alpha_omega$ownedNeighbourChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbour, Operation<Void> original) {
        Level self = (Level) (Object) this;
        OrbifoldGeometry geometry = this.alpha_omega$geometry();
        Band.Link owner = geometry == null || Band.mode == Band.Mode.NONE ? null : Band.owner(self, geometry, pos);
        if (owner == null) {
            original.call(state, level, pos, neighbour);
            return;
        }
        if (Band.mode == Band.Mode.SKIP) {
            BandCounters.neighbourSkipped++;
            return;
        }
        LevelChunk chunk = Band.loadedChunk(self, owner.pos());
        if (chunk == null) {
            BandCounters.forwardsDropped++;
            return;
        }
        BandCounters.comparatorForwarded++;
        original.call(chunk.getBlockState(owner.pos()), level, owner.pos(), owner.transform().block(neighbour));
    }

    @Inject(method = "markAndNotifyBlock", at = @At("TAIL"))
    private void alpha_omega$notifyCopies(BlockPos pos, @Nullable LevelChunk chunk, BlockState old, BlockState state, int flags, int recursionLeft,
        CallbackInfo ci) {
        if (chunk == null || this.isClientSide || !((BandChunk) chunk).alpha_omega$linked()) return;
        BandWrites.notifyCopies((Level) (Object) this, pos, old, state, flags, recursionLeft);
    }
}
