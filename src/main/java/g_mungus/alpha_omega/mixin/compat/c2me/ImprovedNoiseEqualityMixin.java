package g_mungus.alpha_omega.mixin.compat.c2me;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.noise.PeriodicLattice;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C2ME's density function compiler deduplicates noises by value, with an {@code equals} it adds to every noise
 * class; they all come down to comparing octaves here. A periodic copy of a noise has the same seed as the
 * original, so without its periods in the comparison the compiler would sample both through one of them, at the
 * wrong period for the other. Applied after C2ME's mixin (higher priority), which merges the method; absent when its
 * compiler is disabled.
 */
@Mixin(value = ImprovedNoise.class, priority = 1500)
abstract class ImprovedNoiseEqualityMixin {

    @ModifyReturnValue(method = "equals(Ljava/lang/Object;)Z", at = @At("RETURN"), require = 0)
    private boolean alpha_omega$samePeriods(boolean equal, @Local(argsOnly = true) Object other) {
        return equal && (other == this || ((PeriodicLattice) this).alpha_omega$samePeriods((PeriodicLattice) other));
    }
}
