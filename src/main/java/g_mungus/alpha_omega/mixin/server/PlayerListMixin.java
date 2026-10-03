package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** R5: positional broadcasts (sounds, level events, block events) reach players near any image of the source. */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {

    @Unique
    private static final String BROADCAST =
        "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V";

    @WrapOperation(method = BROADCAST, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
    private double alpha_omega$nearestX(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) double x) {
        return Wrap.nearest(original.call(player), x);
    }

    @WrapOperation(method = BROADCAST, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
    private double alpha_omega$nearestZ(ServerPlayer player, Operation<Double> original, @Local(argsOnly = true, ordinal = 2) double z) {
        return Wrap.nearest(original.call(player), z);
    }
}
