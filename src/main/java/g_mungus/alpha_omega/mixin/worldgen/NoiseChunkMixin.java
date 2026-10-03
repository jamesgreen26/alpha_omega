package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A noise chunk (and the aquifer it builds) generates in its noise's dimension. */
@Mixin(NoiseChunk.class)
abstract class NoiseChunkMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$stamp(CallbackInfo ci, @Local(argsOnly = true) RandomState randomState) {
        WrapHolder.set(this, WrapHolder.of(randomState));
    }
}
