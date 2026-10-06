package g_mungus.alpha_omega.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In an orbifold, a chunk beside the band or skirt has its light sources propagated when it is lit, even if it was
 * saved as lit ({@link OrbifoldChunkGenerator#relightsOnLoad}): a band fill next to it while it was unloaded can have
 * left dark data layers in its column.
 */
@Mixin(ChunkStatusTasks.class)
abstract class ChunkLightMixin {

    @WrapOperation(method = "light", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ThreadedLevelLightEngine;lightChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<ChunkAccess> alpha_omega$relightBesideBand(ThreadedLevelLightEngine engine, ChunkAccess chunk, boolean lighted,
                                                                             Operation<CompletableFuture<ChunkAccess>> original,
                                                                             @Local(argsOnly = true) WorldGenContext context) {
        if (lighted && context.generator() instanceof OrbifoldChunkGenerator orbifold && orbifold.relightsOnLoad(chunk.getPos())) lighted = false;
        return original.call(engine, chunk, lighted);
    }
}
