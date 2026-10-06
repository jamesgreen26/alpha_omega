package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.transfer.TransferCooldown;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers when each entity last changed frame, and until when it is anchored in one. */
@Mixin(Entity.class)
abstract class EntityTransferCooldownMixin implements TransferCooldown {

    /** Long enough ago that a new entity is never cooling down. */
    @Unique
    private int alpha_omega$lastTransferTick = Integer.MIN_VALUE / 2;
    @Unique
    private int alpha_omega$anchoredUntil = Integer.MIN_VALUE / 2;

    @Override
    public int alpha_omega$lastTransferTick() {
        return this.alpha_omega$lastTransferTick;
    }

    @Override
    public void alpha_omega$setLastTransferTick(int tick) {
        this.alpha_omega$lastTransferTick = tick;
    }

    @Override
    public int alpha_omega$anchoredUntil() {
        return this.alpha_omega$anchoredUntil;
    }

    @Override
    public void alpha_omega$setAnchoredUntil(int tick) {
        this.alpha_omega$anchoredUntil = tick;
    }
}
