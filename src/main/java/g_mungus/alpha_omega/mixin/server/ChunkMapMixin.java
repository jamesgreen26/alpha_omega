package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.WrapHolder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    /** R2: every chunk holder lookup goes through these maps. */
    @ModifyVariable(method = {"getVisibleChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;",
        "getUpdatingChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;",
        "acquireGeneration(J)Lnet/minecraft/server/level/GenerationChunkHolder;"},
        at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonKey(long chunkKey) {
        return Wrap.of(this.level).canonChunkKey(chunkKey);
    }

    /** R5: player-to-chunk distance (natural spawning and random ticks) uses the nearest image. */
    @Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void alpha_omega$wrappedDistance(ChunkPos chunk, Entity entity, CallbackInfoReturnable<Double> cir) {
        Wrap wrap = Wrap.of(entity.level());
        double dx = wrap.minDelta(SectionPos.sectionToBlockCoord(chunk.x, 8), entity.getX());
        double dz = wrap.minDelta(SectionPos.sectionToBlockCoord(chunk.z, 8), entity.getZ());
        cir.setReturnValue(dx * dx + dz * dz);
    }

    /** Pending chunks are keyed canonically. */
    @WrapOperation(method = "isChunkTracked", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;isPending(J)Z"))
    private boolean alpha_omega$canonPending(PlayerChunkSender sender, long chunkKey, Operation<Boolean> original) {
        return original.call(sender, Wrap.of(this.level).canonChunkKey(chunkKey));
    }

    /** Player chunk views test containment by nearest image, so they need this level's period. */
    @ModifyExpressionValue(method = "updateChunkTracking",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
    private ChunkTrackingView alpha_omega$stampView(ChunkTrackingView view) {
        WrapHolder.set(view, Wrap.of(this.level));
        return view;
    }
}
