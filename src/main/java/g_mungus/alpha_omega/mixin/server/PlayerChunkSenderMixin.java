package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import java.util.Comparator;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla sends a player's pending chunks nearest first by storage distance, which with image views puts every chunk
 * of an image (far away in storage) after every chunk of its own square. They go by distance from the player's
 * position in their own square instead, its own square a little ahead ({@link NeighbourViews#sendPriority}).
 */
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {

    /** The player whose chunks are being chosen, for the sort below. */
    @Unique
    @Nullable
    private ServerPlayer alpha_omega$player;

    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void alpha_omega$rememberPlayer(ServerPlayer player, CallbackInfo ci) {
        this.alpha_omega$player = player;
    }

    @WrapOperation(method = "collectChunksToSend", at = @At(value = "INVOKE",
        target = "Ljava/util/Comparator;comparingInt(Ljava/util/function/ToIntFunction;)Ljava/util/Comparator;"))
    private Comparator<Object> alpha_omega$alongTheSurface(ToIntFunction<Object> distance, Operation<Comparator<Object>> original) {
        ServerPlayer player = this.alpha_omega$player;
        if (player == null || !NeighbourViews.active(player.serverLevel())) return original.call(distance);
        // Pending chunks are sorted as positions, or as loaded chunks once they are ready.
        return Comparator.comparingDouble(chunk -> NeighbourViews.sendPriority(player,
            chunk instanceof LevelChunk loaded ? loaded.getPos().toLong() : (Long) chunk));
    }
}
