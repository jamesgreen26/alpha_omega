package g_mungus.alpha_omega.worldgen.noise;

import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import java.util.Arrays;
import java.util.TreeSet;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * For a density function sampling {@code noise(pos / r)} with the rarity {@code r} picked per position from a small
 * set ({@code weird_scaled_sampler}): one noise per rarity, each invariant at scale {@code 1/r}, made while the noise is
 * built. Shared by every copy of the density function that {@code mapAll} makes (such as each chunk's).
 */
public final class RarityNoises {

    private final double[] rarities;
    private final DensityFunction.NoiseHolder[] noises;
    private final DensityFunction.NoiseHolder fallback;

    public RarityNoises(InvariantNoiseSource source, DensityFunction.NoiseHolder noise, Double2DoubleFunction mapper) {
        TreeSet<Double> found = new TreeSet<>();
        for (int i = -400; i <= 400; i++) found.add(mapper.get(i / 100.0));
        this.rarities = found.stream().mapToDouble(Double::doubleValue).toArray();
        this.noises = new DensityFunction.NoiseHolder[this.rarities.length];
        for (int i = 0; i < this.rarities.length; i++) this.noises[i] = source.alpha_omega$invariant(noise, NoiseSymmetry.even(1.0 / this.rarities[i]));
        this.fallback = noise;
    }

    /** The noise for a rarity; an unexpected one gets the original noise (not invariant). */
    public DensityFunction.NoiseHolder forRarity(double rarity) {
        for (int i = 0; i < this.rarities.length; i++) {
            if (this.rarities[i] == rarity) return this.noises[i];
        }
        return this.fallback;
    }

    @Override
    public String toString() {
        return "RarityNoises" + Arrays.toString(this.rarities);
    }
}
