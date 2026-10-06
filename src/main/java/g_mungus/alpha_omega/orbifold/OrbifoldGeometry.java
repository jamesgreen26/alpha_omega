package g_mungus.alpha_omega.orbifold;

import java.util.ArrayList;
import java.util.List;

/**
 * The world's shape: the plane modulo the group {@code Γ} (p2 on a hexagonal lattice), stored as one tile with a band
 * and a skirt round it ({@code orbifold-implementation.md} §2, {@code alpha-omega-best-wrapping-plan.md} §4). Pure:
 * no Minecraft types ({@link Transform} applies a {@link Motion} to them).
 *
 * <p><b>Conventions.</b>
 * <ul>
 * <li>Coordinates are block {@code x} (east) and {@code z} (south); {@code y} plays no part.</li>
 * <li>A block cell is named by its minimum corner {@code (x, z)}. A chunk is named by its chunk coordinates. Methods
 * taking {@code double}s are about points (positions); methods taking {@code int}s are about cells, or chunks where
 * the name says so.</li>
 * <li>The <b>tile</b> is {@code x ∈ [−a/2, a/2)}, {@code z ∈ [zN, zS)}: every place in the world exactly once. The
 * <b>band</b> is the cells outside it within {@link #band} blocks (Chebyshev), the <b>skirt</b> the next
 * {@link #SKIRT}, and the <b>footprint</b> is all three. Every boundary is a multiple of 16, so each chunk is wholly
 * tile, band, skirt or outside.</li>
 * <li>A band or skirt cell's <b>frame</b> is the element {@code g} of {@code Γ} that maps it to its <b>source</b>, the
 * tile cell holding the same block: {@code source = g(cell)}. It always maps band → source; its inverse maps a source
 * to that copy. A tile cell's frame is the identity.</li>
 * <li>{@code transform(g)} moves anything by {@code g}: cells by {@link Motion#cellX}, points by
 * {@link Motion#pointX}, and directions, yaw, states and boxes turned with it.</li>
 * </ul>
 *
 * <p>The answers for cells and chunks hold within the footprint; outside it, nothing is stored.
 */
public final class OrbifoldGeometry {

    public static final int MIN_BAND_CHUNKS = 2;
    public static final int MAX_BAND_CHUNKS = 16;
    public static final int DEFAULT_BAND_CHUNKS = 4;
    /** Width of the skirt past the band, in blocks: mirrored for light and meshing only. */
    public static final int SKIRT = 16;
    /** Interaction radius {@code R}: claims stop this far short of the band's edge ({@code C = H − R}). */
    public static final int INTERACTION_RADIUS = 32;

    /** A cone point: a fixed point of a half turn in {@code Γ}, on a fold row. */
    public record ConePoint(String name, int x, int z) {
    }

    /**
     * A cell paired with a frame: the frame is always that of the band (or skirt) cell in the pair, mapping it to its
     * source. From {@link #canon}: the source cell, with the frame of the cell asked about. From {@link #copies}: a
     * band cell, with its own frame.
     */
    public record Cell(int x, int z, Motion frame) {
    }

    /** The preset this geometry is: its lattice, north fold row and spawn. */
    public final OrbifoldSize size;
    public final int bandChunks;
    /** Lattice: {@code L1 = (a, 0)}, {@code L2 = (a/2, b)}. */
    public final int a;
    public final int b;
    /** Tile {@code x} bounds: {@code [minX, maxX)} = {@code [−a/2, a/2)}. */
    public final int minX;
    public final int maxX;
    /** North fold row {@code zN}: the tile's first row. */
    public final int northRow;
    /** South fold row {@code zS = zN + b/2}: one past the tile's last row. */
    public final int southRow;
    /** Band depth {@code H}, in blocks. */
    public final int band;
    /** Claim depth {@code C = H − R}, in blocks. */
    public final int claim;
    /** How far the footprint reaches past the tile: {@code H + 16}. */
    public final int reach;
    public final int spawnX;
    public final int spawnZ;

    /** {@code T+}: {@code (x, z) ↦ (x + a, z)}. */
    public final Motion east;
    /** {@code T−}: {@code (x, z) ↦ (x − a, z)}. */
    public final Motion west;
    /** {@code R_N}: the half turn about {@code N}, cells {@code (x, z) ↦ (−1 − x, 2zN − 1 − z)}. */
    public final Motion northFold;
    /** {@code R_S}: the half turn about {@code E}, cells {@code (x, z) ↦ (a/2 − 1 − x, 2zS − 1 − z)}. */
    public final Motion southFold;

