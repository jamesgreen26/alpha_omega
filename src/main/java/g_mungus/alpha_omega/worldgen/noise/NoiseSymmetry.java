package g_mungus.alpha_omega.worldgen.noise;

/**
 * How a noise is sampled, and so how it has to behave for the terrain to be invariant under {@code Γ}
 * ({@code alpha-omega-best-wrapping-plan.md} §8).
 *
 * @param scale  noise input units per block, along both horizontal axes (a density function sampling
 *               {@code noise(x * s, ..., z * s)} has scale {@code s})
 * @param axes   which noise axes carry world {@code x} and {@code z}
 * @param parity what a half turn about a cone point does to the noise's value: keeps it (a scalar such as density or
 *               climate), or negates it (a component of a displacement, such as {@code shift_x})
 */
public record NoiseSymmetry(double scale, Axes axes, Parity parity) {

    /** Where world {@code x} and {@code z} enter a noise sampled at {@code (nx, ny, nz)}. */
    public enum Axes {
        /** {@code noise(x, y, z)}: world {@code x} on noise x, world {@code z} on noise z, noise y free. */
        XZ,
        /** {@code noise(z, x, 0)}, as {@code shift_b} samples: world {@code z} on noise x, world {@code x} on noise y, noise z free. */
        SHIFT_B
    }

    public enum Parity {
        /** {@code f(2N − p) = f(p)}. */
        EVEN,
        /** {@code f(2N − p) = −f(p)}: a displacement component turns with the world. */
        ODD
    }

    /** {@code NormalNoise} samples its second octave set at this multiple of its input. */
    public static final double NORMAL_NOISE_SECOND_FACTOR = 1.0181268882175227;

    public static NoiseSymmetry even(double scale) {
        return new NoiseSymmetry(scale, Axes.XZ, Parity.EVEN);
    }

    public NoiseSymmetry withScale(double scale) {
        return new NoiseSymmetry(scale, this.axes, this.parity);
    }
}
