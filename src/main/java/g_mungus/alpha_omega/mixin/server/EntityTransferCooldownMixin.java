package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.transfer.TransferCooldown;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers when each entity last crossed an edge, so it cannot cross again straight away. */
@Mixin(Entity.class)
abstract class EntityTransferCooldownMixin implements TransferCooldown {

    /** Long enough ago that a new entity is never cooling down. */
    @Unique
    private int alpha_omega$lastTransferTick = Integer.MIN_VALUE / 2;

    @Override
    public int alpha_omega$lastTransferTick() {
        return this.alpha_omega$lastTransferTick;
    }

    @Override
    public void alpha_omega$setLastTransferTick(int tick) {
        this.alpha_omega$lastTransferTick = tick;
    }
}
