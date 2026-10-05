package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
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

/** Surface noises (soil depth, clay bands, badlands and iceberg pillars), each invariant at the scale it is sampled at. */
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
    private void alpha_omega$makeInvariant(CallbackInfo ci, @Local(argsOnly = true) RandomState randomState) {
        InvariantNoiseSource source = (InvariantNoiseSource) (Object) randomState;
        if (source.alpha_omega$geometry() == null) return;
        this.clayBandsOffsetNoise = alpha_omega$invariant(source, Noises.CLAY_BANDS_OFFSET, this.clayBandsOffsetNoise, 1.0);
        this.surfaceNoise = alpha_omega$invariant(source, Noises.SURFACE, this.surfaceNoise, 1.0);
        this.surfaceSecondaryNoise = alpha_omega$invariant(source, Noises.SURFACE_SECONDARY, this.surfaceSecondaryNoise, 1.0);
        this.badlandsPillarNoise = alpha_omega$invariant(source, Noises.BADLANDS_PILLAR, this.badlandsPillarNoise, 0.2);
        this.badlandsPillarRoofNoise = alpha_omega$invariant(source, Noises.BADLANDS_PILLAR_ROOF, this.badlandsPillarRoofNoise, 0.75);
        this.badlandsSurfaceNoise = alpha_omega$invariant(source, Noises.BADLANDS_SURFACE, this.badlandsSurfaceNoise, 1.0);
        this.icebergPillarNoise = alpha_omega$invariant(source, Noises.ICEBERG_PILLAR, this.icebergPillarNoise, 1.28);
        this.icebergPillarRoofNoise = alpha_omega$invariant(source, Noises.ICEBERG_PILLAR_ROOF, this.icebergPillarRoofNoise, 1.17);
        this.icebergSurfaceNoise = alpha_omega$invariant(source, Noises.ICEBERG_SURFACE, this.icebergSurfaceNoise, 1.0);
    }

    @Unique
    private static NormalNoise alpha_omega$invariant(InvariantNoiseSource source, ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, double scale) {
        return source.alpha_omega$invariant(key, noise, NoiseSymmetry.even(scale));
    }
}
