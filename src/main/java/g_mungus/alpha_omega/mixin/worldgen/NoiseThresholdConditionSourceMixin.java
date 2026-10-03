package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Surface rule {@code noise_threshold} conditions sample their noise at block coordinates. */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$NoiseThresholdConditionSource")
abstract class NoiseThresholdConditionSourceMixin {

    @WrapOperation(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$Condition;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateNoise(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/synth/NormalNoise;"))
    private NormalNoise alpha_omega$periodic(RandomState randomState, ResourceKey<NormalNoise.NoiseParameters> key, Operation<NormalNoise> original) {
        NormalNoise noise = original.call(randomState, key);
        return ((PeriodicNoiseSource) (Object) randomState).alpha_omega$periodic(key, noise, Wrap.PERIOD, 0, Wrap.PERIOD);
    }
}
