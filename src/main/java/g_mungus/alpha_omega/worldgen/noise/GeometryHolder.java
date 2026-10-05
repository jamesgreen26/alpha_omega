package g_mungus.alpha_omega.worldgen.noise;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import org.jetbrains.annotations.Nullable;

/** Duck interface on {@code NoiseChunk} (stamped from its {@code RandomState}) and {@code BiomeManager}: the orbifold it works in, if any. */
public interface GeometryHolder {

    @Nullable
    OrbifoldGeometry alpha_omega$geometry();

    @Nullable
    static OrbifoldGeometry of(Object holder) {
        return ((GeometryHolder) holder).alpha_omega$geometry();
    }
}
