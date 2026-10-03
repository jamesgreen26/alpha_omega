package g_mungus.alpha_omega.frame;

import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.island.IslandGraph;
import g_mungus.alpha_omega.island.IslandManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lifts canonical block-side positions into the simulation frame where block-side code starts executing (design
 * doc §7.3, rule R4): the frame of the chunk's island. A chunk outside every island (not loaded) falls back to the
 * image nearest the closest player.
 */
public final class Frames {

    private Frames() {
    }

    public static BlockPos lift(ServerLevel level, BlockPos pos) {
        long offset = lapOffset(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return offset == 0 ? pos : pos.offset(offsetX(offset), 0, offsetZ(offset));
    }

    /**
     * The whole-lap shift, in blocks, that lifts chunk {@code (chunkX, chunkZ)} (any image) into its frame, packed
     * as two ints; read it with {@link #offsetX} and {@link #offsetZ}.
     */
    public static long lapOffset(ServerLevel level, int chunkX, int chunkZ) {
        Wrap wrap = Wrap.of(level);
        if (!wrap.enabled()) return 0;
        long laps = IslandManager.of(level).laps(chunkX, chunkZ);
        if (laps == IslandGraph.ABSENT) return nearestPlayerOffset(level, wrap, chunkX, chunkZ);
        int dx = (wrap.canonChunk(chunkX) + IslandGraph.lapX(laps) * wrap.chunkPeriod - chunkX) << 4;
        int dz = (wrap.canonChunk(chunkZ) + IslandGraph.lapZ(laps) * wrap.chunkPeriod - chunkZ) << 4;
        return pack(dx, dz);
    }

    public static int offsetX(long offset) {
        return (int) (offset >> 32);
    }

    public static int offsetZ(long offset) {
        return (int) offset;
    }

    private static long pack(int dx, int dz) {
        return ((long) dx << 32) | (dz & 0xFFFFFFFFL);
    }

    private static long nearestPlayerOffset(ServerLevel level, Wrap wrap, int chunkX, int chunkZ) {
        int x = SectionPos.sectionToBlockCoord(chunkX, 8);
        int z = SectionPos.sectionToBlockCoord(chunkZ, 8);
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = wrap.minDelta(player.getX(), x);
            double dz = wrap.minDelta(player.getZ(), z);
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) return 0;
        return pack(wrap.lapOffset(x, nearest.getBlockX()), wrap.lapOffset(z, nearest.getBlockZ()));
    }
}
