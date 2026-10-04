package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Turtle eggs hatch fastest just before local dawn. */
@Mixin(TurtleEggBlock.class)
abstract class TurtleEggBlockLocalTimeMixin {

    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/TurtleEggBlock;shouldUpdateHatchLevel(Lnet/minecraft/world/level/Level;)Z"))
    private boolean alpha_omega$localDawn(TurtleEggBlock block, Level level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        if (!LocalSky.local(level)) return original.call(block, level);
        double timeOfDay = LocalSky.sample(level, pos.getX() + 0.5, pos.getZ() + 0.5).timeOfDay();
        return timeOfDay < 0.69 && timeOfDay > 0.65 || level.random.nextInt(500) == 0;
    }
}
