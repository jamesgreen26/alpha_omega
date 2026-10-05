package g_mungus.alpha_omega.mixin.images.compat.sodium;

import g_mungus.alpha_omega.neighbour.ImageGeometry;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SectionCollector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With Sodium, home draws the tile and band only, as with vanilla ({@code ImageRenderer.hideDead}): sections of the
 * skirt and the void beyond are never collected to draw or build. The images' own sections are tile chunks, so they
 * pass.
 */
@Mixin(value = SectionCollector.class, remap = false)
abstract class SectionCollectorMixin {

    @Inject(method = "visit(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;I)V", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$homeDrawsLiveOnly(RenderSection section, int flags, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        OrbifoldGeometry geometry = level == null ? null : Orbifold.of(level);
        if (geometry != null && !ImageGeometry.homeDraws(geometry, section.getChunkX(), section.getChunkZ())) ci.cancel();
    }
}
