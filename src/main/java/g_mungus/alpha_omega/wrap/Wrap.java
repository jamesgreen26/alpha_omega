package g_mungus.alpha_omega.wrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Wrapping for one dimension: its period and helpers over Minecraft position types. {@link #NONE} (an unwrapped
 * dimension, or a world created without wrapping) makes every operation the identity, so code can apply it
 * unconditionally.
 */
public final class Wrap {

    public static final Wrap NONE = new Wrap(0);

    /** {@code W}: period in blocks on each horizontal axis, or 0 if this dimension does not wrap. */
    public final int period;
    /** {@code N}: period in chunks (and sections). */
    public final int chunkPeriod;

    public Wrap(int period) {
        if (period < 0 || period % 16 != 0) throw new IllegalArgumentException("World period must be a non-negative multiple of 16: " + period);
        this.period = period;
        this.chunkPeriod = period >> 4;
    }

    public static Wrap of(Level level) {
        return Wraps.of(level.dimension());
    }

    public static Wrap of(ResourceKey<Level> dimension) {
        return Wraps.of(dimension);
    }

    /** For a level or a worldgen region of one; unwrapped for anything else. */
    public static Wrap of(LevelAccessor level) {
        if (level instanceof Level real) return of(real);
        if (level instanceof ServerLevelAccessor server) return of(server.getLevel());
        return NONE;
    }

    public boolean enabled() {
        return this.period > 0;
    }

    // ---- off-torus coordinates ----

    /*
     * Another mod's far-away storage (Sable's sub-level plots) can be excluded from the torus: on each axis,
     * coordinates in the excluded chunk range are never folded, lifted or bridged. Empty unless such a mod is loaded.
     */
    private static int offTorusMinChunk = Integer.MAX_VALUE;
    private static int offTorusMaxChunk = Integer.MAX_VALUE;

    /** Excludes chunk coordinates {@code [minChunk, maxChunk)} on each horizontal axis from the torus; call during mod construction. */
    public static void excludeFromTorus(int minChunk, int maxChunk) {
        if (minChunk > maxChunk) throw new IllegalArgumentException("Empty range: " + minChunk + ", " + maxChunk);
        offTorusMinChunk = minChunk;
        offTorusMaxChunk = maxChunk;
    }

    public static boolean offTorusChunk(int chunkX) {
        return chunkX >= offTorusMinChunk && chunkX < offTorusMaxChunk;
    }

    public static boolean offTorus(int blockX) {
        return offTorusChunk(blockX >> 4);
    }

    public static boolean offTorus(double x) {
        return offTorusChunk(((int) Math.floor(x)) >> 4);
    }

    /** Whether a horizontal block position is off the torus on either axis. */
    public static boolean offTorus(int blockX, int blockZ) {
        return offTorus(blockX) || offTorus(blockZ);
    }

    public static boolean offTorusChunk(int chunkX, int chunkZ) {
        return offTorusChunk(chunkX) || offTorusChunk(chunkZ);
    }

    // ---- canonicalization (storage addresses) ----

    public int canonBlock(int x) {
        return this.enabled() && !offTorus(x) ? WrapMath.canon(x, this.period) : x;
    }

    public int canonChunk(int x) {
        return this.enabled() && !offTorusChunk(x) ? WrapMath.canon(x, this.chunkPeriod) : x;
    }

    public double canon(double x) {
        if (!this.enabled() || offTorus(x)) return x;
        double c = x % this.period;
        return c < 0 ? c + this.period : c;
    }

    public boolean isCanonBlock(int x, int z) {
        return !this.enabled() || (isCanon(x, this.period) || offTorus(x)) && (isCanon(z, this.period) || offTorus(z));
    }

    public boolean isCanonChunk(int x, int z) {
        return !this.enabled() || (isCanon(x, this.chunkPeriod) || offTorusChunk(x)) && (isCanon(z, this.chunkPeriod) || offTorusChunk(z));
    }

    private static boolean isCanon(int x, int period) {
        return x >= 0 && x < period;
    }

    public BlockPos canon(BlockPos pos) {
        return this.isCanonBlock(pos.getX(), pos.getZ()) ? pos : new BlockPos(this.canonBlock(pos.getX()), pos.getY(), this.canonBlock(pos.getZ()));
    }

