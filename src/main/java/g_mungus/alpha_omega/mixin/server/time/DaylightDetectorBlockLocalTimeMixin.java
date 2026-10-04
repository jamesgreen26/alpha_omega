package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Daylight detectors measure the local sun: its darkening and (through the equivalent time of day) its height. */
@Mixin(DaylightDetectorBlock.class)
abstract class DaylightDetectorBlockLocalTimeMixin {

    @WrapOperation(method = "updateSignalStrength", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSkyDarken()I"))
    private static int alpha_omega$localDarken(Level level, Operation<Integer> original, @Local(argsOnly = true) BlockPos pos) {
        return LocalSky.local(level) ? LocalSky.skyDarken(level, pos) : original.call(level);
    }

    @WrapOperation(method = "updateSignalStrength", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSunAngle(F)F"))
    private static float alpha_omega$localSunAngle(Level level, float partialTick, Operation<Float> original, @Local(argsOnly = true) BlockPos pos) {
        if (!LocalSky.local(level)) return original.call(level, partialTick);
        return (float) (2.0 * Math.PI * LocalSky.sample(level, pos.getX() + 0.5, pos.getZ() + 0.5).equivalentTimeOfDay());
    }
}
