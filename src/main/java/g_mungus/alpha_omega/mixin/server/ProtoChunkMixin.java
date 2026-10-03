package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Worldgen can write block entities into a neighbor across the seam using a non-canonical position. */
@Mixin(ProtoChunk.class)
abstract class ProtoChunkMixin {

    @Unique
    private Wrap alpha_omega$wrap() {
        LevelHeightAccessor heights = ((ChunkAccessAccessor) this).alpha_omega$getLevelHeightAccessor();
        return heights instanceof Level level ? Wrap.of(level) : Wrap.NONE;
    }

    @ModifyVariable(method = {"getBlockEntity", "removeBlockEntity", "getBlockEntityNbtForSaving"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonPos(BlockPos pos) {
        return this.alpha_omega$wrap().canon(pos);
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void alpha_omega$canonBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        BlockPos pos = blockEntity.getBlockPos();
        BlockPos canon = this.alpha_omega$wrap().canon(pos);
        if (canon != pos) ((BlockEntityAccessor) blockEntity).alpha_omega$setWorldPosition(canon);
    }
}
