package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The chunks a client holds over a rectangle of chunks (the storage footprint), in one array indexed by chunk
 * position. A lookup is a few subtractions and an array read, with no hashing, boxing or locks; section builds on
 * worker threads read it too.
 */
public final class FaceChunks {

    public final int minX;
    public final int minZ;
    public final int sizeX;
    public final int sizeZ;
    private final AtomicReferenceArray<LevelChunk> chunks;
    private final AtomicInteger count = new AtomicInteger();

    public FaceChunks(int minX, int minZ, int sizeX, int sizeZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.chunks = new AtomicReferenceArray<>(sizeX * sizeZ);
    }

    /**
     * The store a client keeps for a level's footprint, or null to leave every chunk to vanilla. Null until phase 6
     * (image views), which needs the whole footprint on the client.
     */
    @Nullable
    public static FaceChunks forGeometry(OrbifoldGeometry geometry) {
        return null;
    }

    public boolean contains(int x, int z) {
        int dx = x - this.minX, dz = z - this.minZ;
        return dx >= 0 && dx < this.sizeX && dz >= 0 && dz < this.sizeZ;
    }

    private int index(int x, int z) {
        return (x - this.minX) * this.sizeZ + (z - this.minZ);
    }

    @Nullable
    public LevelChunk get(int x, int z) {
        return this.contains(x, z) ? this.chunks.get(this.index(x, z)) : null;
    }

    /** Stores a chunk of the rectangle ({@link #contains}). */
    public void put(int x, int z, LevelChunk chunk) {
        if (this.chunks.getAndSet(this.index(x, z), chunk) == null) this.count.incrementAndGet();
    }

    @Nullable
    public LevelChunk remove(int x, int z) {
        if (!this.contains(x, z)) return null;
        LevelChunk old = this.chunks.getAndSet(this.index(x, z), null);
        if (old != null) this.count.decrementAndGet();
        return old;
    }

    public int size() {
        return this.count.get();
    }
}
