package g_mungus.alpha_omega.mixin.network;

import java.util.List;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSetEntityDataPacket.class)
public interface SetEntityDataPacketAccessor {

    @Mutable
    @Accessor("packedItems")
    void alpha_omega$setPackedItems(List<SynchedEntityData.DataValue<?>> value);
}
