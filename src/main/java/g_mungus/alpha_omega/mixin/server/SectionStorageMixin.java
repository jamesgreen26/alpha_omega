package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R2: section-keyed storage (POIs) resolves any image of a section to the canonical one. */
@Mixin(SectionStorage.class)
abstract class SectionStorageMixin {

    @ModifyVariable(method = {"get(J)Ljava/util/Optional;", "getOrLoad(J)Ljava/util/Optional;", "getOrCreate(J)Ljava/lang/Object;", "setDirty(J)V", "remove(J)V"},
        at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonSection(long sectionKey) {
        return Wrap.canonSectionKey(sectionKey);
    }
}
