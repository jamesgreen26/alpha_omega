package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** R2: chunk lookups by coordinate resolve to the canonical chunk. */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {


    @ModifyVariable(method = {
        "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
        "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
        "getChunkFuture",
        "hasChunk(II)Z",
        "getChunkForLighting(II)Lnet/minecraft/world/level/chunk/LightChunk;",
    }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpha_omega$canonX(int x) {
        return Wrap.canonChunk(x);
    }

    @ModifyVariable(method = {
        "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
        "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
        "getChunkFuture",
        "hasChunk(II)Z",
        "getChunkForLighting(II)Lnet/minecraft/world/level/chunk/LightChunk;",
    }, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpha_omega$canonZ(int z) {
        return Wrap.canonChunk(z);
    }

    @ModifyVariable(method = "isPositionTicking(J)Z", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonKey(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }
}
