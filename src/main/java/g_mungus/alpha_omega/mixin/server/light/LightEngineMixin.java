package g_mungus.alpha_omega.mixin.server.light;

import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * R1/R2 for light: on the server every position entering the engine is canonicalized, and propagation
 * (see {@link BlockLightEngineMixin}, {@link SkyLightEngineMixin}) wraps neighbor offsets. Client light engines are
 * built outside any level's wrap context, so they stay in the client's unrolled frame.
 */
@Mixin(LightEngine.class)
abstract class LightEngineMixin {

    @ModifyVariable(method = {"checkBlock", "getLightValue"}, at = @At("HEAD"), argsOnly = true)
    private BlockPos alpha_omega$canonBlock(BlockPos pos) {
        return WrapHolder.of(this).canon(pos);
    }

    @ModifyVariable(method = "queueSectionData", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonSectionKey(long sectionKey) {
        return WrapHolder.of(this).canonSectionKey(sectionKey);
    }

    @ModifyVariable(method = {"updateSectionStatus", "getDataLayerData"}, at = @At("HEAD"), argsOnly = true)
    private SectionPos alpha_omega$canonSection(SectionPos pos) {
        return WrapHolder.of(this).canon(pos);
    }

    @ModifyVariable(method = {"retainData", "setLightEnabled"}, at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonChunk(ChunkPos pos) {
        return WrapHolder.of(this).canon(pos);
    }
}
