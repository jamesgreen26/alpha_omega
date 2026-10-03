package g_mungus.alpha_omega.mixin.server.light;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockLightEngine.class)
abstract class BlockLightEngineMixin {

    @ModifyExpressionValue(method = {"propagateIncrease", "propagateDecrease"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(JLnet/minecraft/core/Direction;)J"))
    private long alpha_omega$wrapNeighbor(long pos) {
        return ((WrapFlag) this).alpha_omega$isWrapped() ? Wrap.canonBlockKey(pos) : pos;
    }

    @ModifyVariable(method = "propagateLightSources", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonChunk(ChunkPos pos) {
        return ((WrapFlag) this).alpha_omega$isWrapped() ? Wrap.canon(pos) : pos;
    }
}
