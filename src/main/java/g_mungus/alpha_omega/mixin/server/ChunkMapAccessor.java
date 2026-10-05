package g_mungus.alpha_omega.mixin.server;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    /** For tests: the entity trackers, by entity id. */
    @Accessor("entityMap")
    it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> alpha_omega$entityMap();

    /** For debugging: the loaded chunks. */
    @Invoker("getChunks")
    Iterable<net.minecraft.server.level.ChunkHolder> alpha_omega$visibleChunks();

    @Invoker("getPlayerViewDistance")
    int alpha_omega$getPlayerViewDistance(ServerPlayer player);

    @Invoker("updateChunkTracking")
    void alpha_omega$updateChunkTracking(ServerPlayer player);
}
