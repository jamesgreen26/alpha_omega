package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.util.StaticCache2D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The worldgen neighborhood cache resolves images with its level's period. */
@Mixin(targets = "net.minecraft.server.level.ChunkGenerationTask")
abstract class ChunkGenerationTaskMixin {

    @Shadow
    @Final
    private GeneratingChunkMap chunkMap;

    @Shadow
    @Final
    private StaticCache2D<?> cache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void alpha_omega$stampCache(CallbackInfo ci) {
        if (this.chunkMap instanceof ChunkMap map) WrapHolder.set(this.cache, Wrap.of(((ChunkMapAccessor) map).alpha_omega$getLevel()));
    }
}
