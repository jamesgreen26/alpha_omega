package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Respawn points are block-side state (§10.3): stored canonically; respawning lifts the player into a frame. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @ModifyVariable(method = "setRespawnPosition", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonRespawn(BlockPos pos, @Local(argsOnly = true) ResourceKey<Level> dimension) {
        return pos == null ? null : Wrap.of(dimension).canon(pos);
    }
}