    /** Every frame a footprint cell can have: the inverses of these map a source to its copies. */
    private final List<Motion> frames;

    public OrbifoldGeometry(OrbifoldSize size, int bandChunks) {
        if (bandChunks < MIN_BAND_CHUNKS || bandChunks > MAX_BAND_CHUNKS) {
            throw new IllegalArgumentException("Band must be " + MIN_BAND_CHUNKS + " to " + MAX_BAND_CHUNKS + " chunks: " + bandChunks);
        }
        this.size = size;
        this.bandChunks = bandChunks;
        this.a = size.a();
        this.b = size.b();
        this.minX = -this.a / 2;
        this.maxX = this.a / 2;
        this.northRow = size.northRow();
        this.southRow = size.northRow() + this.b / 2;
        this.band = 16 * bandChunks;
        this.claim = this.band - INTERACTION_RADIUS;
        this.reach = this.band + SKIRT;
        this.spawnX = size.spawnX();
        this.spawnZ = size.spawnZ();
        this.east = Motion.translation(this.a, 0);
        this.west = Motion.translation(-this.a, 0);
        this.northFold = Motion.halfTurn(0, 2 * this.northRow);
        this.southFold = Motion.halfTurn(this.a / 2, 2 * this.southRow);
        this.frames = List.of(this.east, this.west, this.northFold, this.southFold,
            this.northFold.then(this.east), this.northFold.then(this.west), this.southFold.then(this.east), this.southFold.then(this.west));
    }

    /** The generators near the tile: {@code T+}, {@code T−}, {@code R_N}, {@code R_S}. */
    public List<Motion> generators() {
        return List.of(this.east, this.west, this.northFold, this.southFold);
    }

    /** The four cone points: {@code N (0, zN)}, {@code F (a/2, zN)} (also {@code −a/2}), {@code E (a/4, zS)}, {@code W (−a/4, zS)}. */
    public List<ConePoint> conePoints() {
        return List.of(new ConePoint("N", 0, this.northRow), new ConePoint("F", this.a / 2, this.northRow),
            new ConePoint("E", this.a / 4, this.southRow), new ConePoint("W", -this.a / 4, this.southRow));
    }

    // ---- Cells ----

    /** How many cells a cell is past the tile, Chebyshev: 0 in the tile, 1 for the first cell past a seam. */
    public int cellDepth(int x, int z) {
        int dx = x < this.minX ? this.minX - x : x >= this.maxX ? x - this.maxX + 1 : 0;
        int dz = z < this.northRow ? this.northRow - z : z >= this.southRow ? z - this.southRow + 1 : 0;
        return Math.max(dx, dz);
    }

    public boolean isTile(int x, int z) {
        return x >= this.minX && x < this.maxX && z >= this.northRow && z < this.southRow;
    }

    public boolean isBand(int x, int z) {
        int depth = this.cellDepth(x, z);
        return depth >= 1 && depth <= this.band;
    }

    public boolean isSkirt(int x, int z) {
        int depth = this.cellDepth(x, z);
        return depth > this.band && depth <= this.reach;
    }

    public boolean inFootprint(int x, int z) {
        return this.cellDepth(x, z) <= this.reach;
    }

    /**
     * A cell's frame: the element taking it into the tile ({@link #canon}'s {@code g}). Fold first if past a fold row,
     * then wrap {@code x} by {@code T±}. The identity in the tile.
     */
    public Motion frame(int x, int z) {
        Motion g = Motion.IDENTITY;
        if (z < this.northRow) g = this.northFold;
        else if (z >= this.southRow) g = this.southFold;
        int fx = g.cellX(x);
        if (fx < this.minX) g = g.then(this.east);
        else if (fx >= this.maxX) g = g.then(this.west);
        return g;
    }

    /**
     * The tile cell holding the same block as {@code (x, z)}, with {@code (x, z)}'s frame: {@code source = frame(cell)}.
     * A tile cell is its own source, with the identity.
     */
    public Cell canon(int x, int z) {
        Motion g = this.frame(x, z);
        return new Cell(g.cellX(x), g.cellZ(z), g);
    }

    /**
     * A tile cell's copies: every band or skirt cell whose source it is, each with its own frame (which maps it back to
     * {@code (x, z)}). Empty for most cells; one near an edge; up to three near a corner or a cone point. Empty for a
     * cell outside the tile.
     */
    public List<Cell> copies(int x, int z) {
        if (!this.isTile(x, z)) return List.of();
        List<Cell> copies = new ArrayList<>(3);
        for (Motion g : this.frames) {
            Motion back = g.inverse();
            int cx = back.cellX(x), cz = back.cellZ(z);
            if (this.cellDepth(cx, cz) == 0 || !this.inFootprint(cx, cz) || !this.frame(cx, cz).equals(g)) continue;
            copies.add(new Cell(cx, cz, g));
        }
        return copies;
    }

