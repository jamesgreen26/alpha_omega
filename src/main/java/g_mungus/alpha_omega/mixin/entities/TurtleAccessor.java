package g_mungus.alpha_omega.mixin.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Turtle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A turtle's home beach and travel target. */
@Mixin(Turtle.class)
public interface TurtleAccessor {

    @Invoker("getHomePos")
    BlockPos alpha_omega$getHomePos();

    @Invoker("getTravelPos")
    BlockPos alpha_omega$getTravelPos();

    @Invoker("setTravelPos")
    void alpha_omega$setTravelPos(BlockPos pos);
}
