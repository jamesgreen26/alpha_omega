package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.logging.LogUtils;
import g_mungus.alpha_omega.wrap.noise.CanonicalPositionalRandomFactory;
import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseUser;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes the noise router periodic as soon as its noises are wired, before the climate sampler is derived from
 * it. Each noise-sampling density function configures its noise for the scale it samples at.
 */
@Mixin(RandomState.class)
abstract class RandomStateMixin implements PeriodicNoiseSource {

    @Unique
    private static final Logger ALPHA_OMEGA_LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private PositionalRandomFactory random;

    @Shadow
    @Final
    private HolderGetter<NormalNoise.NoiseParameters> noises;

    /** Copies made because a shared noise was needed at a second period. */
    @Unique
    private final Map<List<Object>, NormalNoise> alpha_omega$copies = new HashMap<>();

    @Unique
    private volatile PositionalRandomFactory alpha_omega$canonicalOreRandom;

    @ModifyExpressionValue(method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter alpha_omega$makeRouterPeriodic(NoiseRouter router) {
        return router.mapAll(function -> {
            if (function instanceof PeriodicNoiseUser user) user.alpha_omega$makePeriodic(this);
            return function;
        });
    }

    /** Ore vein blocks are picked with a per-block random from this factory; seed it with the canonical position. */
    @ModifyReturnValue(method = "oreRandom", at = @At("RETURN"))
    private PositionalRandomFactory alpha_omega$canonicalOreRandom(PositionalRandomFactory factory) {
        PositionalRandomFactory canonical = this.alpha_omega$canonicalOreRandom;
        if (canonical == null) {
            canonical = new CanonicalPositionalRandomFactory(factory);
            this.alpha_omega$canonicalOreRandom = canonical;
        }
        return canonical;
    }

    @Override
    public synchronized NormalNoise alpha_omega$periodic(ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, double px, double py, double pz) {
        if (NoisePeriods.configure(noise, px, py, pz)) return noise;
        return this.alpha_omega$copies.computeIfAbsent(List.of(key, px, py, pz), k -> {
            NormalNoise copy = Noises.instantiate(this.noises, this.random, key);
            NoisePeriods.configure(copy, px, py, pz);
            return copy;
        });
    }

    @Override
    public DensityFunction.NoiseHolder alpha_omega$periodic(DensityFunction.NoiseHolder holder, double px, double py, double pz) {
        NormalNoise noise = holder.noise();
        if (noise == null) return holder;
        Optional<ResourceKey<NormalNoise.NoiseParameters>> key = holder.noiseData().unwrapKey();
        if (key.isEmpty()) {
            if (!NoisePeriods.configure(noise, px, py, pz)) {
                ALPHA_OMEGA_LOGGER.warn("Inline noise sampled at conflicting periods {}; it will not tile", Arrays.toString(new double[] {px, py, pz}));
            }
            return holder;
        }
        NormalNoise periodic = this.alpha_omega$periodic(key.get(), noise, px, py, pz);
        return periodic == noise ? holder : new DensityFunction.NoiseHolder(holder.noiseData(), periodic);
    }
}
