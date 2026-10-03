package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.NoisePeriods;
import net.minecraft.world.level.levelgen.feature.stateproviders.DualNoiseProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DualNoiseProvider.class)
abstract class DualNoiseProviderMixin {

    @Shadow
    @Final
    private float slowScale;

    @Shadow
    @Final
    private NormalNoise slowNoise;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$makePeriodic(CallbackInfo ci) {
        NoisePeriods.configure(this.slowNoise, Wrap.PERIOD * this.slowScale, 0, Wrap.PERIOD * this.slowScale);
    }
}
