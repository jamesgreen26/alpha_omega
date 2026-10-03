package g_mungus.alpha_omega.mixin.compat.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.hud.minimap.waypoint.DeathpointHandler;

/** Deathpoints are saved at canonical coordinates. */
@Mixin(DeathpointHandler.class)
abstract class DeathpointHandlerMixin {

    private static final String CREATE = "createDeathpoint(Lnet/minecraft/world/entity/player/Player;Lxaero/hud/minimap/world/MinimapWorld;Z)V";

    @WrapOperation(method = CREATE, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getX()D"))
    private double alpha_omega$canonX(Player player, Operation<Double> original) {
        return Wrap.of(player.level()).canon(original.call(player));
    }

    @WrapOperation(method = CREATE, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getZ()D"))
    private double alpha_omega$canonZ(Player player, Operation<Double> original) {
        return Wrap.of(player.level()).canon(original.call(player));
    }
}