    public ChunkPos canon(ChunkPos pos) {
        return this.isCanonChunk(pos.x, pos.z) ? pos : new ChunkPos(this.canonChunk(pos.x), this.canonChunk(pos.z));
    }

    public SectionPos canon(SectionPos pos) {
        return this.isCanonChunk(pos.x(), pos.z()) ? pos : SectionPos.of(this.canonChunk(pos.x()), pos.y(), this.canonChunk(pos.z()));
    }

    /** Canonicalize a packed {@link ChunkPos} key. {@link ChunkPos#INVALID_CHUNK_POS} is passed through. */
    public long canonChunkKey(long key) {
        if (key == ChunkPos.INVALID_CHUNK_POS) return key;
        int x = ChunkPos.getX(key);
        int z = ChunkPos.getZ(key);
        return this.isCanonChunk(x, z) ? key : ChunkPos.asLong(this.canonChunk(x), this.canonChunk(z));
    }

    /** Canonicalize a packed {@link SectionPos} key. */
    public long canonSectionKey(long key) {
        int x = SectionPos.x(key);
        int z = SectionPos.z(key);
        return this.isCanonChunk(x, z) ? key : SectionPos.asLong(this.canonChunk(x), SectionPos.y(key), this.canonChunk(z));
    }

    /** Canonicalize a packed {@link BlockPos} key. */
    public long canonBlockKey(long key) {
        int x = BlockPos.getX(key);
        int z = BlockPos.getZ(key);
        return this.isCanonBlock(x, z) ? key : BlockPos.asLong(this.canonBlock(x), BlockPos.getY(key), this.canonBlock(z));
    }

    /** Which lap a block coordinate is in (0 if unwrapped or off the torus). */
    public int lap(int x) {
        return this.enabled() && !offTorus(x) ? Math.floorDiv(x, this.period) : 0;
    }

    // ---- nearest image (bridging frames); the identity when either side is off the torus ----

    public int nearestBlock(int x, int ref) {
        return this.enabled() && !offTorus(x) && !offTorus(ref) ? WrapMath.nearestImage(x, ref, this.period) : x;
    }

    public int nearestChunk(int x, int ref) {
        return this.enabled() && !offTorusChunk(x) && !offTorusChunk(ref) ? WrapMath.nearestImage(x, ref, this.chunkPeriod) : x;
    }

    public double nearest(double x, double ref) {
        return this.enabled() && !offTorus(x) && !offTorus(ref) ? WrapMath.nearestImage(x, ref, this.period) : x;
    }

    public double minDelta(double a, double b) {
        return this.enabled() && !offTorus(a) && !offTorus(b) ? WrapMath.minDelta(a, b, this.period) : a - b;
    }

    public int minChunkDelta(int a, int b) {
        return this.enabled() && !offTorusChunk(a) && !offTorusChunk(b) ? WrapMath.minDelta(a, b, this.chunkPeriod) : a - b;
    }

    /** The whole-lap offset (a multiple of {@code W}) that moves block coordinate {@code x} nearest to {@code ref}. */
    public int lapOffset(int x, int ref) {
        return this.nearestBlock(x, ref) - x;
    }

    /** The image of {@code pos} nearest to the block containing {@code ref}. */
    public BlockPos nearest(BlockPos pos, Vec3 ref) {
        int x = this.nearestBlock(pos.getX(), (int) Math.floor(ref.x));
        int z = this.nearestBlock(pos.getZ(), (int) Math.floor(ref.z));
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }

    public Vec3 nearest(Vec3 pos, Vec3 ref) {
        double x = this.nearest(pos.x, ref.x);
        double z = this.nearest(pos.z, ref.z);
        return x == pos.x && z == pos.z ? pos : new Vec3(x, pos.y, z);
    }

    public ChunkPos nearest(ChunkPos pos, int refX, int refZ) {
        int x = this.nearestChunk(pos.x, refX);
        int z = this.nearestChunk(pos.z, refZ);
        return x == pos.x && z == pos.z ? pos : new ChunkPos(x, z);
    }

    @Override
    public String toString() {
        return this.enabled() ? "Wrap[" + this.period + "]" : "Wrap[none]";
    }
}
