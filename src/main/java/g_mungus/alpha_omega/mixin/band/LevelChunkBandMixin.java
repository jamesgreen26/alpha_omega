package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandChunk;
import g_mungus.alpha_omega.band.BandCounters;
import g_mungus.alpha_omega.band.BandData;
import g_mungus.alpha_omega.band.BandFill;
import g_mungus.alpha_omega.band.BandWrites;
import g_mungus.alpha_omega.band.CopyLinks;
import g_mungus.alpha_omega.band.Ownership;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Band rules on {@code LevelChunk}, the one choke point for block writes (RS §3.2, §3.4):
 *
 * <ul>
 * <li>a changed section cell is mirrored into every loaded copy, before {@code onRemove}/{@code onPlace};</li>
 * <li>a mirrored write runs no {@code onPlace}/{@code onRemove}, creates and removes no block entity (it only updates
 * one already there), and so leaves the owner's block entity for the write's own {@code onRemove} to see;</li>
 * <li>a write at a copy that does not own its cell leaves the owner's block entity alone, and creates one only if the
 * owner has none: the block entity created there claims the cell;</li>
 * <li>block entity lookups and removals at a non-owner copy go to the owner; no ticker is registered at one;</li>
 * <li>the band fill runs as the chunk joins its level.</li>
 * </ul>
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkBandMixin implements BandChunk {

    @Shadow
    @Final
    Level level;
    @Shadow
    private boolean loaded;

    @Shadow
    private void removeBlockEntityTicker(BlockPos pos) {
    }

    @Unique
    @Nullable
    private CopyLinks alpha_omega$links;
    @Unique
    @Nullable
    private BandData alpha_omega$data;
    /** 0 not yet known, 1 not filled, 2 filled. */
    @Unique
    private byte alpha_omega$filled;
    @Unique
    private boolean alpha_omega$fresh;

    // ---- BandChunk ----

    @Override
    public CopyLinks alpha_omega$links() {
        CopyLinks links = this.alpha_omega$links;
        if (links == null) {
            if (this.level.isClientSide) return this.alpha_omega$links = CopyLinks.NONE;
            OrbifoldGeometry geometry = Band.geometry(this.level);
            // No geometry: not an orbifold, or asked before the level was ready. Answer without caching.
            if (geometry == null) return CopyLinks.NONE;
            ChunkPos pos = ((LevelChunk) (Object) this).getPos();
            links = this.alpha_omega$links = CopyLinks.compute(geometry, pos.x, pos.z);
        }
        return links;
    }

    @Override
    @Nullable
    public BandData alpha_omega$data(boolean create) {
        BandData data = this.alpha_omega$data;
        if (data == null) {
            LevelChunk self = (LevelChunk) (Object) this;
            if (!create && !self.hasData(BandData.TYPE)) return null;
            data = this.alpha_omega$data = self.getData(BandData.TYPE);
        }
        return data;
    }

    @Override
    public boolean alpha_omega$filled() {
        if (this.alpha_omega$filled == 0) this.alpha_omega$filled = (byte) (this.alpha_omega$links().band ? 1 : 2);
        return this.alpha_omega$filled == 2;
    }

    @Override
    public void alpha_omega$setFilled(boolean filled) {
        this.alpha_omega$filled = (byte) (filled ? 2 : 1);
    }

    @Override
    public boolean alpha_omega$fresh() {
        return this.alpha_omega$fresh;
    }

    @Override
    public boolean alpha_omega$inLevel() {
        return this.loaded;
    }

    // ---- Promotion ----

    /** Promoted from a proto chunk: generated content. A band chunk's generated entities are dropped with it. */
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
        at = @At("RETURN"))
    private void alpha_omega$fromProto(ServerLevel level, ProtoChunk proto, @Nullable LevelChunk.PostLoadProcessor postLoad, CallbackInfo ci) {
        this.alpha_omega$fresh = true;
        if (this.alpha_omega$links().band) proto.getEntities().clear();
    }

    /** The chunk joins its level (inside the {@code FULL} step, before its block entities and ticks are registered). */
    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("HEAD"))
    private void alpha_omega$promote(CallbackInfo ci) {
        if (this.level instanceof ServerLevel server && this.alpha_omega$linked()) BandFill.promote(server, (LevelChunk) (Object) this);
    }

    // ---- Writes ----

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

    /** A mirrored write leaves an invalid block entity for the original write's {@code onRemove} (or the after-write sweep). */
    @WrapWithCondition(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/LevelChunk;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"))
    private boolean alpha_omega$noRemovalWhenMirrored(LevelChunk chunk, BlockPos pos) {
        return !BandWrites.mirroring();
    }

    /** The owner's block entity, found through the redirect at a non-owner copy, is not this write's to update. */
    @ModifyExpressionValue(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity alpha_omega$ownersBlockEntityIsNotOurs(@Nullable BlockEntity entity, @Local(argsOnly = true) BlockPos pos,
        @Share("foreign") LocalBooleanRef foreign) {
        if (entity != null && !entity.getBlockPos().equals(pos)) {
            foreign.set(true);
            return null;
        }
        return entity;
    }

    /** Block entities are created by the original write only, and not where the owner already has one. */
    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/EntityBlock;newBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity alpha_omega$createOnlyWhereWritten(EntityBlock block, BlockPos pos, BlockState state, Operation<BlockEntity> original,
        @Share("foreign") LocalBooleanRef foreign) {
        if (BandWrites.mirroring() || foreign.get()) return null;
        return original.call(block, pos, state);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void alpha_omega$afterWrite(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && !this.level.isClientSide && this.alpha_omega$linked()) {
            BandWrites.afterWrite(this.level, (LevelChunk) (Object) this, pos);
        }
    }

    // ---- Block entities ----

    /** At a non-owner copy with no block entity of its own, the owner's (RS §3.4). Not during mirrored writes. */
    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("HEAD"), cancellable = true)
    private void alpha_omega$ownersBlockEntity(BlockPos pos, LevelChunk.EntityCreationType type, CallbackInfoReturnable<BlockEntity> cir) {
        if (this.level.isClientSide || BandWrites.mirroring() || !this.alpha_omega$linked()) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getBlockEntities().containsKey(pos) || ((ChunkAccessAccessor) self).alpha_omega$pendingBlockEntities().containsKey(pos)) return;
        if (Ownership.isOwner(self, pos)) return;
        cir.setReturnValue(Ownership.ownersBlockEntity(this.level, self, pos, type));
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$removeOwnersBlockEntity(BlockPos pos, CallbackInfo ci) {
        if (this.level.isClientSide || !this.alpha_omega$linked()) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getBlockEntities().containsKey(pos)) {
            // Our own: removing it gives up the cell (if it was a claim).
            if (this.loaded && !BandWrites.mirroring()) Ownership.release(this.level, self, pos);
            return;
        }
        if (Ownership.isOwner(self, pos)) return;
        CopyLinks.Link owner = Ownership.owner(this.level, self, pos);
        if (owner == null) return;
        ci.cancel();
        LevelChunk chunk = owner.chunk(this.level);
        BlockPos at = owner.map(pos);
        if (chunk != null && chunk.getBlockEntities().containsKey(at)) chunk.removeBlockEntity(at);
    }

    /** A block entity set at a copy claims the cell while it exists (RS §3.5, "Block entities created explicitly"). */
    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void alpha_omega$claim(BlockEntity entity, CallbackInfo ci) {
        if (!this.loaded || this.level.isClientSide || BandWrites.mirroring() || !this.alpha_omega$linked()) return;
        LevelChunk self = (LevelChunk) (Object) this;
        if (self.getBlockEntities().get(entity.getBlockPos()) != entity) return;
        if (!Ownership.isOwner(self, entity.getBlockPos())) Ownership.claim(this.level, self, entity.getBlockPos());
    }

    @Inject(method = "updateBlockEntityTicker", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$noTickerAtCopies(BlockEntity entity, CallbackInfo ci) {
        if (this.level.isClientSide || !this.alpha_omega$linked()) return;
        if (Ownership.isOwner((LevelChunk) (Object) this, entity.getBlockPos())) return;
        ci.cancel();
        this.removeBlockEntityTicker(entity.getBlockPos());
        BandCounters.tickersSkipped++;
    }
}
