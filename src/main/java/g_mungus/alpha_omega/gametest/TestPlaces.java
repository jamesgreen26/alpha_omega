package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;

/**
 * Test sites laid out for the default size, and where they go at other sizes ({@code -PorbifoldSize}). Sites must stay
 * inside the tile and clear of each other, which the default layout's absolute coordinates do not at the small size
 * (its tile is 1536 blocks tall), so each site names its place at other sizes explicitly, from the fold rows and
 * edges. Along the east seam at other sizes, from {@code zN}:
 * <pre>
 *   144  fresh band chunk for the gate (default chunk zN/16 + 200, z −2048)
 *   192  band lanes, 32 apart        (default −2000)
 *   592  image lingers walk, ~250 west of the seam (default −2100.5); far enough from both folds to need no fold image
 *   692  image walk south, 320       (default −1500.5)
 *  1040  bridge lanes, 32 apart      (default −3600; owners by the west edge, copies in the east band)
 *  1216  mob sent across the seam    (default 600.5)
 *  1376  mob in the far band         (default 900.5)
 * </pre>
 * The south lanes start at {@code x = −1000} (default −1500), and the gate's forced tile chunk at the north seam at
 * {@code x = −1000} (default 3000), clear of the north lanes and image walks. Bridge sites on the north fold are at
 * {@code x = 400} and up at every size (copies at {@code −401} and down), and on the south fold at {@code x = 300} and up
 * (default 2500).
 */
final class TestPlaces {

    private TestPlaces() {
    }

    /** {@code atDefault} at the default size, else {@code elsewhere}. */
    static int at(OrbifoldGeometry geometry, int atDefault, int elsewhere) {
        return geometry.size.equals(OrbifoldSize.DEFAULT) ? atDefault : elsewhere;
    }

    static double at(OrbifoldGeometry geometry, double atDefault, double elsewhere) {
        return geometry.size.equals(OrbifoldSize.DEFAULT) ? atDefault : elsewhere;
    }
}
