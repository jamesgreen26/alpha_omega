package g_mungus.alpha_omega.mixin.server;

import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    /** R2: every chunk holder lookup goes through these two maps. */
    @ModifyVariable(method = {"getVisibleChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;",
        "getUpdatingChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;",
        "acquireGeneration(J)Lnet/minecraft/server/level/GenerationChunkHolder;"},
        at = @At("HEAD"), argsOnly = true)
    private long alpha_omega$canonKey(long chunkKey) {
        return Wrap.canonChunkKey(chunkKey);
    }

    /** R5: player-to-chunk distance (natural spawning and random ticks) uses the nearest image. */
    @Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void alpha_omega$wrappedDistance(ChunkPos chunk, Entity entity, CallbackInfoReturnable<Double> cir) {
        double dx = Wrap.minDelta(SectionPos.sectionToBlockCoord(chunk.x, 8), entity.getX());
        double dz = Wrap.minDelta(SectionPos.sectionToBlockCoord(chunk.z, 8), entity.getZ());
        cir.setReturnValue(dx * dx + dz * dz);
    }
}
