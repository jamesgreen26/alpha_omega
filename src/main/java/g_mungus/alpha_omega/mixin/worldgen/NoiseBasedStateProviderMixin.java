package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseBasedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Noise-driven block choice (e.g. flower forest flowers), sampled at {@code pos * scale}. Own instance. */
@Mixin(NoiseBasedStateProvider.class)
abstract class NoiseBasedStateProviderMixin {

    @Shadow
    @Final
    protected float scale;

    @Shadow
    @Final
    protected NormalNoise noise;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$makePeriodic(CallbackInfo ci) {
        NoisePeriods.configure(this.noise, Wrap.PERIOD * this.scale, 0, Wrap.PERIOD * this.scale);
    }
}
