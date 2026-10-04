package g_mungus.alpha_omega.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.ActiveSableCompanion;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * R5 for Sable: its sub-level-aware distances measure between the images nearest each other, once both points are
 * projected out of their plots. Sable routes broadcasts, entity distances, explosions, particles and vibrations
 * through these, so this one bridge covers its overwrites of those vanilla methods.
 */
@Mixin(value = ActiveSableCompanion.class, remap = false)
abstract class ActiveSableCompanionMixin {

    @WrapOperation(method = "distanceSquaredWithSubLevels", at = @At(value = "INVOKE", target = "Lorg/joml/Vector3dc;distanceSquared(Lorg/joml/Vector3dc;)D"))
    private double alpha_omega$wrappedDistanceSquared(Vector3dc a, Vector3dc b, Operation<Double> original, @Local(argsOnly = true) Level level) {
        return original.call(a, alpha_omega$nearest(level, b, a));
    }

    @WrapOperation(method = "rectilinearDistanceWithSubLevels", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/ActiveSableCompanion;rectilinearDistance(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)D"))
    private double alpha_omega$wrappedRectilinear(Vector3dc a, Vector3dc b, Operation<Double> original, @Local(argsOnly = true) Level level) {
        return original.call(a, alpha_omega$nearest(level, b, a));
    }

    @Unique
    private static Vector3dc alpha_omega$nearest(Level level, Vector3dc pos, Vector3dc ref) {
        Wrap wrap = Wrap.of(level);
        double x = wrap.nearest(pos.x(), ref.x());
        double z = wrap.nearest(pos.z(), ref.z());
        return x == pos.x() && z == pos.z() ? pos : new Vector3d(x, pos.y(), z);
    }
}
