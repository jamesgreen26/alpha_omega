package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Surface rule noise conditions sample {@code noise(x, 0, z)}: invariant at scale 1 (a copy if the shared noise is not). */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$NoiseThresholdConditionSource")
abstract class NoiseThresholdConditionMixin {

    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/RandomState;getOrCreateNoise(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/synth/NormalNoise;"))
    private NormalNoise alpha_omega$invariant(RandomState randomState, ResourceKey<NormalNoise.NoiseParameters> key, Operation<NormalNoise> original) {
        NormalNoise noise = original.call(randomState, key);
        return ((InvariantNoiseSource) (Object) randomState).alpha_omega$invariant(key, noise, NoiseSymmetry.even(1.0));
    }
}
