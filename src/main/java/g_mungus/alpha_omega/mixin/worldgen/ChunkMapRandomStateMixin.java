package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.worldgen.CubeNoise;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A cube world's level samples its flat noises on the cube's surface ({@link CubeNoise}). */
@Mixin(ChunkMap.class)
abstract class ChunkMapRandomStateMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState alpha_omega$cubeRouter(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noises, long seed,
                                               Operation<RandomState> original, @Local(argsOnly = true) ServerLevel level,
                                               @Local(argsOnly = true) ChunkGenerator generator) {
        return original.call(generator instanceof CubeChunkGenerator cube ? CubeNoise.onCube(settings, cube.geometry(level)) : settings, noises, seed);
    }
}
