package g_mungus.alpha_omega.band;

import org.jetbrains.annotations.Nullable;

/**
 * A {@code DistanceManager} or its {@code TickingTracker}, knowing its level's paired tickets (set by a mixin when the
 * chunk map is built; both trackers of a level share one state).
 */
public interface BandTicketHolder {

    void alpha_omega$setBandTickets(BandTickets.State state);

    @Nullable
    BandTickets.State alpha_omega$bandTickets();
}
