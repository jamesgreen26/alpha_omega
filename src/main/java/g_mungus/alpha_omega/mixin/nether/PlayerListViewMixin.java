package g_mungus.alpha_omega.mixin.nether;

import g_mungus.alpha_omega.nether.NetherView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After the server's view distance is sent to everyone, players in a level with a lower limit are told theirs ({@code nether.NetherView}). */
@Mixin(PlayerList.class)
abstract class PlayerListViewMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "setViewDistance", at = @At("TAIL"))
    private void alpha_omega$limitedLevels(int viewDistance, CallbackInfo ci) {
        NetherView.syncLimited(this.server);
    }
}
