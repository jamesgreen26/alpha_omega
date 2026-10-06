package g_mungus.alpha_omega.mixin.nether;

import g_mungus.alpha_omega.nether.NetherPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Portals between orbifold levels link from the portal's canonical copy ({@code nether.NetherPortals}). */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$orbifoldDestination(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<DimensionTransition> cir) {
        if (NetherPortals.handles(level)) cir.setReturnValue(NetherPortals.destination(level, entity, pos));
    }
}
