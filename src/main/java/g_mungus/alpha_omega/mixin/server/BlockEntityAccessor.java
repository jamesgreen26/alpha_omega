package g_mungus.alpha_omega.mixin.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockEntity.class)
public interface BlockEntityAccessor {

    @Mutable
    @Accessor("worldPosition")
    void alpha_omega$setWorldPosition(BlockPos pos);
}
