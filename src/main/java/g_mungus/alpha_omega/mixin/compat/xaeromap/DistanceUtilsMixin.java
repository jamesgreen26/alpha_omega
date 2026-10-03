package g_mungus.alpha_omega.mixin.compat.xaeromap;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xaero.map.util.DistanceUtils;

/** Distances to map elements are to their nearest image. */
@Mixin(DistanceUtils.class)
abstract class DistanceUtilsMixin {

    @ModifyVariable(method = "addDistanceRightClickOption", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static double alpha_omega$nearestX(double posX, @Local(argsOnly = true) Entity from) {
        return from == null ? posX : Wrap.of(from.level()).nearest(posX, from.getX());
    }

    @ModifyVariable(method = "addDistanceRightClickOption", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static double alpha_omega$nearestZ(double posZ, @Local(argsOnly = true) Entity from) {
        return from == null ? posZ : Wrap.of(from.level()).nearest(posZ, from.getZ());
    }
}
