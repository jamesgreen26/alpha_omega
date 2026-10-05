package g_mungus.alpha_omega.cube;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Where the cube's faces are and how they fit together (design §1). Pure arithmetic, shared by server and client.
 *
 * <p><b>Coordinates.</b> Each face is stored upright around its centre {@code (centerX(f), centerZ())}. A block's
 * face-local cell is {@code (a, n, b) = (x − centerX, R + y − planeY, z − centerZ)}: it covers {@code [a, a+1] ×
 * [n, n+1] × [b, b+1]} in the face's local frame, whose origin is the cube centre and whose {@code n} axis is the
 * face normal. The cube space position is the face's rotation applied to that. The face plane lies under block
 * {@code planeY}, so the base square's top layer sits just above the cube's surface.
 *
 * <p><b>Ownership.</b> A point belongs to the face whose axis dominates its cube position. Cells whose centres tie
 * on two or three axes form the barrier, a one-block diagonal staircase shared by both faces. Cell centres are
 * worked in doubled integer coordinates (all odd), so ties are exact and every face agrees.
 */
public final class CubeGeometry {

    /** {@link #cellOwner} for a barrier cell. */
    public static final int BARRIER = -1;
    /** Margin between the largest view distance (32) and the next face's storage, in chunks. */
    private static final int VIEW_MARGIN_CHUNKS = 34;

    public final CubeSettings settings;
    /** Face width in chunks and half-width in blocks. */
    public final int faceChunks;
    public final int radius;
    /** Storage y of the face plane (the bottom of the first block above it). */
    public final int planeY;
    /** Build limits: lowest block y and one past the highest. */
    public final int minY;
    public final int maxY;
    /** Storage half-width of a face's footprint (owned or barrier cells up to the build limit), in blocks. */
    public final int footprint;
    /** Distance between neighbouring face centres in storage, in chunks. */
    public final int spacingChunks;

    private final int[] centerX = new int[6];
    private final int centerZ;

    public CubeGeometry(CubeSettings settings, int planeY, int minY, int maxY) {
        this.settings = settings;
        this.faceChunks = settings.faceChunks();
        this.radius = 8 * this.faceChunks;
        this.planeY = planeY;
        this.minY = minY;
        this.maxY = maxY;
        // The widest owned layer is the top one: n = R + (maxY - 1 - planeY) reaches |a| <= n, plus a barrier cell.
        this.footprint = Math.max(this.radius, this.radius + maxY - planeY);
        int footprintChunks = Math.ceilDiv(this.footprint, 16) + 1;
        this.spacingChunks = roundUp(2 * footprintChunks + 2 * VIEW_MARGIN_CHUNKS, 16);
        // Face f's base square is chunks [f·S − ⌊W/2⌋, f·S − ⌊W/2⌋ + W) by [−⌊W/2⌋, −⌊W/2⌋ + W).
        int firstChunk = -(this.faceChunks / 2);
        for (CubeFace face : CubeFace.values()) {
            this.centerX[face.slot()] = 16 * (face.slot() * this.spacingChunks + firstChunk) + this.radius;
        }
        this.centerZ = 16 * firstChunk + this.radius;
    }

    private static int roundUp(int value, int step) {
        return Math.ceilDiv(value, step) * step;
    }

    public int centerX(CubeFace face) {
        return this.centerX[face.slot()];
    }

    public int centerZ() {
        return this.centerZ;
    }

    /** First chunk x and z of a face's base square. */
    public int minChunkX(CubeFace face) {
        return (this.centerX(face) - this.radius) >> 4;
    }

    public int minChunkZ() {
        return (this.centerZ - this.radius) >> 4;
    }

    // ---- Which face's storage ----

    /** The face whose storage area holds this block column, or null between faces. */
    @Nullable
    public CubeFace faceAt(int blockX, int blockZ) {
        int half = 8 * this.spacingChunks;
        if (blockZ < this.centerZ - half || blockZ >= this.centerZ + half) return null;
        int slot = Math.floorDiv(blockX - this.centerX[0] + half, 2 * half);
        return slot >= 0 && slot < 6 ? CubeFace.bySlot(slot) : null;
    }

    @Nullable
    public CubeFace faceAt(double x, double z) {
        return this.faceAt((int) Math.floor(x), (int) Math.floor(z));
    }

