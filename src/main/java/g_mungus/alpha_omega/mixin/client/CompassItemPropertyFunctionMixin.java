package g_mungus.alpha_omega.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Compasses (spawn, lodestone, recovery) point at the image of their target nearest the holder (§9.4). */
@Mixin(CompassItemPropertyFunction.class)
abstract class CompassItemPropertyFunctionMixin {

    @ModifyVariable(method = "getAngleFromEntityToPos", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$nearestTarget(BlockPos target, @Local(argsOnly = true) Entity holder) {
        return Wrap.of(holder.level()).nearest(target, holder.position());
    }
}
