package g_mungus.alpha_omega.orbifold;

/**
 * An element of the world's group {@code Γ}: a motion of the plane that keeps the block grid, with {@code y} unchanged.
 * It is a translation {@code p ↦ p + t} or a half turn {@code p ↦ t − p} (about the point {@code t/2}); {@code Γ} has
 * no other kinds of element. Pure: no Minecraft types ({@link Transform} applies one to them).
 *
 * <p>Points (positions, box corners) map by those formulas. A block cell is named by its minimum corner, and maps to
 * the cell it covers after the motion: under a half turn, cell {@code (x, z)} goes to {@code (tx − 1 − x, tz − 1 − z)}.
 * Chunks map the same way when {@code t} is a multiple of 16, as it is for every element of {@code Γ}.
 */
public record Motion(boolean turned, int tx, int tz) {

    public static final Motion IDENTITY = new Motion(false, 0, 0);

    /** {@code p ↦ p + (dx, dz)}. */
    public static Motion translation(int dx, int dz) {
        return new Motion(false, dx, dz);
    }

    /** {@code p ↦ (tx, tz) − p}: a half turn about {@code (tx/2, tz/2)}. */
    public static Motion halfTurn(int tx, int tz) {
        return new Motion(true, tx, tz);
    }

    public boolean isIdentity() {
        return this.equals(IDENTITY);
    }

    /** The turn about Y, in degrees: 0 or 180. */
    public int turn() {
        return this.turned ? 180 : 0;
    }

    /** This motion, then {@code next}: {@code next ∘ this}. */
    public Motion then(Motion next) {
        int sign = next.turned ? -1 : 1;
        return new Motion(this.turned != next.turned, sign * this.tx + next.tx, sign * this.tz + next.tz);
    }

    public Motion inverse() {
        return this.turned ? this : new Motion(false, -this.tx, -this.tz);
    }

    // ---- Block cells, by minimum corner ----

    public int cellX(int x) {
        return this.turned ? this.tx - 1 - x : x + this.tx;
    }

    public int cellZ(int z) {
        return this.turned ? this.tz - 1 - z : z + this.tz;
    }

    // ---- Chunks ----

    /** Whether this motion maps chunks to whole chunks: its translation is a multiple of 16 on both axes. */
    public boolean chunkAligned() {
        return (this.tx & 15) == 0 && (this.tz & 15) == 0;
    }

    public int chunkX(int chunkX) {
        return this.turned ? (this.tx >> 4) - 1 - chunkX : chunkX + (this.tx >> 4);
    }

    public int chunkZ(int chunkZ) {
        return this.turned ? (this.tz >> 4) - 1 - chunkZ : chunkZ + (this.tz >> 4);
    }

    // ---- Points and directions ----

    public double pointX(double x) {
        return this.turned ? this.tx - x : x + this.tx;
    }

    public double pointZ(double z) {
        return this.turned ? this.tz - z : z + this.tz;
    }

    /** A direction's (velocity's) x component: turned with the motion, not moved. */
    public double vectorX(double x) {
        return this.turned ? -x : x;
    }

    public double vectorZ(double z) {
        return this.turned ? -z : z;
    }

    /** A yaw in degrees: 180 more under a turn, so it changes by less than a full turn and interpolation does not spin. */
    public float yaw(float yaw) {
        return this.turned ? yaw + 180.0F : yaw;
    }

    @Override
    public String toString() {
        if (this.isIdentity()) return "id";
        return this.turned ? "turn(" + this.tx + ", " + this.tz + ")" : "shift(" + this.tx + ", " + this.tz + ")";
    }
}
