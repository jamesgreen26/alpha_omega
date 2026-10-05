package g_mungus.alpha_omega.mixin.server;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DistanceManager.class)
public interface DistanceManagerAccessor {

    @Invoker("tickingTracker")
    TickingTracker alpha_omega$tickingTracker();
}
