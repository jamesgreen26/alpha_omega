package g_mungus.alpha_omega.mixin.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundSignUpdatePacket.class)
public interface SignUpdatePacketAccessor {

    @Mutable
    @Accessor("pos")
    void alpha_omega$setPos(BlockPos value);
}
