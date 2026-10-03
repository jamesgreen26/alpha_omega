package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseUser;
import g_mungus.alpha_omega.wrap.noise.RarityNoises;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Samples {@code noise(x / r, y / r, z / r)} where the rarity {@code r} is picked per position from a small
 * discrete set. One noise cannot have every period {@code W / r} at once, so each rarity gets its own copy.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler")
abstract class WeirdScaledSamplerMixin implements PeriodicNoiseUser {

    @Shadow
    @Final
    private DensityFunction.NoiseHolder noise;

    @Unique
    private RarityNoises alpha_omega$rarityNoises;

    @Override
    public void alpha_omega$makePeriodic(PeriodicNoiseSource source) {
        this.alpha_omega$rarityNoises = new RarityNoises(source, this.noise);
    }

    /** {@code mapAll} rebuilds the record; carry the periodic noises over to the copy. */
    @ModifyArg(method = "mapAll",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;apply(Lnet/minecraft/world/level/levelgen/DensityFunction;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction alpha_omega$propagate(DensityFunction copy) {
        if (this.alpha_omega$rarityNoises != null) ((WeirdScaledSamplerMixin) (Object) copy).alpha_omega$rarityNoises = this.alpha_omega$rarityNoises;
        return copy;
    }

    @WrapOperation(method = "transform",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$NoiseHolder;getValue(DDD)D"))
    private double alpha_omega$sampleForRarity(DensityFunction.NoiseHolder holder, double x, double y, double z,
                                              Operation<Double> original, @Local(ordinal = 1) double rarity) {
        RarityNoises noises = this.alpha_omega$rarityNoises;
        return original.call(noises == null ? holder : noises.forRarity(rarity), x, y, z);
    }
}
