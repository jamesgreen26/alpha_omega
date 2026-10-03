package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
public interface LevelChunkWithLightPacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(int value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(int value);
}
