package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gametests run with structures, and only those {@link g_mungus.alpha_omega.gametest.GameTestFilter} lets through. */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {

    /** With structures, so the structure filter is tested against real generation. */
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD",
        target = "Lnet/minecraft/gametest/framework/GameTestServer;WORLD_OPTIONS:Lnet/minecraft/world/level/levelgen/WorldOptions;",
        opcode = Opcodes.GETSTATIC))
    private static WorldOptions alpha_omega$withStructures(WorldOptions options) {
        return new WorldOptions(options.seed(), true, options.generateBonusChest());
    }

    /** Only the tests {@link g_mungus.alpha_omega.gametest.GameTestFilter} lets through. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static java.util.Collection<net.minecraft.gametest.framework.TestFunction> alpha_omega$filter(
        java.util.Collection<net.minecraft.gametest.framework.TestFunction> functions) {
        return functions.stream().filter(g_mungus.alpha_omega.gametest.GameTestFilter::keep).toList();
    }
}
