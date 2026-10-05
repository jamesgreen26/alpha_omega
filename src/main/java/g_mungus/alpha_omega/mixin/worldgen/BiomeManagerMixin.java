package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.noise.GeometryHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Biome boundaries are jittered by a hash of quart coordinates; in an orbifold, each quart hashes its canonical quart.
 * Exact across the east–west seam. Across a fold the quart maps to a quart, but its jitter is not turned and vanilla's
 * zoom grid sits half a block off the fold's centre, so biome edges there can differ by a block or two.
 */
@Mixin(BiomeManager.class)
abstract class BiomeManagerMixin implements GeometryHolder {

    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    /** The orbifold, once known: from a level or generation region source, or carried over by {@code withDifferentSource}. */
    @Unique
    private OrbifoldGeometry alpha_omega$geometry;
    @Unique
    private boolean alpha_omega$resolved;

    @Override
    public OrbifoldGeometry alpha_omega$geometry() {
        if (!this.alpha_omega$resolved) {
            if (this.noiseBiomeSource instanceof Level level) this.alpha_omega$geometry = Orbifold.of(level);
            else if (this.noiseBiomeSource instanceof WorldGenRegion region) this.alpha_omega$geometry = Orbifold.of(region.getLevel());
            this.alpha_omega$resolved = true;
        }
        return this.alpha_omega$geometry;
    }

    @ModifyReturnValue(method = "withDifferentSource", at = @At("RETURN"))
    private BiomeManager alpha_omega$carryGeometry(BiomeManager copy) {
        OrbifoldGeometry geometry = this.alpha_omega$geometry();
        if (geometry != null) {
            BiomeManagerMixin mixin = (BiomeManagerMixin) (Object) copy;
            mixin.alpha_omega$geometry = geometry;
            mixin.alpha_omega$resolved = true;
        }
        return copy;
    }

    @WrapOperation(method = "getBiome", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/biome/BiomeManager;getFiddledDistance(JIIIDDD)D"))
    private double alpha_omega$canonicalQuart(long seed, int quartX, int quartY, int quartZ, double dx, double dy, double dz, Operation<Double> original) {
        OrbifoldGeometry geometry = this.alpha_omega$geometry();
        if (geometry == null) return original.call(seed, quartX, quartY, quartZ, dx, dy, dz);
        OrbifoldGeometry.Cell cell = geometry.canon((quartX << 2) + 1, (quartZ << 2) + 1);
        return original.call(seed, cell.x() >> 2, quartY, cell.z() >> 2, dx, dy, dz);
    }
}
