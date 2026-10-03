package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.noise.PeriodicNoiseSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Surface noises, each made periodic at the horizontal scale {@code SurfaceSystem} samples it at. */
@Mixin(SurfaceSystem.class)
abstract class SurfaceSystemMixin {

    @Shadow @Final @Mutable private NormalNoise clayBandsOffsetNoise;
    @Shadow @Final @Mutable private NormalNoise surfaceNoise;
    @Shadow @Final @Mutable private NormalNoise surfaceSecondaryNoise;
    @Shadow @Final @Mutable private NormalNoise badlandsPillarNoise;
    @Shadow @Final @Mutable private NormalNoise badlandsPillarRoofNoise;
    @Shadow @Final @Mutable private NormalNoise badlandsSurfaceNoise;
    @Shadow @Final @Mutable private NormalNoise icebergPillarNoise;
    @Shadow @Final @Mutable private NormalNoise icebergPillarRoofNoise;
    @Shadow @Final @Mutable private NormalNoise icebergSurfaceNoise;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$makePeriodic(CallbackInfo ci, @Local(argsOnly = true) RandomState randomState) {
        PeriodicNoiseSource source = (PeriodicNoiseSource) (Object) randomState;
        this.clayBandsOffsetNoise = alpha_omega$periodic(source, Noises.CLAY_BANDS_OFFSET, this.clayBandsOffsetNoise, 1.0);
        this.surfaceNoise = alpha_omega$periodic(source, Noises.SURFACE, this.surfaceNoise, 1.0);
        this.surfaceSecondaryNoise = alpha_omega$periodic(source, Noises.SURFACE_SECONDARY, this.surfaceSecondaryNoise, 1.0);
        this.badlandsPillarNoise = alpha_omega$periodic(source, Noises.BADLANDS_PILLAR, this.badlandsPillarNoise, 0.2);
        this.badlandsPillarRoofNoise = alpha_omega$periodic(source, Noises.BADLANDS_PILLAR_ROOF, this.badlandsPillarRoofNoise, 0.75);
        this.badlandsSurfaceNoise = alpha_omega$periodic(source, Noises.BADLANDS_SURFACE, this.badlandsSurfaceNoise, 1.0);
        this.icebergPillarNoise = alpha_omega$periodic(source, Noises.ICEBERG_PILLAR, this.icebergPillarNoise, 1.28);
        this.icebergPillarRoofNoise = alpha_omega$periodic(source, Noises.ICEBERG_PILLAR_ROOF, this.icebergPillarRoofNoise, 1.17);
        this.icebergSurfaceNoise = alpha_omega$periodic(source, Noises.ICEBERG_SURFACE, this.icebergSurfaceNoise, 1.0);
    }

    @Unique
    private static NormalNoise alpha_omega$periodic(PeriodicNoiseSource source, ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, double scale) {
        return source.alpha_omega$periodic(key, noise, Wrap.PERIOD * scale, 0, Wrap.PERIOD * scale);
    }
}
