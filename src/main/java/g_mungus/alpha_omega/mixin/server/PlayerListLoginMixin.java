package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import g_mungus.alpha_omega.island.EntityFrames;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A joining player starts a fresh client frame, so their island need not keep the lap they left in: they and their
 * vehicle load at lap 0, where canonical and lifted positions coincide. If their chunk's island is already loaded
 * in another lap, they are brought into it as they enter the level.
 */
@Mixin(PlayerList.class)
abstract class PlayerListLoginMixin {

    @WrapMethod(method = "placeNewPlayer")
    private void alpha_omega$loadAtLapZero(Connection connection, ServerPlayer player, CommonListenerCookie cookie, Operation<Void> original) {
        EntityFrames.beginLogin();
        try {
            original.call(connection, player, cookie);
        } finally {
            EntityFrames.endLogin();
        }
    }
}
