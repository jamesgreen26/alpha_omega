package g_mungus.alpha_omega.mixin.network;

import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundDamageEventPacket.class)
public interface DamageEventPacketAccessor {

    @Mutable
    @Accessor("sourcePosition")
    void alpha_omega$setSourcePosition(Optional<Vec3> value);
}
