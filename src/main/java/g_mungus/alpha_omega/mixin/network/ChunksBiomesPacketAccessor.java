package g_mungus.alpha_omega.mixin.network;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundChunksBiomesPacket.class)
public interface ChunksBiomesPacketAccessor {

    @Mutable
    @Accessor("chunkBiomeData")
    void alpha_omega$setChunkBiomeData(List<ClientboundChunksBiomesPacket.ChunkBiomeData> value);
}
