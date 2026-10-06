package g_mungus.alpha_omega.mixin.bridges;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
interface ServerLevelEntitiesAccessor {

    @Accessor("entityManager")
    PersistentEntitySectionManager<Entity> alpha_omega$entityManager();
}
