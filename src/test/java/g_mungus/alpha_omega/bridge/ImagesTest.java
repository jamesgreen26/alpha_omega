package g_mungus.alpha_omega.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The bridges' images ({@code orbifold-implementation.md} phase 7): complete near the edges, empty away from them. */
class ImagesTest {

    private static final List<OrbifoldGeometry> GEOMETRIES = List.of(new OrbifoldGeometry(OrbifoldSize.DEFAULT, 4), new OrbifoldGeometry(OrbifoldSize.SMALL, 4));

    /** A random footprint cell, mostly near the edges and the cone points. */
    private static int[] cell(OrbifoldGeometry g, Random random) {
        int x, z;
        switch (random.nextInt(4)) {
            case 0 -> {
                x = random.nextBoolean() ? g.minX - g.reach + random.nextInt(2 * g.reach) : g.maxX - g.reach + random.nextInt(2 * g.reach);
                z = g.northRow - g.reach + random.nextInt(g.southRow - g.northRow + 2 * g.reach);
            }
            case 1 -> {
                x = g.minX - g.reach + random.nextInt(g.a + 2 * g.reach);
                z = random.nextBoolean() ? g.northRow - g.reach + random.nextInt(2 * g.reach) : g.southRow - g.reach + random.nextInt(2 * g.reach);
            }
            case 2 -> {
                OrbifoldGeometry.ConePoint cone = g.conePoints().get(random.nextInt(4));
                x = cone.x() - g.reach + random.nextInt(2 * g.reach);
                z = cone.z() - g.reach + random.nextInt(2 * g.reach);
            }
            default -> {
                x = g.minX - g.reach + random.nextInt(g.a + 2 * g.reach);
                z = g.northRow - g.reach + random.nextInt(g.southRow - g.northRow + 2 * g.reach);
            }
        }
        return new int[] {x, z};
    }

    /**
     * Every other place storage holds a cell is reached by one of the images of a box round it, and every image of the
     * cell that lands in the footprint is the same cell of the world (the same source).
     */
    @Test
    void imagesReachEveryCopyAndOnlyCopies() {
        Random random = new Random(7);
        for (OrbifoldGeometry g : GEOMETRIES) {
            for (int i = 0; i < 20000; i++) {
                int[] p = cell(g, random);
                if (!g.inFootprint(p[0], p[1])) continue;
                OrbifoldGeometry.Cell source = g.canon(p[0], p[1]);
                List<Motion> images = Images.of(g, p[0], p[1], p[0] + 1, p[1] + 1);
                assertTrue(Images.near(g, p[0], p[1], p[0] + 1, p[1] + 1) || images.isEmpty(), "images away from the edges at " + p[0] + ", " + p[1]);
                // Every place: the source and its copies, other than p itself.
                List<int[]> places = new java.util.ArrayList<>();
                places.add(new int[] {source.x(), source.z()});
                for (OrbifoldGeometry.Cell copy : g.copies(source.x(), source.z())) places.add(new int[] {copy.x(), copy.z()});
                for (int[] place : places) {
                    if (place[0] == p[0] && place[1] == p[1]) continue;
                    boolean reached = images.stream().anyMatch(m -> m.cellX(p[0]) == place[0] && m.cellZ(p[1]) == place[1]);
                    assertTrue(reached, g.size + ": no image of " + p[0] + ", " + p[1] + " reaches " + place[0] + ", " + place[1] + " (images " + images + ")");
                }
                for (Motion m : images) {
                    int x = m.cellX(p[0]), z = m.cellZ(p[1]);
                    if (!g.inFootprint(x, z)) continue;
                    OrbifoldGeometry.Cell same = g.canon(x, z);
                    assertEquals(source.x(), same.x(), g.size + ": image " + m + " of " + p[0] + ", " + p[1] + " is another cell");
                    assertEquals(source.z(), same.z(), g.size + ": image " + m + " of " + p[0] + ", " + p[1] + " is another cell");
                }
            }
        }
    }

    /** A region deeper in the tile than the footprint reaches has no image in the footprint, and is not near. */
    @Test
    void interiorHasNoImages() {
        Random random = new Random(11);
        for (OrbifoldGeometry g : GEOMETRIES) {
            int r = g.reach + 1;
            for (int i = 0; i < 5000; i++) {
                double x = g.minX + r + random.nextDouble() * (g.a - 2 * r - 20);
                double z = g.northRow + r + random.nextDouble() * (g.southRow - g.northRow - 2 * r - 20);
                assertFalse(Images.near(g, x, z, x + 20, z + 20));
                assertTrue(Images.of(g, x, z, x + 20, z + 20).isEmpty(), g.size + ": images of an interior box at " + x + ", " + z);
            }
            assertTrue(Images.elementCount(g) < 40, "elements tested near an edge: " + Images.elementCount(g));
        }
    }
}
