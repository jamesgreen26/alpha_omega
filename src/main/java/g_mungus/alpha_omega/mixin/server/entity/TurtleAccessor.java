package g_mungus.alpha_omega.mixin.server.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Turtle.class)
public interface TurtleAccessor {

    @Invoker("getHomePos") BlockPos alpha_omega$getHomePos();
    @Invoker("setHomePos") void alpha_omega$setHomePos(BlockPos pos);
    @Invoker("getTravelPos") BlockPos alpha_omega$getTravelPos();
    @Invoker("setTravelPos") void alpha_omega$setTravelPos(BlockPos pos);
}
