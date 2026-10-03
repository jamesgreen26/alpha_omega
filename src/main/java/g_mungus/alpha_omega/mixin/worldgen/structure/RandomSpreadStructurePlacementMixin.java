package g_mungus.alpha_omega.mixin.worldgen.structure;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import g_mungus.alpha_omega.wrap.Wraps;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Structure grids must tile the world (design doc §12): when the world wraps, spacing is snapped to the nearest
 * divisor of the structure grid period (separation scaled with it), and grid regions are seeded by their
 * canonical index so every image of a region proposes the same chunk. Unwrapped worlds keep vanilla spacing.
 * Placements are shared registry objects built before a world's wrapping is known, so this is evaluated on use.
 */
@Mixin(RandomSpreadStructurePlacement.class)
abstract class RandomSpreadStructurePlacementMixin {

    @Shadow
    @Final
    private int spacing;

    @ModifyExpressionValue(method = "getPotentialStructureChunk",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;spacing:I"))
    private int alpha_omega$tiledSpacing(int spacing) {
        return alpha_omega$snap(spacing);
    }

    @ModifyExpressionValue(method = "getPotentialStructureChunk",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;separation:I"))
    private int alpha_omega$tiledSeparation(int separation) {
        int spacing = this.spacing;
        int snapped = alpha_omega$snap(spacing);
        if (snapped == spacing) return separation;
        return Math.max(0, Math.min((int) Math.round(separation * (double) snapped / spacing), snapped - 1));
    }

    @ModifyReturnValue(method = "spacing", at = @At("RETURN"))
    private int alpha_omega$reportTiledSpacing(int spacing) {
        return alpha_omega$snap(spacing);
    }

    @ModifyArg(method = "getPotentialStructureChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"), index = 1)
    private int alpha_omega$canonRegionX(int regionX) {
        return alpha_omega$canonRegion(regionX, alpha_omega$snap(this.spacing));
    }

    @ModifyArg(method = "getPotentialStructureChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"), index = 2)
    private int alpha_omega$canonRegionZ(int regionZ) {
        return alpha_omega$canonRegion(regionZ, alpha_omega$snap(this.spacing));
    }

    /** The divisor of the grid period nearest {@code spacing} (the larger on a tie); unchanged when unwrapped. */
    @Unique
    private static int alpha_omega$snap(int spacing) {
        int grid = Wraps.structureGridPeriod();
        if (grid == 0 || grid % spacing == 0) return spacing;
        int best = 1;
        for (int d = 1; d <= grid; d++) {
            if (grid % d == 0 && Math.abs(d - spacing) <= Math.abs(best - spacing)) best = d;
        }
        return best;
    }

    @Unique
    private static int alpha_omega$canonRegion(int region, int spacing) {
        int chunks = Wraps.overworld().chunkPeriod;
        return chunks == 0 ? region : Math.floorMod(region, chunks / spacing);
    }
}
