package g_mungus.alpha_omega.mixin.polish.client;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.item.Compasses;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Compasses (spawn, lodestone, recovery) aim at the image of their target nearest the holder ({@link Compasses}), and
 * a holder standing on an image of the target spins, as on the target itself. Priority above 1000 so it still injects
 * if another mod overwrites these methods.
 */
@Mixin(value = CompassItemPropertyFunction.class, priority = 1100)
abstract class CompassItemPropertyFunctionMixin {

    @ModifyVariable(method = "getAngleFromEntityToPos", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$nearestTarget(BlockPos target, @Local(argsOnly = true) Entity holder) {
        return Compasses.aim(holder.level(), target, holder.position());
    }

    @ModifyVariable(method = "isValidCompassTargetPos", at = @At("HEAD"), argsOnly = true)
    private GlobalPos alpha_omega$nearestTargetForCheck(GlobalPos target, @Local(argsOnly = true) Entity holder) {
        return Compasses.aim(holder, target);
    }
}
