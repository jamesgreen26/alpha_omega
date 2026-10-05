package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla retries unloading a chunk at once when a generation task still holds it. While the server stops, that
 * retry runs inside the same loop, over and over, and the generation task (which needs the main thread to finish)
 * never gets to run: the server hangs. Neighbouring faces leave many generations in flight, so here it happens. The
 * retry now goes through the main thread's queue, which the stopping server keeps running between passes.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapUnloadMixin {

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @WrapOperation(method = "lambda$scheduleUnload$12", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"))
    private void alpha_omega$retryLater(ChunkMap map, long pos, ChunkHolder holder, Operation<Void> original) {
        this.mainThreadExecutor.tell(() -> original.call(map, pos, holder));
    }
}
