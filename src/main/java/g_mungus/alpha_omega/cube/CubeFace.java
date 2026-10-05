package g_mungus.alpha_omega.cube;

/**
 * The six faces of the cube, named by their outward normal in cube space (whose axes are named like Minecraft's).
 * The ordinal is the face's storage slot: {@link #UP} is slot 0, stored around the world origin, and holds spawn.
 *
 * <p>Each face's rotation takes its local frame (storage +X, +Y (up), +Z) to cube space. All six are proper
 * rotations, so nothing is mirrored, and their middle column is the face normal.
 */
public enum CubeFace {
    UP(new int[] {
        1, 0, 0,
        0, 1, 0,
        0, 0, 1}),
    EAST(new int[] {
        0, 1, 0,
        -1, 0, 0,
        0, 0, 1}),
    SOUTH(new int[] {
        1, 0, 0,
        0, 0, -1,
        0, 1, 0}),
    WEST(new int[] {
        0, -1, 0,
        1, 0, 0,
        0, 0, 1}),
    NORTH(new int[] {
        1, 0, 0,
        0, 0, 1,
        0, -1, 0}),
    DOWN(new int[] {
        1, 0, 0,
        0, -1, 0,
        0, 0, -1});

    private static final CubeFace[] VALUES = values();

    /** Row-major local-to-cube rotation. */
    private final int[] m;
    /** Cube axis (0 x, 1 y, 2 z) of the normal, and its sign. */
    public final int axis;
    public final int sign;

    CubeFace(int[] m) {
        this.m = m;
        int axis = 0;
        for (int i = 0; i < 3; i++) {
            if (m[3 * i + 1] != 0) axis = i;
        }
        this.axis = axis;
        this.sign = m[3 * axis + 1];
    }

    public static CubeFace bySlot(int slot) {
        return VALUES[slot];
    }

    public int slot() {
        return this.ordinal();
    }

    /** The face whose normal is the given cube axis and sign. */
    public static CubeFace byNormal(int axis, int sign) {
        for (CubeFace face : VALUES) {
            if (face.axis == axis && face.sign == sign) return face;
        }
        throw new IllegalArgumentException("axis " + axis + " sign " + sign);
    }

    public CubeFace opposite() {
        return byNormal(this.axis, -this.sign);
    }

    public boolean isNeighbour(CubeFace other) {
        return other.axis != this.axis;
    }

    /** Matrix entry: row {@code i} (cube axis), column {@code j} (local axis). */
    public int m(int i, int j) {
        return this.m[3 * i + j];
    }

    /** Normal as a cube-space vector component. */
    public int normal(int i) {
        return this.m(i, 1);
    }

    /** The direction of {@code other}'s normal in this face's storage axes: toward that face, or up for itself. */
    public double[] toward(CubeFace other) {
        return this.toLocal((double) other.normal(0), other.normal(1), other.normal(2));
    }

    // Local to cube: c = M l. Cube to local: l = Mᵀ c. Integer forms for exact cell maps.

    public long toCubeX(long u, long up, long v) {
        return this.m[0] * u + this.m[1] * up + this.m[2] * v;
    }

    public long toCubeY(long u, long up, long v) {
        return this.m[3] * u + this.m[4] * up + this.m[5] * v;
    }

    public long toCubeZ(long u, long up, long v) {
        return this.m[6] * u + this.m[7] * up + this.m[8] * v;
    }

    public double[] toCube(double u, double up, double v) {
        return new double[] {
            this.m[0] * u + this.m[1] * up + this.m[2] * v,
            this.m[3] * u + this.m[4] * up + this.m[5] * v,
            this.m[6] * u + this.m[7] * up + this.m[8] * v};
    }

    public double[] toLocal(double x, double y, double z) {
        return new double[] {
            this.m[0] * x + this.m[3] * y + this.m[6] * z,
            this.m[1] * x + this.m[4] * y + this.m[7] * z,
            this.m[2] * x + this.m[5] * y + this.m[8] * z};
    }

    public long[] toLocal(long x, long y, long z) {
        return new long[] {
            this.m[0] * x + this.m[3] * y + this.m[6] * z,
            this.m[1] * x + this.m[4] * y + this.m[7] * z,
            this.m[2] * x + this.m[5] * y + this.m[8] * z};
    }
}
