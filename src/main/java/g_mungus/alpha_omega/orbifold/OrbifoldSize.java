package g_mungus.alpha_omega.orbifold;

import java.util.List;
import java.util.Optional;

/**
 * A world size: the lattice {@code L1 = (a, 0)}, {@code L2 = (a/2, b)}, with its north fold row and spawn
 * ({@code alpha-omega-best-wrapping-plan.md} §4 "Sizes"). Only the presets exist.
 *
 * <p>The fold row and spawn are data, derived once as the wrapping plan derived them: walking south from {@code N}
 * along the meridian {@code x = 0}, spawn is where {@link g_mungus.alpha_omega.sky.HexOrbifoldProjection} reaches the
 * equator (0° 0°, heading 0), {@code 0.390913·b} blocks from {@code N}; the north row is minus that distance rounded to
 * a multiple of 128, and spawn is the row plus the distance, rounded to a block. {@code HexOrbifoldProjectionTest} re-derives
 * every preset.
 *
 * <p>Every element of {@code Γ} maps chunks to whole chunks, in the overworld and in the Nether at 1:8: so every cone
 * point is on a multiple of 128, which needs {@code a} a multiple of 512 (cone points at {@code ±a/4}) and {@code b/2}
 * and the fold rows multiples of 128.
 *
 * @param id         the name saved with a world ({@code size}) and shown on the Customize screen
 * @param a          the lap east to west, in blocks
 * @param b          the north–south period, in blocks; the tile is {@code b/2} tall
 * @param northRow   the north fold row {@code zN}: cone point {@code N} is {@code (0, zN)}
 * @param sizeFactor the wrapping plan's {@code k} ({@code a = 3840·k}, {@code b = 3328·k}) for the sizes that have one,
 *                   saved as {@code size_factor} as before; 0 for a size that is no multiple of the k = 1 lattice
 */
public record OrbifoldSize(String id, int a, int b, int northRow, int spawnX, int spawnZ, int sizeFactor) {

    /** 3584 × 3072: the smallest, a 3,584-block lap; {@code b/a} is 1.0% off {@code √3/2}. */
    public static final OrbifoldSize SMALL = new OrbifoldSize("small", 3584, 3072, -1152, 0, 49, 0);
    /** k = 2. */
    public static final OrbifoldSize MEDIUM = new OrbifoldSize("medium", 7680, 6656, -2560, 0, 42, 2);
    /** k = 4: the default. */
    public static final OrbifoldSize NORMAL = new OrbifoldSize("normal", 15360, 13312, -5248, 0, -44, 4);
    /** k = 8. */
    public static final OrbifoldSize LARGE = new OrbifoldSize("large", 30720, 26624, -10368, 0, 40, 8);

    /** Every size a world can have, smallest first. */
    public static final List<OrbifoldSize> PRESETS = List.of(SMALL, MEDIUM, NORMAL, LARGE);
    public static final OrbifoldSize DEFAULT = NORMAL;
    /** The size factors older saves (and the {@code sizeFactor} config) can name. */
    public static final List<Integer> SIZE_FACTORS = PRESETS.stream().filter(s -> s.sizeFactor > 0).map(OrbifoldSize::sizeFactor).toList();
    public static final List<String> IDS = PRESETS.stream().map(OrbifoldSize::id).toList();

    public OrbifoldSize {
        if (a <= 0 || a % 512 != 0) throw new IllegalArgumentException("a must be a positive multiple of 512: " + a);
        if (b <= 0 || b % 256 != 0) throw new IllegalArgumentException("b/2 must be a positive multiple of 128: " + b);
        if (northRow % 128 != 0) throw new IllegalArgumentException("the north fold row must be a multiple of 128: " + northRow);
        if (spawnX < -a / 2 || spawnX >= a / 2 || spawnZ < northRow || spawnZ >= northRow + b / 2) {
            throw new IllegalArgumentException("spawn must be in the tile: " + spawnX + ", " + spawnZ);
        }
        if (sizeFactor != 0 && (a != 3840 * sizeFactor || b != 3328 * sizeFactor)) {
            throw new IllegalArgumentException("size factor " + sizeFactor + " does not match " + a + " x " + b);
        }
    }

    public static Optional<OrbifoldSize> byId(String id) {
        return PRESETS.stream().filter(s -> s.id.equals(id)).findFirst();
    }

    public static Optional<OrbifoldSize> bySizeFactor(int sizeFactor) {
        return PRESETS.stream().filter(s -> s.sizeFactor != 0 && s.sizeFactor == sizeFactor).findFirst();
    }

    /** {@code "15360 × 13312"}. */
    public String dimensions() {
        return this.a + " × " + this.b;
    }

    @Override
    public String toString() {
        return this.id + " (" + this.a + " x " + this.b + (this.sizeFactor != 0 ? ", k=" + this.sizeFactor : "") + ")";
    }
}
