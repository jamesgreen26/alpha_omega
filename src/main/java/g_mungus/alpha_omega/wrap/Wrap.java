package g_mungus.alpha_omega.wrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * World-wrapping configuration and helpers over Minecraft position types.
 * <p>
 * Phase 1: one hardcoded period shared by every dimension, always enabled.
 */
public final class Wrap {

    /**
     * {@code W}: world period in blocks on each horizontal axis. A multiple of 3072 (see design doc §4.1), and of
     * 4096 so the lowest climate octaves (temperature, at a quarter scale and 2^-10) span a whole number of lattice
     * cells and keep their vanilla size. Large Biomes would need a multiple of 16384 for the same.
     */
    public static final int PERIOD = 12288;
    /** {@code N}: world period in chunks (and sections). */
    public static final int CHUNK_PERIOD = PERIOD >> 4;

    private Wrap() {
    }

    // ---- canonicalization (storage addresses) ----

    public static int canonBlock(int x) {
        return WrapMath.canon(x, PERIOD);
    }

    public static int canonChunk(int x) {
        return WrapMath.canon(x, CHUNK_PERIOD);
    }

    public static boolean isCanonBlock(int x, int z) {
        return x >= 0 && x < PERIOD && z >= 0 && z < PERIOD;
    }

    public static boolean isCanonChunk(int x, int z) {
        return x >= 0 && x < CHUNK_PERIOD && z >= 0 && z < CHUNK_PERIOD;
    }

    public static BlockPos canon(BlockPos pos) {
        return isCanonBlock(pos.getX(), pos.getZ()) ? pos : new BlockPos(canonBlock(pos.getX()), pos.getY(), canonBlock(pos.getZ()));
    }

    public static ChunkPos canon(ChunkPos pos) {
        return isCanonChunk(pos.x, pos.z) ? pos : new ChunkPos(canonChunk(pos.x), canonChunk(pos.z));
    }

    public static SectionPos canon(SectionPos pos) {
        return isCanonChunk(pos.x(), pos.z()) ? pos : SectionPos.of(canonChunk(pos.x()), pos.y(), canonChunk(pos.z()));
    }

    /** Canonicalize a packed {@link ChunkPos} key. {@link ChunkPos#INVALID_CHUNK_POS} is passed through. */
    public static long canonChunkKey(long key) {
        if (key == ChunkPos.INVALID_CHUNK_POS) return key;
        int x = ChunkPos.getX(key);
        int z = ChunkPos.getZ(key);
        return isCanonChunk(x, z) ? key : ChunkPos.asLong(canonChunk(x), canonChunk(z));
    }

    /** Canonicalize a packed {@link SectionPos} key. */
    public static long canonSectionKey(long key) {
        int x = SectionPos.x(key);
        int z = SectionPos.z(key);
        return isCanonChunk(x, z) ? key : SectionPos.asLong(canonChunk(x), SectionPos.y(key), canonChunk(z));
    }

    /** Canonicalize a packed {@link BlockPos} key. */
    public static long canonBlockKey(long key) {
        int x = BlockPos.getX(key);
        int z = BlockPos.getZ(key);
        return isCanonBlock(x, z) ? key : BlockPos.asLong(canonBlock(x), BlockPos.getY(key), canonBlock(z));
    }

    // ---- nearest image (bridging frames) ----

    public static int nearestBlock(int x, int ref) {
        return WrapMath.nearestImage(x, ref, PERIOD);
    }

    public static int nearestChunk(int x, int ref) {
        return WrapMath.nearestImage(x, ref, CHUNK_PERIOD);
    }

    public static double nearest(double x, double ref) {
        return WrapMath.nearestImage(x, ref, PERIOD);
    }

    public static double minDelta(double a, double b) {
        return WrapMath.minDelta(a, b, PERIOD);
    }

    public static int minChunkDelta(int a, int b) {
        return WrapMath.minDelta(a, b, CHUNK_PERIOD);
    }

    /** The whole-lap offset (a multiple of {@code W}) that moves block coordinate {@code x} nearest to {@code ref}. */
    public static int lapOffset(int x, int ref) {
        return nearestBlock(x, ref) - x;
    }

    /** The divisor of {@code N} closest to {@code value} (the larger one on a tie), for grids that must tile the world. */
    public static int nearestChunkPeriodDivisor(int value) {
        int best = 1;
        for (int d = 1; d <= CHUNK_PERIOD; d++) {
            if (CHUNK_PERIOD % d == 0 && Math.abs(d - value) <= Math.abs(best - value)) best = d;
        }
        return best;
    }

    /** The image of {@code pos} nearest to the block containing {@code ref}. */
    public static BlockPos nearest(BlockPos pos, Vec3 ref) {
        int x = nearestBlock(pos.getX(), (int) Math.floor(ref.x));
        int z = nearestBlock(pos.getZ(), (int) Math.floor(ref.z));
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }

    public static Vec3 nearest(Vec3 pos, Vec3 ref) {
        double x = nearest(pos.x, ref.x);
        double z = nearest(pos.z, ref.z);
        return x == pos.x && z == pos.z ? pos : new Vec3(x, pos.y, z);
    }

    public static ChunkPos nearest(ChunkPos pos, int refX, int refZ) {
        int x = nearestChunk(pos.x, refX);
        int z = nearestChunk(pos.z, refZ);
        return x == pos.x && z == pos.z ? pos : new ChunkPos(x, z);
    }
}
