package g_mungus.alpha_omega.mixin.compat.sable;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Portal linking (§11) under Sable, which redirects the destination to be scaled from the traveller's position read
 * afresh: that read is made canonical, as {@code server.NetherPortalBlockMixin} does for vanilla's.
 */
@Mixin(value = NetherPortalBlock.class, priority = 1500)
abstract class NetherPortalBlockMixin {

    @TargetHandler(mixin = "dev.ryanhcode.sable.mixin.portal.NetherPortalBlockMixin", name = "sable$getPortalDestination")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double alpha_omega$canonicalX(Entity entity, Operation<Double> original) {
        return Wrap.of(entity.level()).canon(original.call(entity));
    }

    @TargetHandler(mixin = "dev.ryanhcode.sable.mixin.portal.NetherPortalBlockMixin", name = "sable$getPortalDestination")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double alpha_omega$canonicalZ(Entity entity, Operation<Double> original) {
        return Wrap.of(entity.level()).canon(original.call(entity));
    }
}
