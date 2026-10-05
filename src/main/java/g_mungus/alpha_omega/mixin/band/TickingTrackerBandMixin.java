package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandTicketHolder;
import g_mungus.alpha_omega.band.BandTickets;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TickingTracker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paired tickets ({@link BandTickets}) on the simulation tracker, so a pair ticks together as well as loading together. */
@Mixin(TickingTracker.class)
abstract class TickingTrackerBandMixin implements BandTicketHolder {

    @Unique
    @Nullable
    private BandTickets.State alpha_omega$bandTickets;

    @Override
    public void alpha_omega$setBandTickets(BandTickets.State state) {
        this.alpha_omega$bandTickets = state;
    }

    @Override
    @Nullable
    public BandTickets.State alpha_omega$bandTickets() {
        return this.alpha_omega$bandTickets;
    }

    @Inject(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("TAIL"))
    private void alpha_omega$pairAdded(long chunk, Ticket<?> ticket, CallbackInfo ci) {
        BandTickets.added(this.alpha_omega$bandTickets, this, true, chunk, ticket);
    }

    @Inject(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("TAIL"))
    private void alpha_omega$pairRemoved(long chunk, Ticket<?> ticket, CallbackInfo ci) {
        BandTickets.removed(this.alpha_omega$bandTickets, this, true, chunk, ticket);
    }
}
