package g_mungus.alpha_omega.mixin.client;

import g_mungus.alpha_omega.client.NeighbourRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sections compile nearest the camera first. A section of a neighbouring face is thousands of blocks from the camera in
 * storage, so it would always come last; it is measured from where the camera is seen from that face instead, with
 * the camera's own face a little ahead ({@link NeighbourRenderer#compileDistanceSqr}).
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionMixin {

    @Shadow
    private AABB bb;

    @Inject(method = "getDistToPlayerSqr", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$acrossEdges(CallbackInfoReturnable<Double> cir) {
        double distance = NeighbourRenderer.compileDistanceSqr(this.bb);
        if (!Double.isNaN(distance)) cir.setReturnValue(distance);
    }
}
