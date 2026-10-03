package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wraps;
import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The slow noise of a dual-noise provider, configured on use like {@link NoiseBasedStateProviderMixin}. */
@Mixin(DualNoiseProvider.class)
abstract class DualNoiseProviderMixin {

    @Shadow
    @Final
    private float slowScale;

    @Shadow
    @Final
    private NormalNoise slowNoise;

    @Unique
    private volatile int alpha_omega$configuredPeriod;

    @Inject(method = "getSlowNoiseValue", at = @At("HEAD"))
    private void alpha_omega$makePeriodic(BlockPos pos, CallbackInfoReturnable<Double> cir) {
        int period = Wraps.overworld().period;
        if (period == this.alpha_omega$configuredPeriod) return;
        NoisePeriods.reconfigure(this.slowNoise, period * this.slowScale, 0, period * this.slowScale);
        this.alpha_omega$configuredPeriod = period;
    }
}
