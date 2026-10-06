package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.PlayerBridge;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sounds, level events and block events sent round a point near an edge also reach players near its images ({@link PlayerBridge#broadcast}). */
@Mixin(PlayerList.class)
abstract class PlayerListBridgeMixin {

    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void alpha_omega$toImages(@Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet,
        CallbackInfo ci) {
        if (PlayerBridge.broadcast(this.server, this.players, except, x, y, z, radius, dimension, packet)) ci.cancel();
    }
}
