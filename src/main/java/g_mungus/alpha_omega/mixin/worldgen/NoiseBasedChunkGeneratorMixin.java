package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Carvers: each chunk replays the caves started by every chunk within 8, seeded by that source chunk. Across the
 * seam the source is reached through unrolled coordinates, so seed with its canonical position (the cave itself
 * is still laid out at the unrolled position, next to the chunk being carved).
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {

    @ModifyArg(method = "applyCarvers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"), index = 1)
    private int alpha_omega$canonSourceX(int chunkX, @Local(argsOnly = true) WorldGenRegion region) {
        return Wrap.of(region.getLevel()).canonChunk(chunkX);
    }

    @ModifyArg(method = "applyCarvers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"), index = 2)
    private int alpha_omega$canonSourceZ(int chunkZ, @Local(argsOnly = true) WorldGenRegion region) {
        return Wrap.of(region.getLevel()).canonChunk(chunkZ);
    }
}
