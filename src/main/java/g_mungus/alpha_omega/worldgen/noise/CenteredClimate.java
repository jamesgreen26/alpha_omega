package g_mungus.alpha_omega.worldgen.noise;

/**
 * Duck interface on {@code Climate.Sampler}. Vanilla samples a quart's climate at its minimum corner, which a half turn
 * maps to the far corner of the image quart; an orbifold's sampler samples at the quart's centre instead, which a half
 * turn maps to the image quart's centre, so biomes match across the folds exactly.
 */
public interface CenteredClimate {

    void alpha_omega$sampleCentres();
}
