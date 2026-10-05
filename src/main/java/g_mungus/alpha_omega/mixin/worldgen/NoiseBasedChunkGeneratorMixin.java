package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A cube world's chunk only computes noise above its noise floor ({@link CubeChunkGenerator#noiseSettings}): the
 * chunk's noise chunk, made here for every step that needs one (biomes, noise, surface, carvers), starts there.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {

    @WrapOperation(method = "createNoiseChunk", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk alpha_omega$aboveFloor(ChunkAccess chunk, RandomState random, DensityFunctions.BeardifierOrMarker beardifier,
                                             NoiseGeneratorSettings settings, Aquifer.FluidPicker fluids, Blender blender, Operation<NoiseChunk> original) {
        if ((Object) this instanceof CubeChunkGenerator cube) settings = cube.noiseSettings(chunk);
        return original.call(chunk, random, beardifier, settings, fluids, blender);
    }
}
