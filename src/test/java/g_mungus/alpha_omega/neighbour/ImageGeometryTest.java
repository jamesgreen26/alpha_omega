package g_mungus.alpha_omega.neighbour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link ImageGeometry}: which images a viewer needs, and the clip that makes home and images draw every place once. */
class ImageGeometryTest {

    private static final OrbifoldGeometry DEFAULT = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
    private static final int[] VIEWS = {2, 6, 12, 32};

    private static List<OrbifoldGeometry> all() {
        List<OrbifoldGeometry> all = new ArrayList<>(overworld());
        for (OrbifoldSize size : OrbifoldSize.PRESETS) all.add(new OrbifoldGeometry(size, 4, OrbifoldGeometry.NETHER_SCALE));
        return all;
    }

    private static List<OrbifoldGeometry> overworld() {
        return List.of(new OrbifoldGeometry(OrbifoldSize.SMALL, 4), new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4), DEFAULT, new OrbifoldGeometry(OrbifoldSize.LARGE, 4),
            new OrbifoldGeometry(OrbifoldSize.MEDIUM, 2), new OrbifoldGeometry(OrbifoldSize.MEDIUM, 16), new OrbifoldGeometry(OrbifoldSize.SMALL, 2), new OrbifoldGeometry(OrbifoldSize.SMALL, 16));
    }

    /** Viewer positions (blocks) near every seam, corner and cone point, in the tile and in the band. */
    private static List<int[]> places(OrbifoldGeometry g) {
        int band = g.band;
        List<int[]> places = new ArrayList<>(List.of(
            new int[] {g.spawnX, g.spawnZ},
            new int[] {g.maxX - 8, 0}, new int[] {g.maxX + band / 2, 0}, new int[] {g.minX + 8, -100}, new int[] {g.minX - band + 1, 50},
            new int[] {100, g.northRow + 10}, new int[] {-200, g.northRow - band / 2}, new int[] {0, g.northRow}, new int[] {20, g.northRow + 20},
            new int[] {g.maxX - 5, g.northRow + 5}, new int[] {g.maxX + band / 3, g.northRow - band / 3}, new int[] {g.minX + 5, g.northRow + 5},
            new int[] {g.minX - band, g.northRow - band},
            new int[] {g.a / 4, g.southRow - 5}, new int[] {-g.a / 4, g.southRow + band / 2}, new int[] {0, g.southRow - 10}, new int[] {0, g.southRow + 10},
            new int[] {g.minX + 3, g.southRow - 3}, new int[] {g.maxX - 3 + band, g.southRow + band - 1}));
        Random random = new Random(6);
        for (int i = 0; i < 60; i++) {
            // Anywhere a player can stand (tile or band), weighted toward the edges.
            int x = g.minX - band + random.nextInt(g.a + 2 * band);
            int z = g.northRow - band + random.nextInt(g.b / 2 + 2 * band);
            if (random.nextBoolean()) x = random.nextBoolean() ? g.minX - band + random.nextInt(3 * band) : g.maxX + band - 1 - random.nextInt(3 * band);
            else z = random.nextBoolean() ? g.northRow - band + random.nextInt(3 * band) : g.southRow + band - 1 - random.nextInt(3 * band);
            places.add(new int[] {x, z});
        }
        return places;
    }

    @Test
    void candidatesAreClosedUnderInverse() {
        for (OrbifoldGeometry g : all()) {
            List<Motion> candidates = ImageGeometry.candidates(g);
            assertEquals(candidates.size(), new HashSet<>(candidates).size());
            if (!g.isScaled()) assertEquals(8, candidates.size(), "the overworld's candidates are the generators near the corners");
            else assertTrue(candidates.size() >= 8, g + ": " + candidates.size());
            for (Motion m : candidates) assertTrue(candidates.contains(m.inverse()), m + " has no inverse among " + candidates);
        }
    }

    @Test
    void deepInTheTileThereAreNoImages() {
        for (int view : VIEWS) {
            assertEquals(List.of(), ImageGeometry.images(DEFAULT, DEFAULT.spawnX >> 4, DEFAULT.spawnZ >> 4, view));
        }
    }

    @Test
    void eachSeamNeedsItsImage() {
        OrbifoldGeometry g = DEFAULT;
        assertEquals(List.of(g.east), images(g, g.maxX - 8, 0, 6), "east seam");
        assertEquals(List.of(g.west), images(g, g.minX + 8, 0, 6), "west seam");
        assertEquals(List.of(g.northFold), images(g, 100, g.northRow + 10, 6), "north fold");
        assertEquals(List.of(g.southFold), images(g, g.a / 4, g.southRow - 10, 6), "south fold at E");
        assertEquals(List.of(g.southFold.then(g.west)), images(g, -g.a / 4, g.southRow - 10, 6), "south fold at W");
        // At F the view wraps round a half turn: east, north and their composition.
        List<Motion> atF = images(g, g.maxX - 20, g.northRow + 20, 6);
        assertTrue(atF.containsAll(List.of(g.east, g.northFold, g.northFold.then(g.east))), "at F: " + atF);
        // The image starts a band's depth (plus the view) before the seam, so what stands in the far band is seen.
        int reach = 6 * 16 + g.band;
        assertEquals(List.of(g.east), images(g, g.maxX - reach + 8, 0, 6));
        assertEquals(List.of(), images(g, g.maxX - reach - 40, 0, 6));
    }

    private static List<Motion> images(OrbifoldGeometry g, int x, int z, int view) {
        return ImageGeometry.images(g, x >> 4, z >> 4, view);
    }

    /**
     * The clipping predicate: in every viewer's square, a live chunk is drawn by home and no image, and every other
     * chunk by exactly one image the viewer has, from a tile chunk that image tracks within its own square, and whose
     * neighbours (which meshing reads) it tracks too. In the overworld a viewer needs at most four images.
     */
    @Test
    void everyPlaceIsDrawnOnce() {
        int most = 0;
        for (OrbifoldGeometry g : overworld()) {
            for (int[] place : places(g)) {
                int px = place[0] >> 4, pz = place[1] >> 4;
                assertTrue(ImageGeometry.live(g, px, pz), "viewer should stand in the tile or band: " + place[0] + ", " + place[1]);
                for (int view : VIEWS) {
                    most = Math.max(most, ImageGeometry.images(g, px, pz, view).size());
                    checkView(g, px, pz, view);
                }
            }
        }
        assertTrue(most <= 4, "a viewer needed " + most + " images");
    }

    /**
     * Every Nether tile, at every view distance up to 32: the small Nether's tile is 12 chunks tall, so a view crosses
     * it several times, and the candidates reach the lattice translations past both fold rows. Images stay few where
     * the Nether's view is capped ({@link ImageGeometry#maxViewDistance}).
     */
    @Test
    void everyNetherPlaceIsDrawnOnce() {
        for (OrbifoldGeometry g : all()) {
            if (!g.isScaled()) continue;
            int most = 0;
            for (int[] place : places(g)) {
                int px = place[0] >> 4, pz = place[1] >> 4;
                assertTrue(ImageGeometry.live(g, px, pz), "viewer should stand in the tile or band: " + place[0] + ", " + place[1]);
                for (int view = 2; view <= 32; view++) {
                    checkView(g, px, pz, view);
                    if (view <= ImageGeometry.maxViewDistance(g)) most = Math.max(most, ImageGeometry.images(g, px, pz, view).size());
                }
            }
            assertTrue(most <= 9, g + ": a viewer within the capped view needed " + most + " images");
        }
    }

    /** {@link #everyPlaceIsDrawnOnce}'s check for one viewer and view distance. */
    private static void checkView(OrbifoldGeometry g, int px, int pz, int view) {
        List<Motion> candidates = ImageGeometry.candidates(g);
        List<Motion> images = ImageGeometry.images(g, px, pz, view);
        for (int qx = px - view - 1; qx <= px + view + 1; qx++) {
            for (int qz = pz - view - 1; qz <= pz + view + 1; qz++) {
                if (!ImageGeometry.withinView(px, pz, view, qx, qz, true)) continue;
                String at = g + " viewer " + px + "," + pz + " view " + view + " chunk " + qx + "," + qz;
                List<Motion> drawing = new ArrayList<>();
                for (Motion m : candidates) {
                    Motion back = m.inverse();
                    if (ImageGeometry.draws(g, m, back.chunkX(qx), back.chunkZ(qz))) drawing.add(m);
                }
                if (ImageGeometry.homeDraws(g, qx, qz)) {
                    assertEquals(List.of(), drawing, "home draws, and so do images: " + at);
                    continue;
                }
                assertEquals(1, drawing.size(), "drawn by " + drawing + ": " + at);
                Motion m = drawing.get(0);
                assertTrue(images.contains(m), m + " draws but is not among " + images + ": " + at);
                Motion back = m.inverse();
                int cx = back.chunkX(qx), cz = back.chunkZ(qz);
                int centerX = back.chunkX(px), centerZ = back.chunkZ(pz);
                assertTrue(ImageGeometry.withinView(centerX, centerZ, view, cx, cz, true), "outside its image square: " + at);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) assertTrue(ImageGeometry.live(g, cx + dx, cz + dz), "neighbour not tracked: " + at);
                }
            }
        }
    }

    /** Images never draw band or skirt chunks, and never draw onto home's region. */
    @Test
    void imagesDrawOnlyTileBeyondTheBand() {
        OrbifoldGeometry g = DEFAULT;
        int[] f = g.footprintChunks();
        for (Motion m : ImageGeometry.candidates(g)) {
            for (int x = f[0]; x <= f[2]; x += 7) {
                for (int z = f[1]; z <= f[3]; z += 3) {
                    if (!ImageGeometry.draws(g, m, x, z)) continue;
                    assertTrue(g.isTileChunk(x, z));
                    assertFalse(ImageGeometry.homeDraws(g, m.chunkX(x), m.chunkZ(z)));
                }
            }
        }
    }

    @Test
    void thingsPlayWhereTheViewerSeesThem() {
        OrbifoldGeometry g = DEFAULT;
        // A viewer just inside the east seam; a sound at the west edge of the tile is heard just past the seam.
        double viewX = g.maxX - 10, viewZ = 0;
        List<Motion> images = ImageGeometry.images(g, (int) viewX >> 4, 0, 8);
        assertEquals(g.east, ImageGeometry.nearestPlacement(g, images, g.minX + 5.5, 3.0, viewX, viewZ));
        // One stored in the east band, near the viewer, plays where it is.
        assertEquals(Motion.IDENTITY, ImageGeometry.nearestPlacement(g, images, g.maxX + 5.5, 3.0, viewX, viewZ));
        // A viewer just inside the west seam hears that one at its source, by its own frame.
        double westX = g.minX + 10;
        List<Motion> west = ImageGeometry.images(g, (int) westX >> 4, 0, 8);
        assertEquals(g.west, ImageGeometry.nearestPlacement(g, west, g.maxX + 5.5, 3.0, westX, viewZ));
        // Near N, something just south of N is also seen turned, north of it.
        Set<Motion> atN = new HashSet<>(ImageGeometry.placements(g, ImageGeometry.images(g, 0, g.northRow >> 4, 8), 3.5, g.northRow + 30));
        assertTrue(atN.contains(g.northFold), "placements near N: " + atN);
        // Far outside the footprint nothing moves.
        assertEquals(List.of(Motion.IDENTITY), ImageGeometry.placements(g, images, g.maxX + 5000, 0));
    }
}
