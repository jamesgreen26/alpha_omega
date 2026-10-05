package g_mungus.alpha_omega.mixin.compat.sodium;

import g_mungus.alpha_omega.compat.sodium.SodiumNeighbours;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium's visible-section search, extended to the neighbouring faces: after it has searched from the camera, each
 * neighbouring face's sections are collected from the camera as seen from that face, and queue to build alongside the
 * camera's own ({@link SodiumNeighbours}).
 */
@Mixin(RenderSectionManager.class)
abstract class RenderSectionManagerMixin {

    @Inject(method = "createTerrainRenderList", at = @At("RETURN"), cancellable = true)
    private void alpha_omega$collectNeighbours(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
        if (SodiumNeighbours.collect((RenderSectionManager) (Object) this, camera, frame)) cir.setReturnValue(true);
    }

    @Inject(method = "finalizeRenderLists", at = @At("TAIL"))
    private void alpha_omega$finalizeNeighbours(Viewport viewport, CallbackInfo ci) {
        SodiumNeighbours.finalizeLists((RenderSectionManager) (Object) this);
    }

    @Inject(method = "tickVisibleRenders", at = @At("TAIL"))
    private void alpha_omega$tickNeighbours(CallbackInfo ci) {
        SodiumNeighbours.tickVisible((RenderSectionManager) (Object) this);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void alpha_omega$forgetNeighbours(CallbackInfo ci) {
        SodiumNeighbours.forget((RenderSectionManager) (Object) this);
    }
}
