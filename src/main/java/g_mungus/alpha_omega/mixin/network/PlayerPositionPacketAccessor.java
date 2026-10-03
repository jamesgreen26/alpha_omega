package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundPlayerPositionPacket.class)
public interface PlayerPositionPacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(double value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(double value);
}
