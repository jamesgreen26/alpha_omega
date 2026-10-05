package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandChunk;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandWrites;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Band rules on {@code LevelChunk}, the one choke point for block writes (RS §3.2): a changed section cell is mirrored
 * into every loaded copy; a mirrored write runs no {@code onPlace}/{@code onRemove}; block entities are created only at
 * the owner, and looked up or removed at a non-owner copy through the owner (RS §3.4); and no block entity ticker is
 * registered at a non-owner copy.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkBandMixin implements BandChunk {

    @Shadow
    @Final
    Level level;

    @Shadow
    private void removeBlockEntityTicker(BlockPos pos) {
    }

    /** 0 not yet known, 1 not linked, 2 linked. */
    @Unique
    private byte alpha_omega$link;
    /** 0 not yet known, 1 not filled, 2 filled. */
    @Unique
    private byte alpha_omega$filled;

    @Override
    public boolean alpha_omega$linked() {
        if (this.alpha_omega$link == 0) {
            OrbifoldGeometry geometry = Band.geometry(this.level);
            // No geometry: not an orbifold, or asked before the level was ready. Answer without caching.
            if (geometry == null) return false;
            ChunkPos pos = ((LevelChunk) (Object) this).getPos();
            boolean linked = geometry.isTileChunk(pos.x, pos.z) ? !geometry.copiesChunk(pos.x, pos.z).isEmpty()
                : geometry.inFootprintChunk(pos.x, pos.z);
            this.alpha_omega$link = (byte) (linked ? 2 : 1);
        }
        return this.alpha_omega$link == 2;
    }

    @Override
    public boolean alpha_omega$filled() {
        if (this.alpha_omega$filled == 0) {
            OrbifoldGeometry geometry = Band.geometry(this.level);
            ChunkPos pos = ((LevelChunk) (Object) this).getPos();
            this.alpha_omega$filled = (byte) (geometry == null || geometry.isTileChunk(pos.x, pos.z) ? 2 : 1);
        }
        return this.alpha_omega$filled == 2;
    }

    @Override
    public void alpha_omega$setFilled(boolean filled) {
        this.alpha_omega$filled = (byte) (filled ? 2 : 1);
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState alpha_omega$mirror(LevelChunkSection section, int x, int y, int z, BlockState state, Operation<BlockState> original,
        @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) boolean moving) {
        BlockState old = original.call(section, x, y, z, state);
        if (old != state && !this.level.isClientSide && this.alpha_omega$linked()) {
            BandWrites.mirror(this.level, (LevelChunk) (Object) this, pos, state, moving);
        }
        return old;
    }

    @WrapWithCondition(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onRemove(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private boolean alpha_omega$noRemoveWhenMirrored(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        return !BandWrites.mirroring();
    }

    @WrapWithCondition(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private boolean alpha_omega$noPlaceWhenMirrored(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        return !BandWrites.mirroring();
    }

    /** Block entities are created at the owner only (with claims: where the vanilla write happens, never by a mirror). */
    @ModifyExpressionValue(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z", ordinal = 1))
    private boolean alpha_omega$blockEntityAtOwner(boolean has, @Local(argsOnly = true) BlockPos pos) {
        if (!has || this.level.isClientSide || !this.alpha_omega$linked()) return has;
        if (Band.blockEntityClaims) return !BandWrites.mirroring();
        OrbifoldGeometry geometry = Band.geometry(this.level);
        return geometry == null || geometry.isTile(pos.getX(), pos.getZ());
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void alpha_omega$afterWrite(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && !this.level.isClientSide && this.alpha_omega$linked()) BandWrites.afterWrite(this.level, pos);
    }

    /** At a non-owner copy with no block entity of its own, the owner's (RS §3.4). */
    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("HEAD"), cancellable = true)
    private void alpha_omega$ownersBlockEntity(BlockPos pos, LevelChunk.EntityCreationType type, CallbackInfoReturnable<BlockEntity> cir) {
        if (this.level.isClientSide || !this.alpha_omega$linked()) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getBlockEntities().containsKey(pos) || ((ChunkAccessAccessor) self).alpha_omega$pendingBlockEntities().containsKey(pos)) return;
        Band.Link owner = Band.owner(this.level, pos);
        if (owner == null) return;
        LevelChunk chunk = Band.loadedChunk(this.level, owner.pos());
        BandCounters.blockEntityRedirects++;
        cir.setReturnValue(chunk == null ? null : chunk.getBlockEntity(owner.pos(), type));
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$removeOwnersBlockEntity(BlockPos pos, CallbackInfo ci) {
        if (this.level.isClientSide || !this.alpha_omega$linked()) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getBlockEntities().containsKey(pos)) return;
        Band.Link owner = Band.owner(this.level, pos);
        if (owner == null) return;
        ci.cancel();
        LevelChunk chunk = Band.loadedChunk(this.level, owner.pos());
        if (chunk != null && chunk.getBlockEntities().containsKey(owner.pos())) chunk.removeBlockEntity(owner.pos());
    }

    @Inject(method = "updateBlockEntityTicker", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$noTickerAtCopies(BlockEntity entity, CallbackInfo ci) {
        if (this.level.isClientSide || Band.blockEntityClaims || !this.alpha_omega$linked()) return;
        if (Band.owner(this.level, entity.getBlockPos()) == null) return;
        ci.cancel();
        this.removeBlockEntityTicker(entity.getBlockPos());
        BandCounters.tickersSkipped++;
    }
}
