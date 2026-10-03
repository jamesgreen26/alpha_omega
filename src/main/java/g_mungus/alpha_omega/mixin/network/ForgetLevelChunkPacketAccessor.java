package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundForgetLevelChunkPacket.class)
public interface ForgetLevelChunkPacketAccessor {

    @Mutable
    @Accessor("pos")
    void alpha_omega$setPos(ChunkPos value);
}
