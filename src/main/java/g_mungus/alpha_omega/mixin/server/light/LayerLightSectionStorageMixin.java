package g_mungus.alpha_omega.mixin.server.light;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Section neighbor bookkeeping wraps, so sections on either side of the seam count each other as neighbors. */
@Mixin(LayerLightSectionStorage.class)
abstract class LayerLightSectionStorageMixin {

    @ModifyExpressionValue(method = "updateSectionStatus",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;offset(JIII)J"))
    private long alpha_omega$wrapNeighbor(long sectionKey) {
        return WrapHolder.of(this).canonSectionKey(sectionKey);
    }

    @ModifyVariable(method = "lightOnInSection", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonSection(long sectionKey) {
        return WrapHolder.of(this).canonSectionKey(sectionKey);
    }
}
