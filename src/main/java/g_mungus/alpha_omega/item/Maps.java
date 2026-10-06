package g_mungus.alpha_omega.item;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.NearestImages;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import org.jetbrains.annotations.Nullable;

/**
 * Filled maps on an orbifold, kept simple:
 * <ul>
 * <li>A new map is centred from its creator's <b>source</b> position (in the tile), so a map made in the band is the
 * map of the same place made from the tile.</li>
 * <li>Whatever a map marks (players, frames, banners, explorer targets) and whoever updates it are taken at their
 * image <b>nearest the map's centre</b>, turned with that image: a map held in the band or across a seam shows its
 * holder where they are relative to the mapped ground.</li>
 * <li>Pixels are read at the map's own world positions, which may reach into the band and skirt (live copies, so the
 * right blocks); chunks past the stored footprint are skipped instead of loaded.</li>
 * </ul>
 * Pure; {@code MapItemMixin} and {@code MapItemSavedDataMixin} apply it.
 */
public final class Maps {

    private Maps() {
    }

    /** The point a map made at {@code (x, z)} is centred from: its source in the tile. */
    public static int[] centreFrom(OrbifoldGeometry geometry, int x, int z) {
        if (!geometry.inFootprint(x, z)) return new int[] {x, z};
        OrbifoldGeometry.Cell source = geometry.canon(x, z);
        return new int[] {source.x(), source.z()};
    }

    /** A marked point, at its image nearest a map's centre, with its rotation (degrees) turned along: {@code {x, z, rotation}}. */
    public static double[] decoration(@Nullable OrbifoldGeometry geometry, int centreX, int centreZ, double x, double z, double rotation) {
        if (geometry == null) return new double[] {x, z, rotation};
        Motion h = NearestImages.toward(geometry, x, z, centreX, centreZ);
        return new double[] {h.pointX(x), h.pointZ(z), h.turned() ? rotation + 180.0 : rotation};
    }
}
