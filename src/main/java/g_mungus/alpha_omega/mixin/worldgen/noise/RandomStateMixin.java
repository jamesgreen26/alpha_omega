package g_mungus.alpha_omega.mixin.worldgen.noise;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.noise.CanonicalPositionalRandomFactory;
import g_mungus.alpha_omega.worldgen.noise.CenteredClimate;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseSource;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoiseUser;
import g_mungus.alpha_omega.worldgen.noise.InvariantNoises;
import g_mungus.alpha_omega.worldgen.noise.InvariantNormalNoise;
import g_mungus.alpha_omega.worldgen.noise.InvariantOctaves;
import g_mungus.alpha_omega.worldgen.noise.NoiseSymmetry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An orbifold's noise is made invariant as soon as its router is wired, before the climate sampler is derived from it:
 * each noise-sampling density function makes its noise invariant for the scale it samples at. Positional randoms keyed
 * by block (ore veins, surface gradients) hash the canonical cell.
 */
@Mixin(RandomState.class)
abstract class RandomStateMixin implements InvariantNoiseSource {

    @Shadow
    @Final
    private PositionalRandomFactory random;
    @Shadow
    @Final
    private HolderGetter<NormalNoise.NoiseParameters> noises;
    @Shadow
    @Final
    private Climate.Sampler sampler;

    /** The orbifold this noise generates (captured while it is built), or null. */
    @Unique
    private final OrbifoldGeometry alpha_omega$geometry = InvariantNoises.creating();

    /** Whether the constructor is still running: only then are shared noises changed in place. */
    @Unique
    private boolean alpha_omega$building = true;

    /** Copies made because a shared noise was needed for a second symmetry. */
    @Unique
    private final Map<List<Object>, NormalNoise> alpha_omega$copies = new ConcurrentHashMap<>();

    @Unique
    private final Map<PositionalRandomFactory, PositionalRandomFactory> alpha_omega$canonicalRandoms = new ConcurrentHashMap<>();

    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter alpha_omega$makeRouterInvariant(NoiseRouter router) {
        if (this.alpha_omega$geometry == null) return router;
        NoiseRouter invariant = router.mapAll(function -> {
            if (function instanceof InvariantNoiseUser user) user.alpha_omega$makeInvariant(this);
            return function;
        });
        AlphaOmegaMod.LOGGER.debug("Orbifold noise octaves:\n{}", InvariantOctaves.summary());
        return invariant;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$built(CallbackInfo ci) {
        this.alpha_omega$building = false;
        if (this.alpha_omega$geometry != null) ((CenteredClimate) (Object) this.sampler).alpha_omega$sampleCentres();
    }

    @ModifyReturnValue(method = "oreRandom", at = @At("RETURN"))
    private PositionalRandomFactory alpha_omega$canonicalOreRandom(PositionalRandomFactory factory) {
        return this.alpha_omega$canonical(factory);
    }

    /** Surface rules (the bedrock and deepslate gradients) and generation regions seed by block. */
    @ModifyReturnValue(method = "getOrCreateRandomFactory", at = @At("RETURN"))
    private PositionalRandomFactory alpha_omega$canonicalRandomFactory(PositionalRandomFactory factory) {
        return this.alpha_omega$canonical(factory);
    }

    @Unique
    private PositionalRandomFactory alpha_omega$canonical(PositionalRandomFactory factory) {
        OrbifoldGeometry geometry = this.alpha_omega$geometry;
        if (geometry == null) return factory;
        return this.alpha_omega$canonicalRandoms.computeIfAbsent(factory, f -> new CanonicalPositionalRandomFactory(f, geometry));
    }

    @Override
    public OrbifoldGeometry alpha_omega$geometry() {
        return this.alpha_omega$geometry;
    }

    @Override
    public NormalNoise alpha_omega$invariant(ResourceKey<NormalNoise.NoiseParameters> key, NormalNoise noise, NoiseSymmetry symmetry) {
        OrbifoldGeometry geometry = this.alpha_omega$geometry;
        if (geometry == null || InvariantNoises.invariantFor(noise, symmetry)) return noise;
        if (this.alpha_omega$building && InvariantNoises.configure(noise, geometry, symmetry)) return noise;
        return this.alpha_omega$copies.computeIfAbsent(List.of(key, symmetry), k -> {
            NormalNoise copy = Noises.instantiate(this.noises, this.random, key);
            InvariantNoises.configure(copy, geometry, symmetry);
            return copy;
        });
    }

    @Override
    public DensityFunction.NoiseHolder alpha_omega$invariant(DensityFunction.NoiseHolder holder, NoiseSymmetry symmetry) {
        NormalNoise noise = holder.noise();
        OrbifoldGeometry geometry = this.alpha_omega$geometry;
        if (noise == null || geometry == null) return holder;
        Optional<ResourceKey<NormalNoise.NoiseParameters>> key = holder.noiseData().unwrapKey();
        if (key.isEmpty()) {
            if (!InvariantNoises.invariantFor(noise, symmetry) && !(this.alpha_omega$building && InvariantNoises.configure(noise, geometry, symmetry))) {
                AlphaOmegaMod.LOGGER.warn("An inline noise is sampled two ways ({} and {}); the second use is not invariant",
                    ((InvariantNormalNoise) noise).alpha_omega$symmetry(), symmetry);
            }
            return holder;
        }
        NormalNoise invariant = this.alpha_omega$invariant(key.get(), noise, symmetry);
        return invariant == noise ? holder : new DensityFunction.NoiseHolder(holder.noiseData(), invariant);
    }
}
