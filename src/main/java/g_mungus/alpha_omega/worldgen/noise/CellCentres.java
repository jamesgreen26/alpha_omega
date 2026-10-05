package g_mungus.alpha_omega.worldgen.noise;

/**
 * Duck interface on {@code NoiseChunk}: what to add to an interpolation fraction across a cell. In an orbifold it is
 * half a block, so blocks are sampled at their centres, which a half turn maps to its image blocks' centres; vanilla
 * samples minimum corners, which a half turn maps one block off. Zero elsewhere.
 */
public interface CellCentres {

    double alpha_omega$centreOffset();
}