    @Nullable
    public CubeFace faceAtChunk(int chunkX, int chunkZ) {
        return this.faceAt(chunkX * 16 + 8, chunkZ * 16 + 8);
    }

    /** Whether a chunk column could hold owned or barrier cells of its face: it overlaps the footprint. */
    public boolean inFootprint(int chunkX, int chunkZ) {
        CubeFace face = this.faceAtChunk(chunkX, chunkZ);
        if (face == null) return false;
        int x0 = chunkX * 16 - this.centerX(face);
        int z0 = chunkZ * 16 - this.centerZ;
        return x0 < this.footprint && x0 + 16 > -this.footprint && z0 < this.footprint && z0 + 16 > -this.footprint;
    }

    // ---- Ownership ----

    /** Doubled cell centre in cube space: x, y, z. */
    private long[] cubeCell2(CubeFace face, int x, int y, int z) {
        long u = 2L * (x - this.centerX(face)) + 1;
        long n = 2L * (this.radius + y - this.planeY) + 1;
        long v = 2L * (z - this.centerZ) + 1;
        return new long[] {face.toCubeX(u, n, v), face.toCubeY(u, n, v), face.toCubeZ(u, n, v)};
    }

    /** The face that owns a cube-space point (doubled or not), or {@link #BARRIER} where the dominant axes tie. */
    private static int owner(long x, long y, long z) {
        long ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax > ay && ax > az) return CubeFace.byNormal(0, x > 0 ? 1 : -1).slot();
        if (ay > ax && ay > az) return CubeFace.byNormal(1, y > 0 ? 1 : -1).slot();
        if (az > ax && az > ay) return CubeFace.byNormal(2, z > 0 ? 1 : -1).slot();
        return BARRIER;
    }

    /** Slot of the face owning block {@code (x, y, z)} of {@code face}'s storage, or {@link #BARRIER}. */
    public int cellOwner(CubeFace face, int x, int y, int z) {
        long[] c = this.cubeCell2(face, x, y, z);
        return owner(c[0], c[1], c[2]);
    }

    /**
     * Every face storing a barrier cell of {@code face}'s storage (two along an edge's diagonal, three along the lines
     * from the centre through the cube's corners), in slot order; empty if the cell is not on {@code face}'s barrier.
     */
    public List<CubeFace> barrierFaces(CubeFace face, int x, int y, int z) {
        long[] c = this.cubeCell2(face, x, y, z);
        long own = Math.abs(c[face.axis]);
        if (c[face.axis] * face.sign <= 0) return List.of();
        List<CubeFace> faces = new ArrayList<>(3);
        for (int axis = 0; axis < 3; axis++) {
            long value = Math.abs(c[axis]);
            if (value > own) return List.of();
            if (value == own) faces.add(CubeFace.byNormal(axis, c[axis] > 0 ? 1 : -1));
        }
        if (faces.size() < 2) return List.of();
        faces.sort(java.util.Comparator.comparingInt(CubeFace::slot));
        return faces;
    }

    /**
     * The other face a barrier cell of {@code face}'s storage is shared with, or null where three faces meet there
     * (along the lines from the cube's centre through its corners) or the cell is not on {@code face}'s barrier.
     */
    @Nullable
    public CubeFace barrierPartner(CubeFace face, int x, int y, int z) {
        long[] c = this.cubeCell2(face, x, y, z);
        long own = Math.abs(c[face.axis]);
        CubeFace partner = null;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == face.axis) continue;
            if (Math.abs(c[axis]) == own) {
                if (partner != null) return null;
                partner = CubeFace.byNormal(axis, c[axis] > 0 ? 1 : -1);
            } else if (Math.abs(c[axis]) > own) {
                return null;
            }
        }
        return c[face.axis] * face.sign > 0 ? partner : null;
    }

    public boolean isOwned(CubeFace face, int x, int y, int z) {
        return this.cellOwner(face, x, y, z) == face.slot();
    }

    public boolean isBarrier(CubeFace face, int x, int y, int z) {
        return this.cellOwner(face, x, y, z) == BARRIER;
    }

    /**
     * The y of the one barrier cell in a column of {@code face}'s storage: cells above it are owned, cells below
     * belong to other faces. May lie outside the build limits.
     */
    public int barrierY(CubeFace face, int x, int z) {
        long a = Math.abs(2L * (x - this.centerX(face)) + 1);
        long b = Math.abs(2L * (z - this.centerZ) + 1);
        return (int) ((Math.max(a, b) - 1) / 2) - this.radius + this.planeY;
    }

    /** How many foreign cells under each column's barrier cell collide like the neighbour's own (design: neighbour collision). */
    public static final int BAND = 16;

    /** A cell of another face's storage: the one a band cell takes its collision from. */
    public record Cell(CubeFace face, int x, int y, int z) {
    }

    /**
     * For a cell of {@code face}'s storage in the band ({@link #BAND} cells under its column's barrier cell), the same
     * physical cell in the storage of a face that has it: the face owning it, or for a barrier cell between other
     * faces, the first of them in slot order that is not the opposite face. Null outside the band, or where only the
     * opposite face has it.
     */
    @Nullable
    public Cell bandSource(CubeFace face, int x, int y, int z) {
        int depth = this.barrierY(face, x, z) - y;
        if (depth < 1 || depth > BAND) return null;
        long[] c = this.cubeCell2(face, x, y, z);
        int owner = owner(c[0], c[1], c[2]);
        CubeFace source = null;
        if (owner == BARRIER) {
            long most = Math.max(Math.abs(c[0]), Math.max(Math.abs(c[1]), Math.abs(c[2])));
            for (CubeFace candidate : CubeFace.values()) {
                if (candidate != face && candidate != face.opposite() && c[candidate.axis] * candidate.sign == most) {
                    source = candidate;
                    break;
                }
            }
        } else if (owner != face.slot()) {
            source = CubeFace.bySlot(owner);
        }
        if (source == null || source == face.opposite()) return null;
        int[] cell = this.transformBlock(face, source, x, y, z);
        return new Cell(source, cell[0], cell[1], cell[2]);
    }

    /**
     * Whether a box of blocks (inclusive bounds) lies wholly in {@code face}'s owned region, at least {@code margin}
     * blocks from the barrier sideways and below. The owned region is convex, so its corners decide.
     */
    public boolean boxOwned(CubeFace face, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int margin) {
        for (int x : new int[] {minX - margin, maxX + margin}) {
            for (int z : new int[] {minZ - margin, maxZ + margin}) {
                if (minY - margin <= this.barrierY(face, x, z)) return false;
            }
        }
        return maxY < this.maxY && this.faceAt(minX, minZ) == face && this.faceAt(maxX, maxZ) == face;
    }

    /** The face owning a point of {@code face}'s storage; ties (measure zero) go to {@code face}. */
    public CubeFace ownerAt(CubeFace face, double x, double y, double z) {
        double[] c = this.toCube(face, x, y, z);
        double ax = Math.abs(c[0]), ay = Math.abs(c[1]), az = Math.abs(c[2]);
        double max = Math.max(ax, Math.max(ay, az));
        if (Math.abs(c[face.axis]) == max && c[face.axis] * face.sign > 0) return face;
        int axis = ax == max ? 0 : ay == max ? 1 : 2;
        return CubeFace.byNormal(axis, c[axis] > 0 ? 1 : -1);
    }

    /**
     * How far a storage point of {@code face} lies past the diagonal into {@code other}'s region (positive) or short
     * of it (negative), in blocks across the diagonal plane. Only meaningful for neighbours.
     */
    public double depthInto(CubeFace face, CubeFace other, double x, double y, double z) {
        double[] c = this.toCube(face, x, y, z);
        return (c[other.axis] * other.sign - c[face.axis] * face.sign) / Math.sqrt(2.0);
    }

    /**
     * Where "flat" world generation samples a column (design §4.2): the point on the cube's surface above it, its
     * face coordinates clamped to the face's square. On a face this is a flat slice; over an edge it bends; in the
     * overhang the edge's column carries on outward, so ground on both sides meets at the ridge. Null between faces.
     */
    @Nullable
    public double[] surfacePoint(double x, double z) {
        CubeFace face = this.faceAt(x, z);
        if (face == null) return null;
        double u = Math.max(-this.radius, Math.min(this.radius, x - this.centerX(face)));
        double v = Math.max(-this.radius, Math.min(this.radius, z - this.centerZ));
        return face.toCube(u, this.radius, v);
    }

    // ---- Transforms ----

    /** Cube-space position of a storage point of {@code face}. */
    public double[] toCube(CubeFace face, double x, double y, double z) {
        return face.toCube(x - this.centerX(face), this.radius + y - this.planeY, z - this.centerZ);
    }

    /** Storage position on {@code face} of a cube-space point. */
    public double[] fromCube(CubeFace face, double[] cube) {
        double[] l = face.toLocal(cube[0], cube[1], cube[2]);
        return new double[] {l[0] + this.centerX(face), l[1] - this.radius + this.planeY, l[2] + this.centerZ};
    }

    /** {@code T}: the same cube point in another face's storage. */
    public double[] transform(CubeFace from, CubeFace to, double x, double y, double z) {
        return this.fromCube(to, this.toCube(from, x, y, z));
    }

    /**
     * The corner of a block cell with the smallest cube coordinates, as a point of {@code face}'s storage. Every face
     * storing (or neighbouring) the cell names the same physical point, so terrain sampled there agrees across faces.
     */
    public int[] cubeMinCorner(CubeFace face, int x, int y, int z) {
        int[] offset = new int[3];
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                if (face.m(i, j) < 0) offset[j] = 1;
            }
        }
        return new int[] {x + offset[0], y + offset[1], z + offset[2]};
    }

    /** {@link #transform} for a point on the block grid (a cell corner): exact. */
    public int[] transformCorner(CubeFace from, CubeFace to, int x, int y, int z) {
        double[] p = this.transform(from, to, x, y, z);
        return new int[] {(int) Math.round(p[0]), (int) Math.round(p[1]), (int) Math.round(p[2])};
    }

    /** {@link #transform} for a block cell: exact. */
    public int[] transformBlock(CubeFace from, CubeFace to, int x, int y, int z) {
        long[] c = this.cubeCell2(from, x, y, z);
        long[] l = to.toLocal(c[0], c[1], c[2]);
        return new int[] {
            (int) Math.floorDiv(l[0] - 1, 2) + this.centerX(to),
            (int) Math.floorDiv(l[1] - 1, 2) - this.radius + this.planeY,
            (int) Math.floorDiv(l[2] - 1, 2) + this.centerZ};
    }

    /** A direction (velocity, look vector) of {@code from}'s storage, in {@code to}'s storage axes. */
    public static double[] rotate(CubeFace from, CubeFace to, double x, double y, double z) {
        double[] c = from.toCube(x, y, z);
        return to.toLocal(c[0], c[1], c[2]);
    }

    /**
     * A direction of {@code from}'s storage turned with the unfold across the shared edge, in {@code to}'s storage
     * axes: up stays up, and heading toward the edge becomes heading away from it on {@code to}. How upright things
     * (players, mobs) carry their velocity and gaze over an edge. Neighbours only.
     */
    public static double[] rotateUpright(CubeFace from, CubeFace to, double x, double y, double z) {
        if (!from.isNeighbour(to)) throw new IllegalArgumentException(from + " and " + to + " do not share an edge");
        double[] p = from.toCube(x, y, z);
        double da = p[from.axis];
        double db = p[to.axis];
        double[] q = p.clone();
        q[to.axis] = da * from.sign * to.sign;
        q[from.axis] = -db * to.sign * from.sign;
        return to.toLocal(q[0], q[1], q[2]);
    }

    /**
     * {@code U}: a storage point of {@code from} unfolded across the shared edge into {@code to}'s storage, as if
     * {@code to}'s surface carried on flat past that edge. A point {@code s} blocks short of the edge at height
     * {@code h} on {@code from} lands {@code s} blocks past the edge at height {@code h} on {@code to}. Neighbours only.
     */
    public double[] unfold(CubeFace from, CubeFace to, double x, double y, double z) {
        if (!from.isNeighbour(to)) throw new IllegalArgumentException(from + " and " + to + " do not share an edge");
        double[] p = this.toCube(from, x, y, z);
        // Rotate a quarter turn about the edge: from's normal goes to to's, to's goes to minus from's.
        double edgeA = this.radius * from.sign;
        double edgeB = this.radius * to.sign;
        double da = p[from.axis] - edgeA;
        double db = p[to.axis] - edgeB;
        double[] q = p.clone();
        q[to.axis] = edgeB + da * from.sign * to.sign;
        q[from.axis] = edgeA - db * to.sign * from.sign;
        return this.fromCube(to, q);
    }

    @Override
    public String toString() {
        return "CubeGeometry[W=" + this.faceChunks + " chunks, plane y=" + this.planeY + ", spacing " + this.spacingChunks + " chunks]";
    }
}
