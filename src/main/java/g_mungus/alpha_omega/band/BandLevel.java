package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import org.jetbrains.annotations.Nullable;

/** A level's cached orbifold geometry, for band rules on hot paths (implemented on {@code Level} by a mixin). */
public interface BandLevel {

    @Nullable
    OrbifoldGeometry alpha_omega$geometry();
}
