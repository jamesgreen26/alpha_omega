package g_mungus.alpha_omega.mixin.server.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Bee.class)
public interface BeeAccessor {

    @Accessor("hivePos") BlockPos alpha_omega$getHivePos();
    @Accessor("hivePos") void alpha_omega$setHivePos(BlockPos pos);
    @Accessor("savedFlowerPos") BlockPos alpha_omega$getSavedFlowerPos();
    @Accessor("savedFlowerPos") void alpha_omega$setSavedFlowerPos(BlockPos pos);
}
