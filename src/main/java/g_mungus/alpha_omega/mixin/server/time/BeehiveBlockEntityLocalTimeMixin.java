package g_mungus.alpha_omega.mixin.server.time;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.sky.LocalSky;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hives keep their bees in during local night. */
@Mixin(BeehiveBlockEntity.class)
abstract class BeehiveBlockEntityLocalTimeMixin {

    @WrapOperation(method = "releaseOccupant", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isNight()Z"))
    private static boolean alpha_omega$localNight(Level level, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) BlockPos hive) {
        return LocalSky.local(level) ? LocalSky.isNight(level, hive) : original.call(level);
    }
}
