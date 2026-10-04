package g_mungus.alpha_omega.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Players track sub-levels near any image of them (R5), so ships stay visible across the seam and between frames. */
@Mixin(value = SubLevelTrackingSystem.class, remap = false)
abstract class SubLevelTrackingSystemMixin {

    @WrapOperation(method = "shouldLoad", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3dc;distanceSquared(DDD)D"))
    private double alpha_omega$nearestImageDistance(Vector3dc subLevel, double x, double y, double z, Operation<Double> original,
                                                    @Local(argsOnly = true) Player player) {
        Wrap wrap = Wrap.of(player.level());
        return original.call(subLevel, wrap.nearest(x, subLevel.x()), y, wrap.nearest(z, subLevel.z()));
    }
}
