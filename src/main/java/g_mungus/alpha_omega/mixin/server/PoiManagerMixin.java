package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R1/R2: POI records hold canonical positions. Reads that return positions are bridged in a later phase. */
@Mixin(PoiManager.class)
abstract class PoiManagerMixin {

    @ModifyVariable(method = {"add", "remove", "release", "exists", "getType", "getFreeTickets"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonPos(BlockPos pos) {
        return Wrap.canon(pos);
    }
}
