package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.server.level.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ticket level propagation treats chunk 0 and chunk N-1 as neighbors. */
@Mixin(ChunkTracker.class)
abstract class ChunkTrackerMixin {

    @ModifyExpressionValue(method = {"checkNeighborsAfterUpdate", "getComputedLevel"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ChunkPos;asLong(II)J"))
    private long alpha_omega$wrapNeighbor(long neighbor) {
        return WrapHolder.of(this).canonChunkKey(neighbor);
    }
}
