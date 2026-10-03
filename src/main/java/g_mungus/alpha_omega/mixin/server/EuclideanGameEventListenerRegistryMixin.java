package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** R5: a game event reaches listeners in range of any image of it (sculk sensors, wardens). */
@Mixin(EuclideanGameEventListenerRegistry.class)
abstract class EuclideanGameEventListenerRegistryMixin {

    @WrapOperation(method = "getPostableListenerPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double alpha_omega$nearestImageDistance(BlockPos listener, Vec3i event, Operation<Double> original) {
        int x = Wrap.nearestBlock(event.getX(), listener.getX());
        int z = Wrap.nearestBlock(event.getZ(), listener.getZ());
        return original.call(listener, new BlockPos(x, event.getY(), z));
    }
}
