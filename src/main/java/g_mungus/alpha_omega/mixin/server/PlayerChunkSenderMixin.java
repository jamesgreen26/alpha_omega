package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Pending chunks are keyed canonically (they come from canonical {@link LevelChunk#getPos()}); the pending check in
 * {@code ChunkMap.isChunkTracked} is canonicalized there.
 */
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {

    @ModifyVariable(method = "dropChunk", at = @At("HEAD"), argsOnly = true)
    private ChunkPos alpha_omega$canonDrop(ChunkPos pos, @Local(argsOnly = true) ServerPlayer player) {
        return Wrap.of(player.level()).canon(pos);
    }

    /** Send nearest chunks first, measuring distance from the player's lifted chunk to the nearest image. */
    @ModifyArg(method = "collectChunksToSend",
        at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;", ordinal = 0))
    private ToIntFunction<Long> alpha_omega$sortKeys(ToIntFunction<Long> original, @Local(argsOnly = true) ChunkMap map, @Local(argsOnly = true) ChunkPos center) {
        Wrap wrap = Wrap.of(((ChunkMapAccessor) map).alpha_omega$getLevel());
        return key -> alpha_omega$distanceSquared(wrap, center, ChunkPos.getX(key), ChunkPos.getZ(key));
    }

    @ModifyArg(method = "collectChunksToSend",
        at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;", ordinal = 1))
    private ToIntFunction<LevelChunk> alpha_omega$sortChunks(ToIntFunction<LevelChunk> original, @Local(argsOnly = true) ChunkMap map, @Local(argsOnly = true) ChunkPos center) {
        Wrap wrap = Wrap.of(((ChunkMapAccessor) map).alpha_omega$getLevel());
        return chunk -> alpha_omega$distanceSquared(wrap, center, chunk.getPos().x, chunk.getPos().z);
    }

    private static int alpha_omega$distanceSquared(Wrap wrap, ChunkPos center, int x, int z) {
        int dx = wrap.minChunkDelta(x, center.x);
        int dz = wrap.minChunkDelta(z, center.z);
        return dx * dx + dz * dz;
    }
}
