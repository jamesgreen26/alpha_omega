package g_mungus.alpha_omega.mixin.server.entity;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * R5: reach checks measure to the image of the target nearest the player. Container menus re-check reach every
 * tick against their block entity's position, which is canonical (R1), while the player is in a lifted frame;
 * without this every chest, furnace and hopper menu closes as soon as it opens.
 */
@Mixin(Player.class)
abstract class PlayerMixin {

    @ModifyVariable(method = "canInteractWithBlock", at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$nearestBlock(BlockPos pos) {
        Player player = (Player) (Object) this;
        return Wrap.of(player.level()).nearest(pos, player.getEyePosition());
    }

    @ModifyVariable(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("HEAD"), argsOnly = true)
    private AABB alpha_omega$nearestBox(AABB box) {
        Player player = (Player) (Object) this;
        Vec3 center = box.getCenter();
        Vec3 nearest = Wrap.of(player.level()).nearest(center, player.getEyePosition());
        return nearest == center ? box : box.move(nearest.x - center.x, 0, nearest.z - center.z);
    }
}
