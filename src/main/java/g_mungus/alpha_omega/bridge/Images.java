package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The other places in storage that hold the cells of a region (RS §5, orbifold plan phase 7): its <b>images</b>. An
 * element {@code g} of {@code Γ} maps a region to the same blocks in the world, so wherever {@code g(region)} meets the
 * footprint, storage holds those blocks a second time (as band copies, or as the sources of band copies). Bridges look
 * there as well as at the region itself.
 *
 * <p>Two checks keep this free away from the edges. {@link #near} is four comparisons: the images of a region deeper
 * in the tile than the footprint reaches past it lie wholly outside the footprint (the tile is a fundamental domain, so
 * {@code g⁻¹(tile)} is outside the tile, and {@code g} keeps Chebyshev distance). Then {@link #of} tests the region
 * against a short list of rectangles, {@code g⁻¹(footprint)} for each element whose rectangle meets the footprint at
 * all, computed once per geometry.
 */
public final class Images {

    /** An element and the rectangle of storage it takes into the footprint: {@code g⁻¹(footprint)}. */
    private record Element(Motion motion, double minX, double minZ, double maxX, double maxZ) {
    }

    /**
     * Elements per geometry (by identity: a geometry has no {@code equals}), so the overworld and the Nether, which tick
     * one after the other, keep theirs. Cleared if it ever holds more than a handful (geometries of earlier worlds).
     */
    private static final Map<OrbifoldGeometry, Element[]> CACHE = new ConcurrentHashMap<>();

    private Images() {
    }

    /**
     * Whether a region (block coordinates, {@code min ≤ max}) may have images: it reaches outside the tile, or nearer
     * its edge than the footprint reaches past it.
     */
    public static boolean near(OrbifoldGeometry geometry, double minX, double minZ, double maxX, double maxZ) {
        // One block more than the footprint reaches, so a region whose image only touches the footprint's edge counts.
        int reach = geometry.reach + 1;
        return minX < geometry.minX + reach || maxX > geometry.maxX - reach || minZ < geometry.northRow + reach || maxZ > geometry.southRow - reach;
    }

    /** Whether the cube of radius {@code radius} round a point may have images. */
    public static boolean near(OrbifoldGeometry geometry, double x, double z, double radius) {
        return near(geometry, x - radius, z - radius, x + radius, z + radius);
    }

    /**
     * Every element other than the identity that takes some of the region into the footprint, or an empty list. The
     * caller is expected to have checked {@link #near} first; this is correct either way.
     */
    public static List<Motion> of(OrbifoldGeometry geometry, double minX, double minZ, double maxX, double maxZ) {
        List<Motion> images = null;
        for (Element e : elements(geometry)) {
            if (maxX >= e.minX && minX <= e.maxX && maxZ >= e.minZ && minZ <= e.maxZ) {
                if (images == null) images = new ArrayList<>(4);
                images.add(e.motion);
            }
        }
        return images == null ? List.of() : images;
    }

    /** The images of the cube of radius {@code radius} round a point, if it is {@link #near}; else an empty list. */
    public static List<Motion> around(OrbifoldGeometry geometry, double x, double z, double radius) {
        if (!near(geometry, x, z, radius)) return List.of();
        return of(geometry, x - radius, z - radius, x + radius, z + radius);
    }

    private static Element[] elements(OrbifoldGeometry geometry) {
        Element[] cached = CACHE.get(geometry);
        if (cached != null) return cached;
        // Every product of up to four generators: enough to reach each place a footprint cell can be held, including
        // round the cone points, where a cell's copies are a fold and a translation away from each other.
        Set<Motion> found = new LinkedHashSet<>();
        found.add(Motion.IDENTITY);
        List<Motion> frontier = List.of(Motion.IDENTITY);
        for (int depth = 0; depth < 4; depth++) {
            List<Motion> next = new ArrayList<>();
            for (Motion m : frontier) {
                for (Motion generator : geometry.generators()) {
                    Motion product = m.then(generator);
                    if (found.add(product)) next.add(product);
                }
            }
            frontier = next;
        }
        double fMinX = geometry.minX - geometry.reach, fMaxX = geometry.maxX + geometry.reach;
        double fMinZ = geometry.northRow - geometry.reach, fMaxZ = geometry.southRow + geometry.reach;
        List<Element> elements = new ArrayList<>();
        for (Motion g : found) {
            if (g.isIdentity()) continue;
            Motion back = g.inverse();
            double x1 = back.pointX(fMinX), x2 = back.pointX(fMaxX), z1 = back.pointZ(fMinZ), z2 = back.pointZ(fMaxZ);
            Element e = new Element(g, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
            // Only elements that take some of the footprint back into it matter: storage holds nothing elsewhere.
            if (e.maxX >= fMinX && e.minX <= fMaxX && e.maxZ >= fMinZ && e.minZ <= fMaxZ) elements.add(e);
        }
        Element[] array = elements.toArray(Element[]::new);
        if (CACHE.size() >= 8) CACHE.clear();
        CACHE.put(geometry, array);
        return array;
    }

    /** How many elements are tested for a region near an edge (for the scan report). */
    public static int elementCount(OrbifoldGeometry geometry) {
        return elements(geometry).length;
    }
}