    /**
     * The motion taking cell {@code a} to cell {@code b} when both are copies of the same source (or one is the source):
     * {@code frame(a)} then {@code frame(b)⁻¹}. The identity when {@code a = b}; between two band copies it goes through
     * their source. Exact on chunks too, since every frame is chunk-aligned.
     */
    public Motion between(int ax, int az, int bx, int bz) {
        return this.frame(ax, az).then(this.frame(bx, bz).inverse());
    }

    // ---- Chunks ----

    /** How many chunks a chunk is past the tile, Chebyshev: 0 in the tile. */
    public int chunkDepth(int chunkX, int chunkZ) {
        int minX = this.minX >> 4, maxX = this.maxX >> 4, minZ = this.northRow >> 4, maxZ = this.southRow >> 4;
        int dx = chunkX < minX ? minX - chunkX : chunkX >= maxX ? chunkX - maxX + 1 : 0;
        int dz = chunkZ < minZ ? minZ - chunkZ : chunkZ >= maxZ ? chunkZ - maxZ + 1 : 0;
        return Math.max(dx, dz);
    }

    public boolean isTileChunk(int chunkX, int chunkZ) {
        return this.isTile(chunkX << 4, chunkZ << 4);
    }

    public boolean isBandChunk(int chunkX, int chunkZ) {
        return this.isBand(chunkX << 4, chunkZ << 4);
    }

    public boolean isSkirtChunk(int chunkX, int chunkZ) {
        return this.isSkirt(chunkX << 4, chunkZ << 4);
    }

    public boolean inFootprintChunk(int chunkX, int chunkZ) {
        return this.inFootprint(chunkX << 4, chunkZ << 4);
    }

    /** The footprint's chunk bounds, as {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}}, all inclusive. */
    public int[] footprintChunks() {
        return new int[] {(this.minX - this.reach) >> 4, (this.northRow - this.reach) >> 4,
            (this.maxX + this.reach - 1) >> 4, (this.southRow + this.reach - 1) >> 4};
    }

    /** A chunk's frame: that of every cell in it. */
    public Motion frameChunk(int chunkX, int chunkZ) {
        return this.frame(chunkX << 4, chunkZ << 4);
    }

    /** The tile chunk holding the same blocks as a chunk, with the chunk's frame; coordinates are chunk coordinates. */
    public Cell canonChunk(int chunkX, int chunkZ) {
        Motion g = this.frameChunk(chunkX, chunkZ);
        return new Cell(g.chunkX(chunkX), g.chunkZ(chunkZ), g);
    }

    /** A tile chunk's copies in the band and skirt, each with its own frame; coordinates are chunk coordinates. */
    public List<Cell> copiesChunk(int chunkX, int chunkZ) {
        if (!this.isTileChunk(chunkX, chunkZ)) return List.of();
        List<Cell> copies = new ArrayList<>(3);
        for (Motion g : this.frames) {
            Motion back = g.inverse();
            int cx = back.chunkX(chunkX), cz = back.chunkZ(chunkZ);
            if (this.isTileChunk(cx, cz) || !this.inFootprintChunk(cx, cz) || !this.frameChunk(cx, cz).equals(g)) continue;
            copies.add(new Cell(cx, cz, g));
        }
        return copies;
    }

    // ---- Points ----

    /**
     * How far a point is past the tile's edge, Chebyshev, in blocks: positive past a seam (into the band), negative
     * inside the tile (minus the distance to the nearest seam).
     */
    public double seamDepth(double x, double z) {
        return Math.max(Math.max(this.minX - x, x - this.maxX), Math.max(this.northRow - z, z - this.southRow));
    }

    /** A point's frame: that of the cell it is in. */
    public Motion frame(double x, double z) {
        return this.frame((int) Math.floor(x), (int) Math.floor(z));
    }

    /** {@code g} applied to Minecraft's types. */
    public static Transform transform(Motion g) {
        return Transform.of(g);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "orbifold %s: tile x %d..%d, z %d..%d (%d x %d), band %d, claim %d, skirt %d",
            this.size, this.minX, this.maxX, this.northRow, this.southRow, this.a, this.b / 2, this.band, this.claim, SKIRT);
    }
}
