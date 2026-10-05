package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gametests run in a cube world rather than vanilla's flat one, so they can exercise the faces and edges. Tests are
 * laid out high over the middle of UP instead of at a random spot (most of which is between faces); near the edges,
 * lower down belongs to the neighbouring faces.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/levelgen/presets/WorldPresets;FLAT:Lnet/minecraft/resources/ResourceKey;"))
    private static ResourceKey<WorldPreset> alpha_omega$cubePreset(ResourceKey<WorldPreset> flat) {
        return CubeChunkGenerator.PRESET;
    }

    /** With structures, so the structure filter is tested against real generation. */
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD",
        target = "Lnet/minecraft/gametest/framework/GameTestServer;WORLD_OPTIONS:Lnet/minecraft/world/level/levelgen/WorldOptions;",
        opcode = Opcodes.GETSTATIC))
    private static WorldOptions alpha_omega$withStructures(WorldOptions options) {
        return new WorldOptions(options.seed(), true, options.generateBonusChest());
    }

    @ModifyArg(method = "startTests", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/gametest/framework/StructureGridSpawner;<init>(Lnet/minecraft/core/BlockPos;IZ)V"))
    private BlockPos alpha_omega$onTheTopFace(BlockPos random, @Local(argsOnly = true) ServerLevel level) {
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) return random;
        return new BlockPos(geometry.centerX(CubeFace.UP) - 64, 100, geometry.centerZ() - 64);
    }
}
