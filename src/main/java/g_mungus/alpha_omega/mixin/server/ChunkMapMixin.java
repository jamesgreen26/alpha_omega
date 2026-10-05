package g_mungus.alpha_omega.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.neighbour.CubeTrackingView;
import g_mungus.alpha_omega.neighbour.NeighbourViews;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a cube world a player's chunk tracking view also covers its virtual squares on neighbouring faces
 * ({@link CubeTrackingView}). Vanilla only tells the client its new centre for a plain square view; this does it for
 * the cube view's home square.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    @Shadow
    @Final
    ServerLevel level;

    @WrapOperation(method = "updateChunkTracking", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ChunkTrackingView;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
    private ChunkTrackingView alpha_omega$withNeighbours(ChunkPos center, int viewDistance, Operation<ChunkTrackingView> original, ServerPlayer player) {
        if (Cube.of(this.level) == null) return original.call(center, viewDistance);
        return NeighbourViews.view(this.level, player, center, viewDistance);
    }

    @Inject(method = "applyChunkTrackingView", at = @At("HEAD"))
    private void alpha_omega$sendCenter(ServerPlayer player, ChunkTrackingView view, CallbackInfo ci) {
        if (!(view instanceof CubeTrackingView cube) || player.level() != this.level) return;
        ChunkPos center = cube.home().center();
        ChunkTrackingView old = player.getChunkTrackingView();
        ChunkPos oldCenter = old instanceof CubeTrackingView oldCube ? oldCube.home().center()
            : old instanceof ChunkTrackingView.Positioned positioned ? positioned.center() : null;
        if (!center.equals(oldCenter)) player.connection.send(new ClientboundSetChunkCacheCenterPacket(center.x, center.z));
    }
}
