package g_mungus.alpha_omega.mixin.bridges;

import g_mungus.alpha_omega.bridge.PlaceBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Reach checks measure to the block's image nearest the player ({@link PlaceBridge#forInteraction}). */
@Mixin(Player.class)
abstract class PlayerInteractionBridgeMixin {

    @ModifyVariable(method = "canInteractWithBlock", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$nearestImage(BlockPos pos) {
        return PlaceBridge.forInteraction((Player) (Object) this, pos);
    }
}
