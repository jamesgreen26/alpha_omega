package g_mungus.alpha_omega.mixin.compat.c2me;

import com.ishland.flowsched.scheduler.StatusAdvancingScheduler;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For {@code C2meGameTests}: the chunk system's holders by key. */
@Mixin(value = StatusAdvancingScheduler.class, remap = false)
public interface StatusAdvancingSchedulerAccessor {

    @Accessor("items")
    Object2ReferenceOpenHashMap<Object, ?> alpha_omega$getItems();

    @Accessor("itemsLock")
    StampedLock alpha_omega$getItemsLock();
}
