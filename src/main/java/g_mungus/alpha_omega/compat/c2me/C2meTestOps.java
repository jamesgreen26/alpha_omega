package g_mungus.alpha_omega.compat.c2me;

import com.ishland.c2me.rewrites.chunksystem.common.ducks.IChunkSystemAccess;
import g_mungus.alpha_omega.mixin.compat.c2me.StatusAdvancingSchedulerAccessor;
import g_mungus.alpha_omega.wrap.Wrap;
import java.util.concurrent.locks.StampedLock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** C2ME-facing steps of {@link g_mungus.alpha_omega.gametest.C2meGameTests}; loaded only when C2ME is. */
public final class C2meTestOps {

    private C2meTestOps() {
    }

    /** How many of the chunk system's holders are keyed by a position outside the canonical window. */
    public static int nonCanonicalHolders(ServerLevel level) {
        Wrap wrap = Wrap.of(level);
        StatusAdvancingSchedulerAccessor system = (StatusAdvancingSchedulerAccessor) ((IChunkSystemAccess) level.getChunkSource().chunkMap).c2me$getTheChunkSystem();
        StampedLock lock = system.alpha_omega$getItemsLock();
        long stamp = lock.readLock();
        try {
            int count = 0;
            for (Object key : system.alpha_omega$getItems().keySet()) {
                if (key instanceof ChunkPos pos && !wrap.isCanonChunk(pos.x, pos.z)) count++;
            }
            return count;
        } finally {
            lock.unlockRead(stamp);
        }
    }
}
