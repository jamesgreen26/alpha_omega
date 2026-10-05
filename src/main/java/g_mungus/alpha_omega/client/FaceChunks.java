package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The chunks a client holds in a cube world (design §6.2): one array per face, covering that face's footprint and
 * indexed by chunk position. A lookup is a few subtractions and an array read, with no hashing, boxing or locks;
 * section builds on worker threads read it too. Chunks outside every footprint (the empty sky between faces) go in a
 * small map.
 */
public final class FaceChunks {

    public final CubeGeometry geometry;
    private final int[] minX = new int[6];
    private final int minZ;
    private final int width;
    @SuppressWarnings("unchecked")
    private final AtomicReferenceArray<LevelChunk>[] faces = new AtomicReferenceArray[6];
    private final Map<Long, LevelChunk> outside = new ConcurrentHashMap<>();
    private final AtomicInteger count = new AtomicInteger();

    public FaceChunks(CubeGeometry geometry) {
        this.geometry = geometry;
        this.minZ = Math.floorDiv(geometry.centerZ() - geometry.footprint, 16);
        this.width = Math.floorDiv(geometry.centerZ() + geometry.footprint - 1, 16) - this.minZ + 1;
        for (CubeFace face : CubeFace.values()) {
            this.minX[face.slot()] = Math.floorDiv(geometry.centerX(face) - geometry.footprint, 16);
            this.faces[face.slot()] = new AtomicReferenceArray<>(this.width * this.width);
        }
    }

    /** The array slot of a chunk, as {@code face * width² + index}, or -1 outside every footprint. */
    private int slot(int x, int z) {
        CubeFace face = this.geometry.faceAtChunk(x, z);
        if (face == null) return -1;
        int dx = x - this.minX[face.slot()];
        int dz = z - this.minZ;
        if (dx < 0 || dx >= this.width || dz < 0 || dz >= this.width) return -1;
        return face.slot() * this.width * this.width + dx * this.width + dz;
    }

    private AtomicReferenceArray<LevelChunk> array(int slot) {
        return this.faces[slot / (this.width * this.width)];
    }

    private static Long key(int x, int z) {
        // Spread the bits: a packed position's own hash is x ^ z, which collides across a whole face.
        return HashCommon.mix(((long) x & 0xFFFFFFFFL) | ((long) z & 0xFFFFFFFFL) << 32);
    }

    @Nullable
    public LevelChunk get(int x, int z) {
        int slot = this.slot(x, z);
        if (slot < 0) return this.outside.get(key(x, z));
        return this.array(slot).get(slot % (this.width * this.width));
    }

    public void put(int x, int z, LevelChunk chunk) {
        int slot = this.slot(x, z);
        LevelChunk old = slot < 0 ? this.outside.put(key(x, z), chunk) : this.array(slot).getAndSet(slot % (this.width * this.width), chunk);
        if (old == null) this.count.incrementAndGet();
    }

    @Nullable
    public LevelChunk remove(int x, int z) {
        int slot = this.slot(x, z);
        LevelChunk old = slot < 0 ? this.outside.remove(key(x, z)) : this.array(slot).getAndSet(slot % (this.width * this.width), null);
        if (old != null) this.count.decrementAndGet();
        return old;
    }

    public int size() {
        return this.count.get();
    }
}
