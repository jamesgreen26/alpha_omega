package g_mungus.alpha_omega.mixin.server.light;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapFlag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R1/R2 for light: on the server every position entering the engine is canonicalized, and propagation
 * (see {@link BlockLightEngineMixin}, {@link SkyLightEngineMixin}) wraps neighbor offsets.
 */
@Mixin(LightEngine.class)
abstract class LightEngineMixin implements WrapFlag {

    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @Unique
    private boolean alpha_omega$wrapped;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$init(CallbackInfo ci) {
        this.alpha_omega$wrapped = this.chunkSource instanceof ServerChunkCache;
    }

    @Override
    public boolean alpha_omega$isWrapped() {
        return this.alpha_omega$wrapped;
    }

    @Override
    public void alpha_omega$setWrapped(boolean wrapped) {
        this.alpha_omega$wrapped = wrapped;
    }

    @ModifyVariable(method = {"checkBlock", "getLightValue"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonBlock(BlockPos pos) {
        return this.alpha_omega$wrapped ? Wrap.canon(pos) : pos;
    }

    @ModifyVariable(method = "queueSectionData", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonSectionKey(long sectionKey) {
        return this.alpha_omega$wrapped ? Wrap.canonSectionKey(sectionKey) : sectionKey;
    }

    @ModifyVariable(method = {"updateSectionStatus", "getDataLayerData"}, at = @At("HEAD"), argsOnly = true)
    private SectionPos alpha_omega$canonSection(SectionPos pos) {
        return this.alpha_omega$wrapped ? Wrap.canon(pos) : pos;
    }

    @ModifyVariable(method = {"retainData", "setLightEnabled"}, at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonChunk(ChunkPos pos) {
        return this.alpha_omega$wrapped ? Wrap.canon(pos) : pos;
    }
}
