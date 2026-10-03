package g_mungus.alpha_omega.mixin.server.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.Wrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crossing the seam is not a section change unless the canonical section changes. */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
abstract class PersistentEntitySectionManagerCallbackMixin {

    @ModifyExpressionValue(method = "onMove",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long alpha_omega$canonSection(long sectionKey) {
        return Wrap.canonSectionKey(sectionKey);
    }
}
