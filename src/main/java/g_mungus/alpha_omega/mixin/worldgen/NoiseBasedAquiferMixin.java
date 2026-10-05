package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.noise.GeometryHolder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Aquifers work on coarse cells addressed by index, not block: a centre is placed at random in each 16 × 12 × 16 cell,
 * seeded by its index, and the fluid level spread and lava are sampled at 16- and 64-block cell indices. In an orbifold,
 * each cell takes its canonical cell's values. Exact across the east–west seam (a translation by whole cells). Across a
 * fold, a cell maps to a cell, but the centre's random offset in it is not turned, so aquifer centres there do not match:
 * a residue ({@code TerrainGameTests}).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
abstract class NoiseBasedAquiferMixin {

    @Shadow
    @Final
    private NoiseChunk noiseChunk;

    @WrapOperation(method = "computeSubstance", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private RandomSource alpha_omega$canonicalCentre(PositionalRandomFactory factory, int cellX, int cellY, int cellZ, Operation<RandomSource> original) {
        OrbifoldGeometry geometry = GeometryHolder.of(this.noiseChunk);
        if (geometry == null) return original.call(factory, cellX, cellY, cellZ);
        OrbifoldGeometry.Cell cell = geometry.canon(cellX * 16 + 4, cellZ * 16 + 4);
        return original.call(factory, cell.x() >> 4, cellY, cell.z() >> 4);
    }

    @ModifyArg(method = "computeRandomizedFluidSurfaceLevel", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$canonicalSpreadX(int cellX, int cellY, int cellZ) {
        return this.alpha_omega$canonicalCell(cellX, cellZ, 4, true);
    }

    @ModifyArg(method = "computeRandomizedFluidSurfaceLevel", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$canonicalSpreadZ(int cellX, int cellY, int cellZ) {
        return this.alpha_omega$canonicalCell(cellX, cellZ, 4, false);
    }

    @ModifyArg(method = "computeFluidType", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$canonicalLavaX(int cellX, int cellY, int cellZ) {
        return this.alpha_omega$canonicalCell(cellX, cellZ, 6, true);
    }

    @ModifyArg(method = "computeFluidType", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/DensityFunction$SinglePointContext;<init>(III)V"))
    private int alpha_omega$canonicalLavaZ(int cellX, int cellY, int cellZ) {
        return this.alpha_omega$canonicalCell(cellX, cellZ, 6, false);
    }

    /** The canonical index of a {@code 2^shift}-block cell: every {@code Γ} generator maps such cells to whole cells. */
    @Unique
    private int alpha_omega$canonicalCell(int cellX, int cellZ, int shift, boolean x) {
        OrbifoldGeometry geometry = GeometryHolder.of(this.noiseChunk);
        if (geometry == null) return x ? cellX : cellZ;
        int half = 1 << (shift - 1);
        OrbifoldGeometry.Cell cell = geometry.canon((cellX << shift) + half, (cellZ << shift) + half);
        return (x ? cell.x() : cell.z()) >> shift;
    }
}
