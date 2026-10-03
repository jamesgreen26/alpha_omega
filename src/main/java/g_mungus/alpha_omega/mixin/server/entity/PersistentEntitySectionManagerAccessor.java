package g_mungus.alpha_omega.mixin.server.entity;

import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor {

    @Accessor("sectionStorage")
    <T extends EntityAccess> EntitySectionStorage<T> alpha_omega$getSectionStorage();
}
