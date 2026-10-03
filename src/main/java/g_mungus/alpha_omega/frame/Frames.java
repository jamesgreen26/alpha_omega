package g_mungus.alpha_omega.frame;

import g_mungus.alpha_omega.wrap.Wrap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Lifts canonical block-side positions into the simulation frame where block-side code starts executing (design
 * doc §7.3, rule R4).
 * <p>
 * Provisional, until islands exist (phase 3): a chunk lifts to its image nearest the closest player, which is
 * the frame that player and everything around them live in. With no players a chunk stays canonical. The island
 * lift table replaces {@link #lapOffset} without changing any caller.
 */
public final class Frames {

    private Frames() {
    }

    /** Per-level cache of chunk lap offsets, valid for one game tick. */
    public interface Cache {

        Long2LongOpenHashMap alpha_omega$lapOffsets();

        long alpha_omega$lapOffsetsTick();

        void alpha_omega$setLapOffsetsTick(long tick);
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
        if (level.players().isEmpty()) return 0;
        Cache cache = (Cache) level;
        Long2LongOpenHashMap offsets = cache.alpha_omega$lapOffsets();
        long tick = level.getGameTime();
        if (cache.alpha_omega$lapOffsetsTick() != tick) {
            offsets.clear();
            cache.alpha_omega$setLapOffsetsTick(tick);
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        long offset = offsets.get(key);
        if (offset == Long.MIN_VALUE) {
            offset = computeLapOffset(level, chunkX, chunkZ);
            offsets.put(key, offset);
        }
        return offset;
    }

    public static int offsetX(long offset) {
        return (int) (offset >> 32);
    }

    public static int offsetZ(long offset) {
        return (int) offset;
    }

    private static long computeLapOffset(ServerLevel level, int chunkX, int chunkZ) {
        int x = SectionPos.sectionToBlockCoord(chunkX, 8);
        int z = SectionPos.sectionToBlockCoord(chunkZ, 8);
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double dx = Wrap.minDelta(player.getX(), x);
            double dz = Wrap.minDelta(player.getZ(), z);
            double distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) return 0;
        int dx = Wrap.lapOffset(x, nearest.getBlockX());
        int dz = Wrap.lapOffset(z, nearest.getBlockZ());
        return ((long) dx << 32) | (dz & 0xFFFFFFFFL);
    }
}
