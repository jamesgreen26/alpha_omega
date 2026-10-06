package g_mungus.alpha_omega.transfer;

/**
 * Per-entity transfer timing, added to every entity by mixin: the entity tick ({@code Entity.tickCount}) at which it
 * last changed frame, and until which tick it is anchored (its own code put it in a frame, so followers leave it).
 */
public interface TransferCooldown {

    int alpha_omega$lastTransferTick();

    void alpha_omega$setLastTransferTick(int tick);

    int alpha_omega$anchoredUntil();

    void alpha_omega$setAnchoredUntil(int tick);
}
