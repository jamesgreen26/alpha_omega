package g_mungus.alpha_omega.client;

import g_mungus.alpha_omega.orbifold.Motion;

/**
 * Where the clouds are: the map {@code m} from storage to cloud space that the client draws vanilla's cloud layer
 * through. Vanilla places clouds from the camera's storage {@code x} and {@code z}; a frame transfer moves the camera
 * in storage by an element {@code g} of {@code Γ} while the world round it stays put, so vanilla's clouds jump (by the
 * texture's offset between the two images, and on a fold also turn round).
 *
 * <p><b>The approach.</b> No drifting cloud field can be invariant under {@code Γ}: a half turn reverses any constant
 * wind, so the clouds over a fold would have to drift both ways at once. Instead the clouds follow the player: each
 * transfer by {@code g} updates {@code m ← m ∘ g⁻¹}, so a storage point and its image after the transfer land on the
 * same cloud ({@code m'(g·p) = m(p)}). The frame before and after a crossing shows the same clouds in the same places,
 * drifting the same way over the same ground, at every seam, translation or fold. Since {@code m} is always an element
 * of {@code Γ}, a half-turned {@code m} draws the layer turned 180° about the camera, and its drift runs the other way
 * in storage, which is the same way over the ground.
 *
 * <p>The cost: clouds depend on the path a client took (two players who met by different routes can see different
 * clouds), and {@code m} starts again at the identity on joining a world. Pure apart from the client's one current
 * value.
 */
public final class CloudFrame {

    private static Motion current = Motion.IDENTITY;

    private CloudFrame() {
    }

    /** The client's current storage-to-cloud-space map. */
    public static Motion current() {
        return current;
    }

    /** A new world or connection: clouds start from storage again. */
    public static void reset() {
        current = Motion.IDENTITY;
    }

    /** The local player was moved by {@code g} (a frame transfer): keep every cloud over the same ground. */
    public static void transferred(Motion g) {
        current = afterTransfer(current, g);
    }

    /** {@code m ∘ g⁻¹}: the map after a transfer by {@code g}. */
    public static Motion afterTransfer(Motion m, Motion g) {
        return g.inverse().then(m);
    }

    /** A storage point's cloud-space {@code x} under {@code m}. */
    public static double cloudX(Motion m, double x) {
        return m.pointX(x);
    }

    /** A storage point's cloud-space {@code z} under {@code m}. */
    public static double cloudZ(Motion m, double z) {
        return m.pointZ(z);
    }

    /**
     * Vanilla's cloud texture coordinates (in cloud cells of 12 blocks, before wrapping) of a storage point under
     * {@code m} at a time in ticks: {@code ((x' + 0.03·t) / 12, z' / 12 + 0.33)} with {@code (x', z') = m(x, z)}.
     */
    public static double[] texture(Motion m, double x, double z, double ticks) {
        return new double[] {(cloudX(m, x) + ticks * 0.03) / 12.0, cloudZ(m, z) / 12.0 + 0.33};
    }
}
