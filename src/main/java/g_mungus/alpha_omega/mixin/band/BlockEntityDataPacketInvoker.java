package g_mungus.alpha_omega.mixin.band;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundBlockEntityDataPacket.class)
interface BlockEntityDataPacketInvoker {

    @Invoker("<init>")
    static ClientboundBlockEntityDataPacket alpha_omega$create(BlockPos pos, BlockEntityType<?> type, CompoundTag tag) {
        throw new AssertionError();
    }
}
