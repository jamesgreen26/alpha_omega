package g_mungus.alpha_omega.mixin.server.entity;

import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Brain.class)
public interface BrainAccessor {

    @Accessor("memories")
    Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> alpha_omega$getMemories();
}
