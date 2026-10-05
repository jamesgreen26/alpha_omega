package g_mungus.alpha_omega.transfer;

/** The entity tick ({@code Entity.tickCount}) at which an entity last crossed an edge; added to every entity by mixin. */
public interface TransferCooldown {

    int alpha_omega$lastTransferTick();

    void alpha_omega$setLastTransferTick(int tick);
}
