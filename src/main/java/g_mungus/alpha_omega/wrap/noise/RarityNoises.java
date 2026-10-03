package g_mungus.alpha_omega.wrap.noise;

import java.util.Arrays;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * For a density function sampling {@code noise(pos / r)} with {@code r} from a small discrete set: one copy of the
 * noise per rarity, periodic at {@code W / r}, created as rarities are first seen. Shared by every copy of the
 * density function that {@code mapAll} makes (e.g. each chunk's {@code NoiseChunk}).
 */
public final class RarityNoises {

    private final PeriodicNoiseSource source;
    private final DensityFunction.NoiseHolder noise;
    private volatile double[] rarities = new double[0];
    private volatile DensityFunction.NoiseHolder[] noises = new DensityFunction.NoiseHolder[0];

    public RarityNoises(PeriodicNoiseSource source, DensityFunction.NoiseHolder noise) {
        this.source = source;
        this.noise = noise;
    }

    public DensityFunction.NoiseHolder forRarity(double rarity) {
        // Read noises first: they are published before rarities, so it is at least as long.
        DensityFunction.NoiseHolder[] noises = this.noises;
        double[] rarities = this.rarities;
        for (int i = 0; i < rarities.length; i++) {
            if (rarities[i] == rarity) return noises[i];
        }
        return this.create(rarity);
    }

    private synchronized DensityFunction.NoiseHolder create(double rarity) {
        double[] rarities = this.rarities;
        for (int i = 0; i < rarities.length; i++) {
            if (rarities[i] == rarity) return this.noises[i];
        }
        double period = this.source.alpha_omega$wrap().period / rarity;
        DensityFunction.NoiseHolder periodic = this.source.alpha_omega$periodic(this.noise, period, 0, period);
        DensityFunction.NoiseHolder[] noises = Arrays.copyOf(this.noises, rarities.length + 1);
        noises[rarities.length] = periodic;
        double[] grown = Arrays.copyOf(rarities, rarities.length + 1);
        grown[rarities.length] = rarity;
        this.noises = noises;
        this.rarities = grown;
        return periodic;
    }
}
