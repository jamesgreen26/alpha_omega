package g_mungus.alpha_omega.mixin.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.ishland.c2me.rewrites.chunksystem.common.statuses.VanillaWorldGenerationDelegate;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.c2me.C2meChunkSystem;
import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * C2ME builds worldgen neighborhood caches itself (vanilla's are stamped in {@code ChunkGenerationTaskMixin}); they
 * resolve images with their level's period.
 */
@Mixin(value = VanillaWorldGenerationDelegate.class, remap = false)
abstract class VanillaWorldGenerationDelegateMixin {

    @ModifyExpressionValue(method = "upgradeToThis", allow = 2,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StaticCache2D;create(IIILnet/minecraft/util/StaticCache2D$Initializer;)Lnet/minecraft/util/StaticCache2D;"))
    private StaticCache2D<?> alpha_omega$stampCache(StaticCache2D<?> cache, @Local(argsOnly = true) ChunkLoadingContext context) {
        return C2meChunkSystem.stamp(cache, context);
    }
}
