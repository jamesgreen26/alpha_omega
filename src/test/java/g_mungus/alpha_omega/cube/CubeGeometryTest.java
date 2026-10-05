package g_mungus.alpha_omega.cube;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class CubeGeometryTest {

    /** Small enough to check every cell: R = 16, cube centre at y = -16, build height -24..40. */
    private static final CubeGeometry SMALL = new CubeGeometry(new CubeSettings(2, CubeSettings.SunAxis.DIAGONAL, 1.0), 0, -24, 40);
    private static final CubeGeometry DEFAULT = new CubeGeometry(CubeSettings.DEFAULT, 63, -64, 320);
    private static final CubeGeometry ODD = new CubeGeometry(new CubeSettings(5, CubeSettings.SunAxis.POLAR, 2.0), 63, -64, 320);

    @Test
    void facesAreProperRotationsWithTheirNormalInTheMiddle() {
        for (CubeFace face : CubeFace.values()) {
            int det = 0;
            for (int j = 0; j < 3; j++) {
                det += face.m(0, j) * (face.m(1, (j + 1) % 3) * face.m(2, (j + 2) % 3) - face.m(1, (j + 2) % 3) * face.m(2, (j + 1) % 3));
            }
            assertEquals(1, det, face + " is not a proper rotation");
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    int dot = 0;
                    for (int i = 0; i < 3; i++) dot += face.m(i, a) * face.m(i, b);
                    assertEquals(a == b ? 1 : 0, dot, face + " columns not orthonormal");
                }
            }
            assertEquals(face.sign, face.normal(face.axis));
            assertEquals(face, CubeFace.byNormal(face.axis, face.sign));
            assertEquals(face.axis, face.opposite().axis);
            assertNotEquals(face.sign, face.opposite().sign);
        }
        assertEquals(CubeFace.UP, CubeFace.bySlot(0));
    }

    /** Every cube cell is owned by exactly one face, or is a barrier cell that every face storing it agrees on. */
    @Test
    void ownedRegionsAndBarrierTileSpace() {
        int r = SMALL.radius;
        int reach = r + SMALL.maxY - SMALL.planeY;
        for (int cx = -reach; cx < reach; cx++) {
            for (int cy = -reach; cy < reach; cy++) {
                for (int cz = -reach; cz < reach; cz++) {
                    double[] centre = {cx + 0.5, cy + 0.5, cz + 0.5};
                    int owners = 0, barriers = 0, stored = 0;
                    for (CubeFace face : CubeFace.values()) {
                        double[] s = SMALL.fromCube(face, centre);
                        int x = (int) Math.floor(s[0]), y = (int) Math.floor(s[1]), z = (int) Math.floor(s[2]);
                        if (y < SMALL.minY || y >= SMALL.maxY) continue;
                        stored++;
                        int owner = SMALL.cellOwner(face, x, y, z);
                        if (owner == face.slot()) owners++;
                        if (owner == CubeGeometry.BARRIER) barriers++;
                    }
                    if (stored == 0) continue;
                    assertTrue(owners <= 1, "cell " + cx + " " + cy + " " + cz + " owned twice");
                    assertTrue(owners == 0 || barriers == 0, "cell " + cx + " " + cy + " " + cz + " both owned and barrier");
                    if (barriers > 0) assertEquals(stored, barriers, "faces disagree on barrier cell " + cx + " " + cy + " " + cz);
                }
            }
        }
    }

    /** No owned cell of one face shares a block face with an owned cell of another: the barrier is tight. */
    @Test
    void barrierIsFaceTight() {
        CubeFace face = CubeFace.UP;
        int cx = SMALL.centerX(face), cz = SMALL.centerZ();
        int f = SMALL.footprint;
        int[][] steps = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int x = cx - f - 1; x <= cx + f; x++) {
            for (int y = SMALL.minY; y < SMALL.maxY - 1; y++) {
                for (int z = cz - f - 1; z <= cz + f; z++) {
                    int a = SMALL.cellOwner(face, x, y, z);
                    if (a == CubeGeometry.BARRIER) continue;
                    for (int[] d : steps) {
                        int b = SMALL.cellOwner(face, x + d[0], y + d[1], z + d[2]);
                        if (b != CubeGeometry.BARRIER) assertEquals(a, b, "owned cells of two faces touch at " + x + " " + y + " " + z);
                    }
                }
            }
        }
    }

    @Test
    void ownedRegionWidensAboveTheFacePlaneAndNarrowsBelow() {
        CubeFace face = CubeFace.UP;
        int cx = SMALL.centerX(face), cz = SMALL.centerZ(), r = SMALL.radius;
        // Just above the plane, the base square is owned up to its edge; the next cell out is barrier.
        assertTrue(SMALL.isOwned(face, cx + r - 1, SMALL.planeY, cz));
        assertTrue(SMALL.isBarrier(face, cx + r, SMALL.planeY, cz));
        // Ten blocks up, the face owns ten blocks past its edge: the overhang.
        int y = SMALL.planeY + 10;
        assertTrue(SMALL.isOwned(face, cx + r + 9, y, cz));
        assertTrue(SMALL.isBarrier(face, cx + r + 10, y, cz));
        assertEquals(CubeFace.EAST.slot(), SMALL.cellOwner(face, cx + r + 11, y, cz));
        // Ten blocks below the plane, the diagonal is ten blocks in from the edge.
        y = SMALL.planeY - 10;
        assertTrue(SMALL.isOwned(face, cx + r - 11, y, cz));
        assertTrue(SMALL.isBarrier(face, cx + r - 10, y, cz));
        assertEquals(CubeFace.EAST.slot(), SMALL.cellOwner(face, cx + r - 9, y, cz));
        assertTrue(SMALL.isBarrier(face, cx, y, cz - r + 9));
        assertEquals(CubeFace.NORTH.slot(), SMALL.cellOwner(face, cx, y, cz - r + 8));
    }

    /** Each column has one barrier cell of its own face: owned above, other faces' below. */
    @Test
    void barrierIsAHeightfield() {
        for (CubeFace face : CubeFace.values()) {
            for (int x = SMALL.centerX(face) - SMALL.footprint - 2; x < SMALL.centerX(face) + SMALL.footprint + 2; x++) {
                for (int z = SMALL.centerZ() - SMALL.footprint - 2; z < SMALL.centerZ() + SMALL.footprint + 2; z++) {
                    int barrierY = SMALL.barrierY(face, x, z);
                    for (int y = SMALL.minY; y < SMALL.maxY; y++) {
                        int owner = SMALL.cellOwner(face, x, y, z);
                        if (y > barrierY) assertEquals(face.slot(), owner);
                        else if (y == barrierY) assertEquals(CubeGeometry.BARRIER, owner);
                        // Below: other faces' cells, or (in corner columns) the barrier between two other faces.
                        else assertNotEquals(face.slot(), owner);
                    }
                }
            }
        }
    }

    /** A barrier cell names its partner, and the partner's copy of the cell names this face back. */
    @Test
    void barrierPartnersAreMutual() {
        for (CubeFace face : CubeFace.values()) {
            for (int x = SMALL.centerX(face) - SMALL.footprint; x < SMALL.centerX(face) + SMALL.footprint; x++) {
                for (int z = SMALL.centerZ() - SMALL.footprint; z < SMALL.centerZ() + SMALL.footprint; z++) {
                    int y = SMALL.barrierY(face, x, z);
                    if (y < SMALL.minY || y >= SMALL.maxY) continue;
                    CubeFace partner = SMALL.barrierPartner(face, x, y, z);
                    if (partner == null) continue;
                    assertTrue(face.isNeighbour(partner));
                    int[] there = SMALL.transformBlock(face, partner, x, y, z);
                    assertEquals(face, SMALL.barrierPartner(partner, there[0], there[1], there[2]));
                    if (y + 1 < SMALL.maxY) assertNull(SMALL.barrierPartner(face, x, y + 1, z), "owned cells have no partner");
                }
            }
        }
    }

    /** Above the face plane, both faces sample flat noise at the same point on the diagonal: they meet at the ridge. */
    @Test
    void surfacePointsMeetOnTheDiagonalAboveGround() {
        Random random = new Random(11);
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                if (!from.isNeighbour(to)) continue;
                for (int i = 0; i < 100; i++) {
                    double h = random.nextDouble() * 200;
                    double lateral = (random.nextDouble() * 2 - 1) * DEFAULT.radius;
                    double[] toward = from.toward(to);
                    double along = DEFAULT.radius + h;
                    double x = DEFAULT.centerX(from) + toward[0] * along + toward[2] * lateral;
                    double z = DEFAULT.centerZ() + toward[2] * along + toward[0] * lateral;
                    double y = DEFAULT.planeY + h;
                    double[] there = DEFAULT.transform(from, to, x, y, z);
                    assertArrayEquals(DEFAULT.surfacePoint(x, z), DEFAULT.surfacePoint(there[0], there[2]), 1e-9);
                }
            }
        }
        // On a face, the surface point is the face's own flat square.
        double[] s = DEFAULT.surfacePoint(DEFAULT.centerX(CubeFace.UP) + 10, DEFAULT.centerZ() - 20);
        assertArrayEquals(new double[] {10, DEFAULT.radius, -20}, s, 1e-12);
        assertNull(DEFAULT.surfacePoint(DEFAULT.centerX(CubeFace.UP), DEFAULT.centerZ() + 8 * DEFAULT.spacingChunks + 1));
    }

    /** Every face names the same physical point as a cell's smallest cube corner. */
    @Test
    void cubeMinCornersAgreeAcrossFaces() {
        Random random = new Random(13);
        for (int i = 0; i < 2000; i++) {
            CubeFace from = CubeFace.bySlot(random.nextInt(6)), to = CubeFace.bySlot(random.nextInt(6));
            int x = DEFAULT.centerX(from) + random.nextInt(400) - 200, y = random.nextInt(300) - 50, z = DEFAULT.centerZ() + random.nextInt(400) - 200;
            int[] mine = DEFAULT.cubeMinCorner(from, x, y, z);
            int[] cell = DEFAULT.transformBlock(from, to, x, y, z);
            int[] theirs = DEFAULT.cubeMinCorner(to, cell[0], cell[1], cell[2]);
            assertArrayEquals(theirs, DEFAULT.transformCorner(from, to, mine[0], mine[1], mine[2]));
            double[] c = DEFAULT.toCube(from, mine[0], mine[1], mine[2]);
            double[] centre = DEFAULT.toCube(from, x + 0.5, y + 0.5, z + 0.5);
            for (int axis = 0; axis < 3; axis++) assertEquals(centre[axis] - 0.5, c[axis], 1e-9, "the corner is the smallest in cube space");
        }
    }

    @Test
    void boxOwnedKeepsClearOfTheBarrier() {
        CubeFace up = CubeFace.UP;
        int cx = DEFAULT.centerX(up), cz = DEFAULT.centerZ(), r = DEFAULT.radius, y0 = DEFAULT.planeY;
        assertTrue(DEFAULT.boxOwned(up, cx - 10, y0 - 20, cz - 10, cx + 10, y0 + 20, cz + 10, 8));
        // Reaching past the edge at ground level crosses the barrier.
        assertFalse(DEFAULT.boxOwned(up, cx + r - 5, y0, cz, cx + r + 5, y0 + 10, cz + 5, 0));
        // High enough up, the same columns are owned overhang, but not within the margin.
        assertTrue(DEFAULT.boxOwned(up, cx + r - 5, y0 + 20, cz, cx + r + 5, y0 + 30, cz + 5, 0));
        assertFalse(DEFAULT.boxOwned(up, cx + r - 5, y0 + 10, cz, cx + r + 5, y0 + 30, cz + 5, 8));
        // Deep down near an edge belongs to the neighbour.
        assertFalse(DEFAULT.boxOwned(up, cx + r - 40, y0 - 50, cz, cx + r - 30, y0 - 40, cz + 5, 0));
        // Above the build limit, or in another face's storage.
        assertFalse(DEFAULT.boxOwned(up, cx, DEFAULT.maxY - 5, cz, cx + 5, DEFAULT.maxY, cz + 5, 0));
        assertFalse(DEFAULT.boxOwned(CubeFace.EAST, cx - 5, y0, cz - 5, cx + 5, y0 + 5, cz + 5, 0));
    }

    @Test
    void blockTransformsMatchPointTransformsAndRoundTrip() {
        Random random = new Random(1);
        for (CubeGeometry g : new CubeGeometry[] {SMALL, DEFAULT, ODD}) {
            for (int i = 0; i < 2000; i++) {
                CubeFace from = CubeFace.bySlot(random.nextInt(6));
                CubeFace to = CubeFace.bySlot(random.nextInt(6));
                int x = g.centerX(from) + random.nextInt(2 * g.footprint) - g.footprint;
                int y = g.minY + random.nextInt(g.maxY - g.minY);
                int z = g.centerZ() + random.nextInt(2 * g.footprint) - g.footprint;
                int[] b = g.transformBlock(from, to, x, y, z);
                double[] p = g.transform(from, to, x + 0.5, y + 0.5, z + 0.5);
                assertArrayEquals(new double[] {b[0] + 0.5, b[1] + 0.5, b[2] + 0.5}, p, 1e-9);
                assertArrayEquals(new int[] {x, y, z}, g.transformBlock(to, from, b[0], b[1], b[2]));
                // The same cell: ownership agrees in both storages.
                assertEquals(g.cellOwner(from, x, y, z), g.cellOwner(to, b[0], b[1], b[2]));
            }
        }
    }

    @Test
    void transformsCompose() {
        Random random = new Random(2);
        for (int i = 0; i < 1000; i++) {
            CubeFace a = CubeFace.bySlot(random.nextInt(6)), b = CubeFace.bySlot(random.nextInt(6)), c = CubeFace.bySlot(random.nextInt(6));
            double x = DEFAULT.centerX(a) + random.nextGaussian() * 200, y = random.nextGaussian() * 100, z = DEFAULT.centerZ() + random.nextGaussian() * 200;
            double[] ab = DEFAULT.transform(a, b, x, y, z);
            assertArrayEquals(DEFAULT.transform(a, c, x, y, z), DEFAULT.transform(b, c, ab[0], ab[1], ab[2]), 1e-9);
            double[] v = CubeGeometry.rotate(a, b, 0.3, -1.2, 2.5);
            assertArrayEquals(new double[] {0.3, -1.2, 2.5}, CubeGeometry.rotate(b, a, v[0], v[1], v[2]), 1e-12);
        }
        // A face's up, seen from a neighbour, points along that neighbour's ground toward it... away from it.
        double[] up = CubeGeometry.rotate(CubeFace.UP, CubeFace.EAST, 0, 1, 0);
        double[] east = CubeGeometry.rotate(CubeFace.EAST, CubeFace.EAST, 0, 1, 0);
        assertEquals(0.0, up[1], 1e-12, "UP's normal lies in EAST's ground plane");
        assertEquals(1.0, east[1], 1e-12);
    }

    @Test
    void ownerAtAgreesWithCellOwnerAwayFromTies() {
        Random random = new Random(3);
        for (int i = 0; i < 5000; i++) {
            CubeFace face = CubeFace.bySlot(random.nextInt(6));
            int x = SMALL.centerX(face) + random.nextInt(2 * SMALL.footprint) - SMALL.footprint;
            int y = SMALL.minY + random.nextInt(SMALL.maxY - SMALL.minY);
            int z = SMALL.centerZ() + random.nextInt(2 * SMALL.footprint) - SMALL.footprint;
            int owner = SMALL.cellOwner(face, x, y, z);
            if (owner == CubeGeometry.BARRIER) continue;
            assertEquals(owner, SMALL.ownerAt(face, x + 0.5, y + 0.5, z + 0.5).slot());
        }
    }

    @Test
    void depthIntoIsSignedDistanceFromTheDiagonal() {
        CubeFace up = CubeFace.UP;
        double edge = DEFAULT.centerX(up) + DEFAULT.radius;
        // On the diagonal above the edge: as high above the plane as it is past the edge.
        assertEquals(0.0, DEFAULT.depthInto(up, CubeFace.EAST, edge + 5, DEFAULT.planeY + 5, DEFAULT.centerZ()), 1e-9);
        assertTrue(DEFAULT.depthInto(up, CubeFace.EAST, edge + 7, DEFAULT.planeY + 5, DEFAULT.centerZ()) > 0);
        assertEquals(-Math.sqrt(2), DEFAULT.depthInto(up, CubeFace.EAST, edge + 3, DEFAULT.planeY + 5, DEFAULT.centerZ()), 1e-9);
        assertEquals(CubeFace.EAST, DEFAULT.ownerAt(up, edge + 7, DEFAULT.planeY + 5, DEFAULT.centerZ()));
    }

    /** A point s short of the shared edge at height h lands s past the neighbour's edge at height h. */
    @Test
    void unfoldContinuesTheNeighboursGroundPastItsEdge() {
        for (CubeGeometry g : new CubeGeometry[] {DEFAULT, ODD}) {
            for (CubeFace from : CubeFace.values()) {
                for (CubeFace to : CubeFace.values()) {
                    if (!from.isNeighbour(to)) continue;
                    Random random = new Random(from.ordinal() * 7 + to.ordinal());
                    for (int i = 0; i < 200; i++) {
                        double u = (random.nextDouble() * 2 - 1) * g.radius;
                        double v = (random.nextDouble() * 2 - 1) * g.radius;
                        double h = random.nextDouble() * 100 - 30;
                        double x = g.centerX(from) + u, y = g.planeY + h, z = g.centerZ() + v;
                        double[] q = g.unfold(from, to, x, y, z);
                        assertEquals(y, q[1], 1e-9, "height kept");
                        assertEquals(to, g.faceAt(q[0], q[2]));
                        // Distance from the edge on 'from' equals distance past the edge on 'to'.
                        double[] c = g.toCube(from, x, y, z);
                        double shortOfEdge = g.radius - c[to.axis] * to.sign;
                        double[] cq = g.toCube(to, q[0], q[1], q[2]);
                        assertEquals(shortOfEdge, cq[from.axis] * from.sign - g.radius, 1e-9);
                        // Points on the edge line itself stay put.
                        double[] onEdge = g.toCube(from, x, g.planeY, z);
                        onEdge[to.axis] = g.radius * to.sign;
                        double[] e = g.fromCube(from, onEdge);
                        assertArrayEquals(g.transform(from, to, e[0], e[1], e[2]), g.unfold(from, to, e[0], e[1], e[2]), 1e-9);
                    }
                    // Unfolding is an isometry: distances between points on 'from' are kept.
                    double[] a = g.unfold(from, to, g.centerX(from) + 10, g.planeY + 3, g.centerZ() - 20);
                    double[] b = g.unfold(from, to, g.centerX(from) - 40, g.planeY + 30, g.centerZ() + 7);
                    assertEquals(Math.sqrt(50 * 50 + 27 * 27 + 27 * 27), Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2)), 1e-9);
                }
            }
        }
    }

    @Test
    void storageLayout() {
        for (CubeGeometry g : new CubeGeometry[] {SMALL, DEFAULT, ODD}) {
            // UP's base square holds the origin chunk and is a whole number of chunks.
            assertTrue(g.minChunkX(CubeFace.UP) <= 0 && g.minChunkX(CubeFace.UP) + g.faceChunks > 0);
            assertTrue(g.minChunkZ() <= 0 && g.minChunkZ() + g.faceChunks > 0);
            for (CubeFace face : CubeFace.values()) {
                assertEquals(0, (g.centerX(face) - g.radius) & 15, "base square starts on a chunk");
                assertEquals(face, g.faceAt(g.centerX(face), g.centerZ()));
                assertEquals(face, g.faceAt(g.centerX(face) + g.footprint, g.centerZ() - g.footprint));
                // A player at the edge of the footprint with view distance 32 still sees only this face's storage.
                int viewBlocks = 33 * 16;
                assertEquals(face, g.faceAt(g.centerX(face) + g.footprint + viewBlocks, g.centerZ()));
                assertEquals(face, g.faceAt(g.centerX(face) - g.footprint - viewBlocks, g.centerZ()));
                assertTrue(g.inFootprint((g.centerX(face) - g.footprint) >> 4, g.centerZ() >> 4));
                assertFalse(g.inFootprint((g.centerX(face) + g.footprint + 16) >> 4, g.centerZ() >> 4));
            }
            assertNull(g.faceAt(g.centerX(CubeFace.UP), g.centerZ() + 8 * g.spacingChunks));
            assertNull(g.faceAt(g.centerX(CubeFace.DOWN) + 8 * g.spacingChunks, g.centerZ()));
            // The highest owned or barrier cell reaches exactly the footprint.
            CubeFace up = CubeFace.UP;
            assertTrue(g.isBarrier(up, g.centerX(up) + g.footprint - 1, g.maxY - 1, g.centerZ()));
            assertTrue(g.isBarrier(up, g.centerX(up) - g.footprint, g.maxY - 1, g.centerZ()));
        }
    }

    /**
     * A band cell (up to {@link CubeGeometry#BAND} under its column's barrier) reads a cell the neighbour owns, up to
     * BAND above that face's barrier, or a barrier cell there; and that cell maps straight back.
     */
    @Test
    void bandCellsReadTheNeighboursCellsNearItsBarrier() {
        for (CubeFace face : CubeFace.values()) {
            int cx = SMALL.centerX(face), cz = SMALL.centerZ();
            int reach = SMALL.radius + 8;
            for (int x = cx - reach; x < cx + reach; x++) {
                for (int z = cz - reach; z < cz + reach; z++) {
                    int barrierY = SMALL.barrierY(face, x, z);
                    for (int y = barrierY - CubeGeometry.BAND - 2; y <= barrierY + 1; y++) {
                        CubeGeometry.Cell cell = SMALL.bandSource(face, x, y, z);
                        int depth = barrierY - y;
                        if (depth < 1 || depth > CubeGeometry.BAND) {
                            assertNull(cell, "cell " + depth + " under the barrier is not in the band");
                            continue;
                        }
                        if (cell == null) {
                            // Only possible on a cube this small, where the band reaches its centre.
                            assertEquals(face.opposite().slot(), SMALL.cellOwner(face, x, y, z), face + " band cell has no source: " + x + " " + y + " " + z);
                            continue;
                        }
                        assertTrue(cell.face() != face && cell.face() != face.opposite(), face + " band cell reads " + cell.face());
                        int above = cell.y() - SMALL.barrierY(cell.face(), cell.x(), cell.z());
                        int owner = SMALL.cellOwner(cell.face(), cell.x(), cell.y(), cell.z());
                        if (owner == CubeGeometry.BARRIER) {
                            assertEquals(0, above, "a barrier source is its column's barrier cell");
                        } else {
                            assertEquals(cell.face().slot(), owner, "source cell is owned by the face it is read from");
                            assertTrue(above >= 1 && above <= CubeGeometry.BAND, "source cell " + above + " above its barrier");
                        }
                        assertArrayEquals(new int[] {x, y, z}, SMALL.transformBlock(cell.face(), face, cell.x(), cell.y(), cell.z()));
                    }
                }
            }
        }
    }

    /** Every cell a face owns within the band above its barrier is read by the band of each face it shares that barrier with. */
    @Test
    void neighboursBandCellsAreEachReadOnce() {
        for (CubeFace face : CubeFace.values()) {
            int cx = SMALL.centerX(face), cz = SMALL.centerZ();
            int reach = SMALL.radius + 8;
            for (int x = cx - reach; x < cx + reach; x++) {
                for (int z = cz - reach; z < cz + reach; z++) {
                    int barrierY = SMALL.barrierY(face, x, z);
                    if (barrierY < SMALL.minY || barrierY >= SMALL.maxY) continue;
                    for (CubeFace partner : SMALL.barrierFaces(face, x, barrierY, z)) {
                        if (partner == face) continue;
                        for (int k = 1; k <= CubeGeometry.BAND; k++) {
                            if (!SMALL.isOwned(face, x, barrierY + k, z)) continue;
                            int[] there = SMALL.transformBlock(face, partner, x, barrierY + k, z);
                            CubeGeometry.Cell cell = SMALL.bandSource(partner, there[0], there[1], there[2]);
                            assertTrue(cell != null && cell.face() == face && cell.x() == x && cell.y() == barrierY + k && cell.z() == z,
                                face + " cell " + k + " above its barrier is not read by " + partner + ": " + cell);
                        }
                    }
                }
            }
        }
    }
}
