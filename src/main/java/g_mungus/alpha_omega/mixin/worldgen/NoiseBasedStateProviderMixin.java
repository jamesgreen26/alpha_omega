package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wraps;
import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseBasedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Noise-driven block choice (e.g. flower forest flowers), sampled at {@code pos * scale}. Providers are built when
 * data packs load, before a world's wrapping is known, so the noise is (re)configured on use.
 */
@Mixin(NoiseBasedStateProvider.class)
abstract class NoiseBasedStateProviderMixin {

    @Shadow
    @Final
    protected float scale;

    @Shadow
    @Final
    protected NormalNoise noise;

    @Unique
    private volatile int alpha_omega$configuredPeriod;

    @Inject(method = "getNoiseValue", at = @At("HEAD"))
    private void alpha_omega$makePeriodic(BlockPos pos, double scale, CallbackInfoReturnable<Double> cir) {
        int period = Wraps.overworld().period;
        if (period == this.alpha_omega$configuredPeriod) return;
        NoisePeriods.reconfigure(this.noise, period * this.scale, 0, period * this.scale);
        this.alpha_omega$configuredPeriod = period;
    }
}
