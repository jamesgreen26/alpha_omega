package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * R5: a player's view (centered on their lifted chunk) contains a chunk if any image of it is in range.
 * Unambiguous because the period exceeds twice the view distance.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkTrackingView$Positioned")
abstract class ChunkTrackingViewPositionedMixin {

    @Shadow
    @Final
    private ChunkPos center;

    @ModifyVariable(method = "contains(IIZ)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$nearestX(int x) {
        return Wrap.nearestChunk(x, this.center.x);
    }

    @ModifyVariable(method = "contains(IIZ)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$nearestZ(int z) {
        return Wrap.nearestChunk(z, this.center.z);
    }
}
