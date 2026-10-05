package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Which chunks hold copies of a chunk's cells, and the motion to each (orbifold plan phase 4, "Links"). Every motion of
 * the group is chunk-aligned, so a chunk's cells all have their copies in the same chunks: the links are per chunk,
 * computed once, and cached on the chunk ({@link BandChunk#alpha_omega$links()}). Chunks away from the edges get
 * {@link #NONE}, which the write path tests with one field read.
 *
 * <p>A band or skirt chunk's first link is its source (in the tile); the others are the source's other copies. A tile
 * chunk's links are its copies. Each link caches the loaded chunk at the other end (refreshed once that chunk unloads)
 * and a scratch position, so a mirrored write allocates nothing.
 */
public final class CopyLinks {

    public static final CopyLinks NONE = new CopyLinks(false, new Link[0]);

    /** Whether the chunk is outside the tile (band or skirt): it owns nothing unless it claims. */
    public final boolean band;
    public final Link[] links;

    private CopyLinks(boolean band, Link[] links) {
        this.band = band;
        this.links = links;
    }

    public boolean isEmpty() {
        return this.links.length == 0;
    }

    /** The link to the source, for a band or skirt chunk; null for a tile chunk. */
    @Nullable
    public Link source() {
        return this.band ? this.links[0] : null;
    }

    public static CopyLinks compute(OrbifoldGeometry geometry, int chunkX, int chunkZ) {
        if (!geometry.inFootprintChunk(chunkX, chunkZ)) return NONE;
        List<Link> links = new ArrayList<>(3);
        int bx = chunkX << 4, bz = chunkZ << 4;
        if (geometry.isTileChunk(chunkX, chunkZ)) {
            for (OrbifoldGeometry.Cell copy : geometry.copiesChunk(chunkX, chunkZ)) {
                links.add(new Link(copy.x(), copy.z(), geometry.between(bx, bz, copy.x() << 4, copy.z() << 4), false));
            }
            return links.isEmpty() ? NONE : new CopyLinks(false, links.toArray(Link[]::new));
        }
        OrbifoldGeometry.Cell source = geometry.canonChunk(chunkX, chunkZ);
        links.add(new Link(source.x(), source.z(), source.frame(), true));
        for (OrbifoldGeometry.Cell copy : geometry.copiesChunk(source.x(), source.z())) {
            if (copy.x() == chunkX && copy.z() == chunkZ) continue;
            links.add(new Link(copy.x(), copy.z(), geometry.between(bx, bz, copy.x() << 4, copy.z() << 4), false));
        }
        return new CopyLinks(true, links.toArray(Link[]::new));
    }

    /** One linked chunk: where it is and the motion from this chunk's cells to their copies there. */
    public static final class Link {
        public final int chunkX;
        public final int chunkZ;
        public final long key;
        public final Motion motion;
        /** Whether the motion is a half turn (states, directions and local coordinates turn). */
        public final boolean turned;
        /** Whether the linked chunk is the tile copy (this chunk's source). */
        public final boolean toTile;
        @Nullable
        private LevelChunk cached;
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

        Link(int chunkX, int chunkZ, Motion motion, boolean toTile) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.key = ChunkPos.asLong(chunkX, chunkZ);
            this.motion = motion;
            this.turned = motion.turned();
            this.toTile = toTile;
        }

        public int x(int x) {
            return this.motion.cellX(x);
        }

        public int z(int z) {
            return this.motion.cellZ(z);
        }

        /** Local (0..15) coordinate of the copy of local coordinate {@code l}: the same, or {@code 15 − l} under a half turn. */
        public int local(int l) {
            return this.turned ? 15 - l : l;
        }

        /** The copy of {@code pos}, as a new position. */
        public BlockPos map(BlockPos pos) {
            return new BlockPos(this.x(pos.getX()), pos.getY(), this.z(pos.getZ()));
        }

        /** The copy of {@code pos} in this link's scratch position: valid until the next call. Main thread only. */
        public BlockPos scratch(BlockPos pos) {
            return this.scratch.set(this.x(pos.getX()), pos.getY(), this.z(pos.getZ()));
        }

        /** The cell this link's copy at {@code pos} comes from: the inverse motion. */
        public BlockPos back(BlockPos pos) {
            Motion inverse = this.motion.inverse();
            return new BlockPos(inverse.cellX(pos.getX()), pos.getY(), inverse.cellZ(pos.getZ()));
        }

        /** The loaded chunk at the other end, or null. Main thread. */
        @Nullable
        public LevelChunk chunk(Level level) {
            LevelChunk chunk = this.cached;
            if (chunk != null && ((BandChunk) chunk).alpha_omega$inLevel()) return chunk;
            chunk = level instanceof ServerLevel server ? server.getChunkSource().getChunkNow(this.chunkX, this.chunkZ) : null;
            this.cached = chunk;
            return chunk;
        }

        /** Forgets the cached chunk if it is {@code chunk} (it is unloading). */
        public void forget(LevelChunk chunk) {
            if (this.cached == chunk) this.cached = null;
        }

        @Override
        public String toString() {
            return "link to [" + this.chunkX + ", " + this.chunkZ + "] by " + this.motion;
        }
    }
}
