package g_mungus.alpha_omega.mixin.bridges;

import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
interface PersistentEntitySectionManagerAccessor {

    @Accessor("sectionStorage")
    EntitySectionStorage<?> alpha_omega$sectionStorage();
}
