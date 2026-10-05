package g_mungus.alpha_omega.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import g_mungus.alpha_omega.compat.sable.SubLevelTransfers;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Players see sub-levels on neighbouring faces near their virtual positions there (design §6.1), as they see entities. */
@Mixin(value = SubLevelTrackingSystem.class, remap = false)
abstract class SubLevelTrackingSystemMixin {

    @ModifyReturnValue(method = "shouldLoad", at = @At("RETURN"))
    private boolean alpha_omega$fromVirtualPositions(boolean near, @Local(argsOnly = true) Player player, @Local(argsOnly = true) Vector3dc position) {
        return near || SubLevelTransfers.nearVirtual(player, position);
    }
}
