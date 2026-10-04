package g_mungus.alpha_omega.mixin.compat.c2me;

import com.ishland.c2me.opts.dfc.common.ast.AstNode;
import com.ishland.c2me.opts.dfc.common.ast.McToAst;
import com.ishland.c2me.opts.dfc.common.ast.misc.DelegateNode;
import g_mungus.alpha_omega.wrap.noise.PeriodicRaritySampler;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C2ME's density function compiler samples a weird scaled sampler's single noise at {@code x / rarity}, but a
 * periodic one needs a copy of its noise per rarity ({@code WeirdScaledSamplerMixin}). Periodic ones are left to the
 * compiler's fallback, which calls the vanilla function; everything around them stays compiled.
 */
@Mixin(value = McToAst.class, remap = false)
abstract class McToAstMixin {

    @Inject(method = "toAst", at = @At("HEAD"), cancellable = true)
    private static void alpha_omega$delegatePeriodicRarity(DensityFunction df, CallbackInfoReturnable<AstNode> cir) {
        if (df instanceof PeriodicRaritySampler sampler && sampler.alpha_omega$isPeriodic()) {
            cir.setReturnValue(new DelegateNode(df));
        }
    }
}
