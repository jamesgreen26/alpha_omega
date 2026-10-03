package g_mungus.alpha_omega.mixin.worldgen.structure;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Structure grids must tile the world (design doc §12): spacing is snapped to the nearest divisor of {@code N}
 * (separation scaled with it), and grid regions are seeded by their canonical index, so every image of a region
 * proposes the same structure chunk.
 */
@Mixin(RandomSpreadStructurePlacement.class)
abstract class RandomSpreadStructurePlacementMixin {

    @Shadow
    @Final
    @Mutable
    private int spacing;

    @Shadow
    @Final
    @Mutable
    private int separation;

    @Inject(method = "<init>(Lnet/minecraft/core/Vec3i;Lnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement$FrequencyReductionMethod;FILjava/util/Optional;IILnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadType;)V",
        at = @At("RETURN"))
    private void alpha_omega$tileGrid(CallbackInfo ci) {
        int spacing = Wrap.nearestChunkPeriodDivisor(this.spacing);
        if (spacing == this.spacing) return;
        int separation = (int) Math.round(this.separation * (double) spacing / this.spacing);
        this.separation = Math.max(0, Math.min(separation, spacing - 1));
        this.spacing = spacing;
    }

    @ModifyArg(method = "getPotentialStructureChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"), index = 1)
    private int alpha_omega$canonRegionX(int regionX) {
        return Math.floorMod(regionX, Wrap.CHUNK_PERIOD / this.spacing);
    }

    @ModifyArg(method = "getPotentialStructureChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"), index = 2)
    private int alpha_omega$canonRegionZ(int regionZ) {
        return Math.floorMod(regionZ, Wrap.CHUNK_PERIOD / this.spacing);
    }
}
