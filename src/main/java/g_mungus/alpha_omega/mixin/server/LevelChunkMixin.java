package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R1/R2 for block entities on the server: they are created, stored and looked up at canonical positions.
 * Client chunks live in the client's unrolled frame and are left alone.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {

    @Shadow
    @Final
    Level level;

    @ModifyVariable(method = {
        "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        "removeBlockEntity",
        "getBlockEntityNbtForSaving",
        "createBlockEntity",
        "promotePendingBlockEntity",
        "removeBlockEntityTicker",
    }, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonPos(BlockPos pos) {
        return this.level.isClientSide ? pos : Wrap.canon(pos);
    }

    @ModifyArg(method = "setBlockState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/EntityBlock;newBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockPos alpha_omega$canonNewBlockEntity(BlockPos pos) {
        return this.level.isClientSide ? pos : Wrap.canon(pos);
    }

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    private void alpha_omega$canonBlockEntity(BlockEntity blockEntity, CallbackInfo ci) {
        if (this.level.isClientSide) return;
        BlockPos pos = blockEntity.getBlockPos();
        BlockPos canon = Wrap.canon(pos);
        if (canon != pos) ((BlockEntityAccessor) blockEntity).alpha_omega$setWorldPosition(canon);
    }
}
