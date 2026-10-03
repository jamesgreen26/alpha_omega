package g_mungus.alpha_omega.mixin.network;

import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundUseItemOnPacket.class)
public interface UseItemOnPacketAccessor {

    @Mutable
    @Accessor("blockHit")
    void alpha_omega$setBlockHit(BlockHitResult value);
}
