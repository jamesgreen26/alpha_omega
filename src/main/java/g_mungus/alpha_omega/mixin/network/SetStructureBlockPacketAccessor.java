package g_mungus.alpha_omega.mixin.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundSetStructureBlockPacket.class)
public interface SetStructureBlockPacketAccessor {

    @Mutable
    @Accessor("pos")
    void alpha_omega$setPos(BlockPos value);
}
