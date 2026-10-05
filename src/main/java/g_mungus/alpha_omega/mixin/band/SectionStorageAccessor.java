package g_mungus.alpha_omega.mixin.band;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionStorage.class)
interface SectionStorageAccessor {

    @Accessor("levelHeightAccessor")
    LevelHeightAccessor alpha_omega$levelHeightAccessor();
}
