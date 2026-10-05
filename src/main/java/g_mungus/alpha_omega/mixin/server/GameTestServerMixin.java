package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
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
 * Gametests run in an orbifold world (with the config's settings: the default size) rather than vanilla's flat one,
 * with structures, laid out high over spawn instead of at a random spot. Only the tests
 * {@link g_mungus.alpha_omega.gametest.GameTestFilter} lets through run.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/levelgen/presets/WorldPresets;FLAT:Lnet/minecraft/resources/ResourceKey;"))
    private static ResourceKey<WorldPreset> alpha_omega$orbifoldPreset(ResourceKey<WorldPreset> flat) {
        return OrbifoldChunkGenerator.PRESET;
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
    private BlockPos alpha_omega$overSpawn(BlockPos random, @Local(argsOnly = true) ServerLevel level) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) return random;
        return new BlockPos(geometry.spawnX - 64, 100, geometry.spawnZ - 64);
    }

    /** Only the tests {@link g_mungus.alpha_omega.gametest.GameTestFilter} lets through. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static java.util.Collection<net.minecraft.gametest.framework.TestFunction> alpha_omega$filter(
        java.util.Collection<net.minecraft.gametest.framework.TestFunction> functions) {
        return functions.stream().filter(g_mungus.alpha_omega.gametest.GameTestFilter::keep).toList();
    }
}
