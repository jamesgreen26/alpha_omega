package g_mungus.alpha_omega.mixin.compat.c2me;

import com.ishland.flowsched.scheduler.StatusAdvancingScheduler;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapContext;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R2 for C2ME's chunk system, which replaces vanilla's chunk holders with its own scheduler. Dependencies are the
 * holder's position plus an offset, so near the seam they name other images of canonical chunks; every key is
 * resolved to the canonical chunk so each chunk has one holder. The chunk system is built in the chunk map's
 * constructor, while its level's {@link WrapContext} is set.
 */
@Mixin(value = StatusAdvancingScheduler.class, remap = false)
abstract class StatusAdvancingSchedulerMixin {

    @Unique
    private Wrap alpha_omega$wrap = Wrap.NONE;

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void alpha_omega$capture(CallbackInfo ci) {
        this.alpha_omega$wrap = WrapContext.current();
    }

    @Inject(method = "<init>(Lcom/ishland/flowsched/scheduler/ObjectFactory;)V", at = @At("RETURN"))
    private void alpha_omega$captureWithFactory(CallbackInfo ci) {
        this.alpha_omega$wrap = WrapContext.current();
    }

    @ModifyVariable(method = {"getHolder", "getOrCreateHolder", "addTicket0", "removeTicket0", "swapTicket"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Object alpha_omega$canonKey(Object key) {
        return key instanceof ChunkPos pos ? this.alpha_omega$wrap.canon(pos) : key;
    }
}
