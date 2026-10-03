package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundTeleportEntityPacket.class)
public interface TeleportEntityPacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(double value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(double value);
}
