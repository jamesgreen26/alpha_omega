package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMoveVehiclePacket.class)
public interface ServerboundMoveVehiclePacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(double value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(double value);
}
