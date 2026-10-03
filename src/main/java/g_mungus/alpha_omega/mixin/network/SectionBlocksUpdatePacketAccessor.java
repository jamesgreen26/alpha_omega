package g_mungus.alpha_omega.mixin.network;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface SectionBlocksUpdatePacketAccessor {

    @Accessor("sectionPos")
    SectionPos alpha_omega$getSectionPos();

    @Mutable
    @Accessor("sectionPos")
    void alpha_omega$setSectionPos(SectionPos value);
}
