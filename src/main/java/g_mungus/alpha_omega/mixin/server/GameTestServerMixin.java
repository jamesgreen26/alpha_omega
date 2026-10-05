package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Gametests run in a cube world rather than vanilla's flat one, so they can exercise the faces and edges. */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/levelgen/presets/WorldPresets;FLAT:Lnet/minecraft/resources/ResourceKey;"))
    private static ResourceKey<WorldPreset> alpha_omega$cubePreset(ResourceKey<WorldPreset> flat) {
        return CubeChunkGenerator.PRESET;
    }
}
