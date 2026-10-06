package g_mungus.alpha_omega.mixin.band;

import g_mungus.alpha_omega.band.BandTicketHolder;
import g_mungus.alpha_omega.band.BandTickets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Paired tickets ({@link BandTickets}) on the loading tracker: every ticket added or removed here is mirrored. */
@Mixin(DistanceManager.class)
abstract class DistanceManagerBandMixin implements BandTicketHolder {

    @Shadow
    @Final
    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets;

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
        BandTickets.added(this.alpha_omega$bandTickets, this, false, chunk, ticket);
    }

    @Inject(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("TAIL"))
    private void alpha_omega$pairRemoved(long chunk, Ticket<?> ticket, CallbackInfo ci) {
        BandTickets.removed(this.alpha_omega$bandTickets, this, false, chunk, ticket);
    }

    @Inject(method = "purgeStaleTickets", at = @At("TAIL"))
    private void alpha_omega$pairPurged(CallbackInfo ci) {
        BandTickets.purged(this.alpha_omega$bandTickets, (DistanceManager) (Object) this, this.tickets::get);
    }
}
