package g_mungus.alpha_omega.neighbour;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which images of the storage a viewer needs, and which chunks each one shows ({@code orbifold-implementation.md}
 * phase 6). Pure: chunk coordinates and {@link Motion}s only, shared by the server's views and the client's renderer.
 *
 * <p>A viewer at storage chunk {@code p} sees vanilla's square around {@code p}. Its <b>home</b> part is the tile and
 * band ({@link #live}): vanilla draws that from local chunks. Everything further out is drawn from <b>images</b>: an
 * image {@code g} is the storage moved by {@code g}, drawn around the viewer's <b>image position</b>
 * {@code g⁻¹(p)}. A point {@code q} of the square past the band is drawn by the image {@code g = frame(q)⁻¹}, from
 * the tile cell {@code g⁻¹(q)} ({@link #draws}). Every such point is drawn exactly once: by home if it is live, else
 * by the one image whose inverse takes it into the tile.
 *
 * <p>An image is <b>needed</b> ({@link #images}) as soon as the viewer's square reaches {@code g(live)}: the tile and
 * band moved by {@code g}. That is a band's depth before its terrain is drawn, because entities standing in the band
 * on the far side of the tile are physically just across the seam, and because the image's chunks then arrive strip
 * by strip as the viewer nears the seam rather than all at once. Around its image position, an image tracks the live
 * chunks ({@link #live}): the tile chunks it draws and their neighbours (for meshing), and band chunks (for the
 * entities standing there).
 */
public final class ImageGeometry {

    private ImageGeometry() {
    }

    /**
     * The elements whose images can come into view from the footprint: the generators and their compositions near the
     * tile corners, as {@link OrbifoldGeometry#frame} produces them. Closed under inverses.
     */
    public static List<Motion> candidates(OrbifoldGeometry geometry) {
        Candidates last = lastCandidates;
        if (last != null && last.geometry == geometry) return last.elements;
        List<Motion> elements = List.of(geometry.east, geometry.west, geometry.northFold, geometry.southFold,
            geometry.northFold.then(geometry.east), geometry.northFold.then(geometry.west),
            geometry.southFold.then(geometry.east), geometry.southFold.then(geometry.west));
        lastCandidates = new Candidates(geometry, elements);
        return elements;
    }

    /** The candidates last worked out, for the geometry they were worked out for (asked for per entity and player). */
    private record Candidates(OrbifoldGeometry geometry, List<Motion> elements) {
    }

    private static volatile Candidates lastCandidates;

    /** Whether a chunk is live storage: tile or band. Home draws exactly these; images track exactly these. */
    public static boolean live(OrbifoldGeometry geometry, int chunkX, int chunkZ) {
        return geometry.chunkDepth(chunkX, chunkZ) <= geometry.bandChunks;
    }

    /** The live chunks' bounds, {@code {minX, minZ, maxX, maxZ}}, inclusive. */
    public static int[] liveChunks(OrbifoldGeometry geometry) {
        int band = geometry.bandChunks;
        return new int[] {(geometry.minX >> 4) - band, (geometry.northRow >> 4) - band, (geometry.maxX >> 4) - 1 + band,
            (geometry.southRow >> 4) - 1 + band};
    }

    /** The chunk rectangle {@code {minX, minZ, maxX, maxZ}} moved by {@code g}. */
    public static int[] moved(Motion g, int[] rect) {
        int x1 = g.chunkX(rect[0]), x2 = g.chunkX(rect[2]), z1 = g.chunkZ(rect[1]), z2 = g.chunkZ(rect[3]);
        return new int[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
    }

    /**
     * The images a viewer at chunk {@code (centerX, centerZ)} needs with a view distance: each element whose live
     * chunks, moved by it, come within the view (as vanilla counts it, outer ring included). In a fixed order; none
     * for a viewer outside the footprint.
     */
    public static List<Motion> images(OrbifoldGeometry geometry, int centerX, int centerZ, int viewDistance) {
        // Outside the footprint (a spectator flying off, another mod's storage) there is nothing to see images of.
        if (!geometry.inFootprintChunk(centerX, centerZ)) return List.of();
        int[] live = liveChunks(geometry);
        List<Motion> images = new ArrayList<>(4);
        for (Motion g : candidates(geometry)) {
            int[] r = moved(g, live);
            int nearestX = Math.max(r[0], Math.min(r[2], centerX));
            int nearestZ = Math.max(r[1], Math.min(r[3], centerZ));
            if (withinView(centerX, centerZ, viewDistance, nearestX, nearestZ, true)) images.add(g);
        }
        images.sort(ORDER);
        return images;
    }

    /** A fixed order for images, so lists of them compare equal when they hold the same. */
    public static final Comparator<Motion> ORDER = Comparator.comparing(Motion::turned).thenComparingInt(Motion::tx).thenComparingInt(Motion::tz);

    /**
     * Whether image {@code g} draws storage chunk {@code (chunkX, chunkZ)}: it is a tile chunk (band and skirt are
     * never drawn from an image) and {@code g} takes it past the band, where home does not draw.
     */
    public static boolean draws(OrbifoldGeometry geometry, Motion g, int chunkX, int chunkZ) {
        return geometry.isTileChunk(chunkX, chunkZ) && geometry.chunkDepth(g.chunkX(chunkX), g.chunkZ(chunkZ)) > geometry.bandChunks;
    }

    /** Whether home (vanilla) draws a storage chunk: the tile and band. The skirt and the void beyond come from images. */
    public static boolean homeDraws(OrbifoldGeometry geometry, int chunkX, int chunkZ) {
        return live(geometry, chunkX, chunkZ);
    }

    /** Vanilla's {@code ChunkTrackingView.isWithinDistance}: the round view square, optionally with its outer ring. */
    public static boolean withinView(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuter) {
        int dx = Math.max(0, Math.abs(x - centerX) - 1);
        int dz = Math.max(0, Math.abs(z - centerZ) - 1);
        long far = Math.max(0, Math.max(dx, dz) - (includeOuter ? 1 : 0));
        long near = Math.min(dx, dz);
        return near * near + far * far < (long) viewDistance * viewDistance;
    }

    /**
     * The places a thing stored at point {@code (x, z)} shows to a viewer with the given images, as the elements that
     * take it there (identity first: where it is stored). Its source {@code s} (in the tile) shows at {@code s} itself
     * and at {@code g(s)} for each image {@code g}; the element for {@code s} is the point's frame {@code f}, and for
     * {@code g(s)} it is {@code f} then {@code g}. Each element is listed once.
     */
    public static List<Motion> placements(OrbifoldGeometry geometry, List<Motion> images, double x, double z) {
        // Outside the footprint (another mod's storage far out in the level) nothing is an image of anything.
        if (!geometry.inFootprint((int) Math.floor(x), (int) Math.floor(z))) return List.of(Motion.IDENTITY);
        Motion f = geometry.frame(x, z);
        List<Motion> placements = new ArrayList<>(images.size() + 2);
        placements.add(Motion.IDENTITY);
        if (!f.isIdentity()) placements.add(f);
        for (Motion g : images) {
            Motion k = f.then(g);
            if (!placements.contains(k)) placements.add(k);
        }
        return placements;
    }

    /**
     * Of a point's placements ({@link #placements}), the one nearest the viewer at {@code (viewX, viewZ)}: where a
     * sound or particle there is heard or seen. The identity when it is nearest where it is stored.
     */
    public static Motion nearestPlacement(OrbifoldGeometry geometry, List<Motion> images, double x, double z, double viewX, double viewZ) {
        Motion best = Motion.IDENTITY;
        double bestDistance = Double.MAX_VALUE;
        for (Motion k : placements(geometry, images, x, z)) {
            double dx = k.pointX(x) - viewX, dz = k.pointZ(z) - viewZ;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = k;
            }
        }
        return best;
    }
}
