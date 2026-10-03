package g_mungus.alpha_omega.mixin.server.light;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SkyLightEngine.class)
abstract class SkyLightEngineMixin {

    @ModifyExpressionValue(method = {"propagateIncrease", "propagateDecrease"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
    private long alpha_omega$wrapNeighbor(long pos) {
        return WrapHolder.of(this).canonBlockKey(pos);
    }

    @ModifyVariable(method = {"propagateLightSources", "setLightEnabled"}, at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonChunk(ChunkPos pos) {
        return WrapHolder.of(this).canon(pos);
    }
}
