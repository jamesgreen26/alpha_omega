package g_mungus.alpha_omega.mixin.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.ChunkLoadingContext;
import com.ishland.c2me.rewrites.chunksystem.common.statuses.ServerAccessibleChunkSending;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.compat.c2me.C2meChunkSystem;
import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The neighborhood C2ME's no-tick view distance builds to suppress ghost mushrooms resolves images with its level's
 * period. It is in an overwrite from that module, so this applies after it, and only when the module is enabled.
 */
@Mixin(value = ServerAccessibleChunkSending.class, priority = 1500, remap = false)
abstract class ServerAccessibleChunkSendingMixin {

    @ModifyExpressionValue(method = "upgradeToThis", require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StaticCache2D;create(IIILnet/minecraft/util/StaticCache2D$Initializer;)Lnet/minecraft/util/StaticCache2D;"))
    private StaticCache2D<?> alpha_omega$stampCache(StaticCache2D<?> cache, @Local(argsOnly = true) ChunkLoadingContext context) {
        return C2meChunkSystem.stamp(cache, context);
    }
}
