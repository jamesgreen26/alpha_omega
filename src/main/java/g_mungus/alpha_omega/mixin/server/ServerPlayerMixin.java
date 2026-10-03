package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Respawn points are block-side state (§10.3): stored canonically; respawning lifts the player into a frame. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @ModifyVariable(method = "setRespawnPosition", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonRespawn(BlockPos pos) {
        return pos == null ? null : Wrap.canon(pos);
    }
}
