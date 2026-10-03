package g_mungus.alpha_omega.mixin.server.light;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Section neighbor bookkeeping wraps, so sections on either side of the seam count each other as neighbors. */
@Mixin(LayerLightSectionStorage.class)
abstract class LayerLightSectionStorageMixin {

    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Unique
    private boolean alpha_omega$wrapped;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$init(CallbackInfo ci) {
        this.alpha_omega$wrapped = this.chunkSource instanceof ServerChunkCache;
    }

    @ModifyExpressionValue(method = "updateSectionStatus",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;offset(JIII)J"))
    private long alpha_omega$wrapNeighbor(long sectionKey) {
        return this.alpha_omega$wrapped ? Wrap.canonSectionKey(sectionKey) : sectionKey;
    }

    @ModifyVariable(method = "lightOnInSection", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonSection(long sectionKey) {
        return this.alpha_omega$wrapped ? Wrap.canonSectionKey(sectionKey) : sectionKey;
    }
}
