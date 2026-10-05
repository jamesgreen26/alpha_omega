package g_mungus.alpha_omega.mixin.band;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.band.BandCounters;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Block entity data for a copy (RS §3.4): at a non-owner copy the chunk holder finds the owner's block entity (through
 * the lookup redirect), whose packet names the owner's position; it is re-addressed to the copy's.
 */
@Mixin(ChunkHolder.class)
abstract class ChunkHolderBandMixin {

    @WrapOperation(method = "broadcastBlockEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getUpdatePacket()Lnet/minecraft/network/protocol/Packet;"))
    private Packet<?> alpha_omega$atCopy(BlockEntity entity, Operation<Packet<?>> original, @Local(argsOnly = true) BlockPos pos) {
        Packet<?> packet = original.call(entity);
        if (packet instanceof ClientboundBlockEntityDataPacket data && !entity.getBlockPos().equals(pos)) {
            BandCounters.replicasSent++;
            return BlockEntityDataPacketInvoker.alpha_omega$create(pos.immutable(), data.getType(), data.getTag());
        }
        return packet;
    }
}
