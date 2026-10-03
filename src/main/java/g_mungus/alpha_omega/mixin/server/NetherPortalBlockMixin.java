package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Portal linking (§11): the destination is scaled from the canonical source position, so every lap of a portal
 * leads to the same place. Arrival lifts the traveller into the destination's island frame.
 */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {

    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double alpha_omega$canonicalX(Entity entity, Operation<Double> original) {
        return Wrap.canon(original.call(entity));
    }

    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double alpha_omega$canonicalZ(Entity entity, Operation<Double> original) {
        return Wrap.canon(original.call(entity));
    }
}
