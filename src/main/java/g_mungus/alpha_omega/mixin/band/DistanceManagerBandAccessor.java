package g_mungus.alpha_omega.mixin.band;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DistanceManager.class)
interface DistanceManagerBandAccessor {

    @Accessor("tickingTicketsTracker")
    TickingTracker alpha_omega$tickingTicketsTracker();
}
