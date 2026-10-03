package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.function.ToIntFunction;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Pending chunks are keyed canonically (they come from canonical {@link LevelChunk#getPos()}). */
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {

    @ModifyVariable(method = "dropChunk", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonDrop(ChunkPos pos) {
        return Wrap.canon(pos);
    }

    @ModifyVariable(method = "isPending", at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonPending(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }

    /** Send nearest chunks first, measuring distance from the player's lifted chunk to the nearest image. */
    @ModifyArg(method = "collectChunksToSend",
        at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;", ordinal = 0))
    private ToIntFunction<Long> alpha_omega$sortKeys(ToIntFunction<Long> original, @Local(argsOnly = true) ChunkPos center) {
        return key -> alpha_omega$distanceSquared(center, ChunkPos.getX(key), ChunkPos.getZ(key));
    }

    @ModifyArg(method = "collectChunksToSend",
        at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;", ordinal = 1))
    private ToIntFunction<LevelChunk> alpha_omega$sortChunks(ToIntFunction<LevelChunk> original, @Local(argsOnly = true) ChunkPos center) {
        return chunk -> alpha_omega$distanceSquared(center, chunk.getPos().x, chunk.getPos().z);
    }

    private static int alpha_omega$distanceSquared(ChunkPos center, int x, int z) {
        int dx = Wrap.minChunkDelta(x, center.x);
        int dz = Wrap.minChunkDelta(z, center.z);
        return dx * dx + dz * dz;
    }
}
