package g_mungus.alpha_omega.mixin.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundOpenSignEditorPacket.class)
public interface OpenSignEditorPacketAccessor {

    @Mutable
    @Accessor("pos")
    void alpha_omega$setPos(BlockPos value);
}
