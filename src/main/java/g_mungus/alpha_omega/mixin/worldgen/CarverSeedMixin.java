package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Carvers: each chunk replays the caves started by every chunk within 8, seeded by that chunk. Past a seam, the starting
 * chunk is seeded as its canonical chunk, so a cave started there is the one its source starts. The cave is laid out
 * from the chunk where it starts, unturned: exact across the east–west seam, but across a fold the replayed cave is
 * not the half turn of its source's, a residue ({@code TerrainGameTests}).
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class CarverSeedMixin {

    @WrapOperation(method = "applyCarvers", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void alpha_omega$canonicalSource(WorldgenRandom random, long seed, int chunkX, int chunkZ, Operation<Void> original) {
        if ((Object) this instanceof OrbifoldChunkGenerator orbifold) {
            OrbifoldGeometry.Cell source = orbifold.geometry().canonChunk(chunkX, chunkZ);
            original.call(random, seed, source.x(), source.z());
        } else {
            original.call(random, seed, chunkX, chunkZ);
        }
    }
}
