package g_mungus.alpha_omega.mixin.band;

import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Ticket.class)
public interface TicketAccessor {

    @Accessor("key")
    Object alpha_omega$key();
}
