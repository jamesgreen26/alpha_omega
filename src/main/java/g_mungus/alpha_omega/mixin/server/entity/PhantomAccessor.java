package g_mungus.alpha_omega.mixin.server.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Phantom.class)
public interface PhantomAccessor {

    @Accessor("anchorPoint") BlockPos alpha_omega$getAnchorPoint();
    @Accessor("anchorPoint") void alpha_omega$setAnchorPoint(BlockPos pos);
    @Accessor("moveTargetPoint") Vec3 alpha_omega$getMoveTargetPoint();
    @Accessor("moveTargetPoint") void alpha_omega$setMoveTargetPoint(Vec3 pos);
}
