package g_mungus.alpha_omega.mixin.band;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapBandAccessor {

    /** The chunk source's main thread executor, which also runs during {@code managedBlock} waits for chunks. */
    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> alpha_omega$mainThreadExecutor();
}
