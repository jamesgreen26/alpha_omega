package g_mungus.alpha_omega.mixin.network;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundExplodePacket.class)
public interface ExplodePacketAccessor {

    @Mutable
    @Accessor("x")
    void alpha_omega$setX(double value);

    @Mutable
    @Accessor("z")
    void alpha_omega$setZ(double value);

    @Mutable
    @Accessor("toBlow")
    void alpha_omega$setToBlow(List<BlockPos> value);
}
