package g_mungus.alpha_omega.mixin.worldgen;

import g_mungus.alpha_omega.wrap.Wrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Aquifers work on coarse cells, addressed by index rather than block position: centers are placed randomly
 * within 16x12x16 cells seeded by cell index, and fluid level spread and lava are sampled at 16- and 64-block
 * cell indices. A chunk next to the seam sees cells on the far side through unrolled indices, so wrap them
 * (16 and 64 both divide W).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
abstract class NoiseBasedAquiferMixin {

    @ModifyArg(method = "computeSubstance",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"),
        index = 0)
    private int alpha_omega$canonCellX(int cellX) {
        return Wrap.canonChunk(cellX);
    }

    @ModifyArg(method = "computeSubstance",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"),
        index = 2)
    private int alpha_omega$canonCellZ(int cellZ) {
        return Wrap.canonChunk(cellZ);
    }

    @ModifyArg(method = "computeRandomizedFluidSurfaceLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"), index = 0)
    private int alpha_omega$canonSpreadCellX(int cellX) {
        return Math.floorMod(cellX, Wrap.PERIOD / 16);
    }

    @ModifyArg(method = "computeRandomizedFluidSurfaceLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"), index = 2)
    private int alpha_omega$canonSpreadCellZ(int cellZ) {
        return Math.floorMod(cellZ, Wrap.PERIOD / 16);
    }

    @ModifyArg(method = "computeFluidType",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"), index = 0)
    private int alpha_omega$canonLavaCellX(int cellX) {
        return Math.floorMod(cellX, Wrap.PERIOD / 64);
    }

    @ModifyArg(method = "computeFluidType",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"), index = 2)
    private int alpha_omega$canonLavaCellZ(int cellZ) {
        return Math.floorMod(cellZ, Wrap.PERIOD / 64);
    }
}
