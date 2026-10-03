package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLightUpdatePacket.class)
public interface LightUpdatePacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(int value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(int value);
}
