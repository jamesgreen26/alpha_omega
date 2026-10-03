package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.frame.Frames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Block-based game event listeners (sculk sensors, shriekers, catalysts) listen from their lifted position. */
@Mixin(BlockPositionSource.class)
abstract class BlockPositionSourceMixin {

    @ModifyArg(method = "getPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3i alpha_omega$lift(Vec3i pos, @Local(argsOnly = true) Level level) {
        return level instanceof ServerLevel server && pos instanceof BlockPos block ? Frames.lift(server, block) : pos;
    }
}
