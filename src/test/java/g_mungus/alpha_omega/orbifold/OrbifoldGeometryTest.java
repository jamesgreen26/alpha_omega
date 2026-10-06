package g_mungus.alpha_omega.orbifold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.command.OrbifoldCommand;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** {@link OrbifoldGeometry} against {@code orbifold-implementation.md} phase 1 "Tests". */
class OrbifoldGeometryTest {

    private static final OrbifoldGeometry DEFAULT = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);

    /**
     * Every size, with the default band, and the smallest and largest bands at the two smallest sizes; and every size's
     * Nether (1:8) with the default band, and the smallest Nether with its deepest and shallowest bands.
     */
    private static List<OrbifoldGeometry> all() {
        List<OrbifoldGeometry> all = new ArrayList<>(List.of(new OrbifoldGeometry(OrbifoldSize.SMALL, 4), new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4), DEFAULT,
            new OrbifoldGeometry(OrbifoldSize.LARGE, 4), new OrbifoldGeometry(OrbifoldSize.MEDIUM, 2), new OrbifoldGeometry(OrbifoldSize.MEDIUM, 16),
            new OrbifoldGeometry(OrbifoldSize.SMALL, 2), new OrbifoldGeometry(OrbifoldSize.SMALL, 16)));
        all.addAll(nether());
        return all;
    }

    /** Every size's Nether with the default band, and the smallest Nether's shallowest and deepest bands. */
    static List<OrbifoldGeometry> nether() {
        List<OrbifoldGeometry> nether = new ArrayList<>();
        for (OrbifoldSize size : OrbifoldSize.PRESETS) nether.add(new OrbifoldGeometry(size, 4, OrbifoldGeometry.NETHER_SCALE));
        nether.add(new OrbifoldGeometry(OrbifoldSize.SMALL, 2, OrbifoldGeometry.NETHER_SCALE));
        nether.add(new OrbifoldGeometry(OrbifoldSize.SMALL, 5, OrbifoldGeometry.NETHER_SCALE));
        return nether;
    }

    /** Calls {@code action} on every cell of the footprint outside the tile (band and skirt). */
    private static void forEachBandCell(OrbifoldGeometry g, IntBinaryOperator action) {
        for (int x = g.minX - g.reach; x < g.maxX + g.reach; x++) {
            boolean edgeColumn = x < g.minX || x >= g.maxX;
            for (int z = g.northRow - g.reach; z < g.southRow + g.reach; z++) {
                if (!edgeColumn && z == g.northRow) z = g.southRow;
                action.applyAsInt(x, z);
            }
        }
    }

    /** Calls {@code action} on every tile cell within the footprint's reach of the tile's edge. */
    private static void forEachEdgeTileCell(OrbifoldGeometry g, IntBinaryOperator action) {
        for (int x = g.minX; x < g.maxX; x++) {
            boolean edgeColumn = x < g.minX + g.reach || x >= g.maxX - g.reach;
            for (int z = g.northRow; z < g.southRow; z++) {
                if (!edgeColumn && z == g.northRow + g.reach) z = Math.max(z, g.southRow - g.reach);
                action.applyAsInt(x, z);
            }
        }
    }

    @Test
    void sizesMatchTheWrappingPlan() {
        OrbifoldGeometry k2 = new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4), k8 = new OrbifoldGeometry(OrbifoldSize.LARGE, 4);
        OrbifoldGeometry small = new OrbifoldGeometry(OrbifoldSize.SMALL, 4);
        assertEquals(List.of(3584, 3072, -1152, 384, 0, 49), List.of(small.a, small.b, small.northRow, small.southRow, small.spawnX, small.spawnZ));
        assertEquals(List.of(7680, 6656, -2560, 768, 0, 42), List.of(k2.a, k2.b, k2.northRow, k2.southRow, k2.spawnX, k2.spawnZ));
        assertEquals(List.of(15360, 13312, -5248, 1408, 0, -44), List.of(DEFAULT.a, DEFAULT.b, DEFAULT.northRow, DEFAULT.southRow, DEFAULT.spawnX, DEFAULT.spawnZ));
        assertEquals(List.of(30720, 26624, -10368, 2944, 0, 40), List.of(k8.a, k8.b, k8.northRow, k8.southRow, k8.spawnX, k8.spawnZ));
        assertEquals(List.of(64, 32, 80), List.of(DEFAULT.band, DEFAULT.claim, DEFAULT.reach));
        // Footprint 15520 x 6816 at k = 4 (plan §2.4).
        int[] chunks = DEFAULT.footprintChunks();
        assertEquals(15520, (chunks[2] - chunks[0] + 1) * 16);
        assertEquals(6816, (chunks[3] - chunks[1] + 1) * 16);
        for (OrbifoldGeometry g : all()) {
            assertTrue(g.isTile(g.spawnX, g.spawnZ));
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
                assertEquals(0, cone.x() % (128 / g.scale), cone.name());
                assertEquals(0, cone.z() % (128 / g.scale), cone.name());
            }
        }
    }

    /**
     * The Nether is every preset at 1:8 (wrapping plan §4): at k = 4 a 1920 × 1664 lattice with fold rows at −656 and
     * 176, cone points at x = 0, ±960 and ±480. Everything is chunk-aligned and the band keeps its depth in chunks.
     */
    @Test
    void theNetherIsEveryPresetAtOneEighth() {
        OrbifoldGeometry normal = DEFAULT.scaled(OrbifoldGeometry.NETHER_SCALE);
        assertEquals(List.of(1920, 1664, -656, 176, -960, 960), List.of(normal.a, normal.b, normal.northRow, normal.southRow, normal.minX, normal.maxX));
        assertEquals(List.of(new OrbifoldGeometry.ConePoint("N", 0, -656), new OrbifoldGeometry.ConePoint("F", 960, -656),
            new OrbifoldGeometry.ConePoint("E", 480, 176), new OrbifoldGeometry.ConePoint("W", -480, 176)), normal.conePoints());
        assertEquals(List.of(64, 32, 80), List.of(normal.band, normal.claim, normal.reach));
        OrbifoldGeometry small = new OrbifoldGeometry(OrbifoldSize.SMALL, 4, 8);
        assertEquals(List.of(448, 384, -144, 48), List.of(small.a, small.b, small.northRow, small.southRow));
        assertEquals(List.of(960, 832, -320, 96), List.of(new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4, 8).a, new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4, 8).b,
            new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4, 8).northRow, new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4, 8).southRow));
        OrbifoldGeometry large = new OrbifoldGeometry(OrbifoldSize.LARGE, 4, 8);
        assertEquals(List.of(3840, 3328, -1296, 368), List.of(large.a, large.b, large.northRow, large.southRow));
        for (OrbifoldGeometry g : nether()) {
            OrbifoldGeometry overworld = new OrbifoldGeometry(g.size, g.bandChunks);
            assertEquals(List.of(overworld.a, overworld.b, overworld.northRow, overworld.southRow),
                List.of(8 * g.a, 8 * g.b, 8 * g.northRow, 8 * g.southRow), g.toString());
            for (Motion m : elementsNearTheTile(g)) assertTrue(m.chunkAligned(), g + " " + m);
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
                assertEquals(8 * cone.x(), overworld.conePoints().stream().filter(c -> c.name().equals(cone.name())).findFirst().orElseThrow().x());
                assertEquals(0, cone.x() % 16, g + " " + cone);
                assertEquals(0, cone.z() % 16, g + " " + cone);
            }
            assertTrue(g.isTile(g.spawnX, g.spawnZ));
            assertTrue(g.isScaled());
        }
        // The band is kept in chunks where the tile has room (half the tile's height for the band and skirt), and the
        // settings fall back to the deepest band that fits: the small Nether's tile is 12 chunks tall.
        assertEquals(4, new OrbifoldSettings(OrbifoldSize.SMALL, 4, 8).geometry().bandChunks);
        assertEquals(5, new OrbifoldSettings(OrbifoldSize.SMALL, 16, 8).geometry().bandChunks);
        assertEquals(12, new OrbifoldSettings(OrbifoldSize.MEDIUM, 16, 8).geometry().bandChunks);
        assertEquals(16, new OrbifoldSettings(OrbifoldSize.LARGE, 16, 8).geometry().bandChunks);
        assertEquals(16, new OrbifoldSettings(OrbifoldSize.SMALL, 16).geometry().bandChunks);
        assertFalse(OrbifoldGeometry.fitsBand(OrbifoldSize.SMALL, 6, 8));
    }

    /** The presets: the k sizes are 3840·k × 3328·k, ids and size factors look them up, and the default is k = 4. */
    @Test
    void presetsAreTheSizes() {
        assertEquals(List.of(OrbifoldSize.SMALL, OrbifoldSize.MEDIUM, OrbifoldSize.NORMAL, OrbifoldSize.LARGE), OrbifoldSize.PRESETS);
        assertEquals(OrbifoldSize.NORMAL, OrbifoldSize.DEFAULT);
        assertEquals(4, OrbifoldSize.DEFAULT.sizeFactor());
        assertEquals(List.of(2, 4, 8), OrbifoldSize.SIZE_FACTORS);
        for (OrbifoldSize size : OrbifoldSize.PRESETS) {
            assertEquals(size, OrbifoldSize.byId(size.id()).orElseThrow());
            if (size.sizeFactor() != 0) {
                assertEquals(List.of(3840 * size.sizeFactor(), 3328 * size.sizeFactor()), List.of(size.a(), size.b()));
                assertEquals(size, OrbifoldSize.bySizeFactor(size.sizeFactor()).orElseThrow());
            }
        }
        assertTrue(OrbifoldSize.bySizeFactor(0).isEmpty() && OrbifoldSize.bySizeFactor(3).isEmpty());
        // The small size: b/a 1.0% short of √3/2, and about 5.5 M blocks² of area (the tile, a·b/2).
        OrbifoldSize small = OrbifoldSize.SMALL;
        assertEquals(0.0103, 1 - (double) small.b() / small.a() / (Math.sqrt(3) / 2), 0.0001);
        assertEquals(5.5e6, small.a() * (double) small.b() / 2, 0.01e6);
    }

    /**
     * Every element of Γ maps chunks to whole chunks in the overworld and in the Nether at 1:8: {@code a} is a multiple
     * of 512 (cone points at ±a/4), {@code b/2} and the fold rows of 128, so every cone point is on a multiple of 128,
     * and the generators of the 1:8 lattice are chunk-aligned too.
     */
    @Test
    void everySizeIsChunkAlignedInTheNether() {
        for (OrbifoldSize size : OrbifoldSize.PRESETS) {
            assertEquals(0, size.a() % 512, size.id());
            assertEquals(0, (size.b() / 2) % 128, size.id());
            assertEquals(0, size.northRow() % 128, size.id());
            OrbifoldGeometry g = new OrbifoldGeometry(size, 4);
            assertEquals(0, g.southRow % 128, size.id());
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
                assertEquals(0, cone.x() % 128, size.id() + " " + cone.name());
                assertEquals(0, cone.z() % 128, size.id() + " " + cone.name());
            }
            for (Motion m : elementsNearTheTile(g)) assertTrue(m.chunkAligned(), size.id() + " " + m);
            // The Nether: the same layout scaled by 1/8.
            int a8 = g.a / 8, b8 = g.b / 8, north8 = g.northRow / 8, south8 = g.southRow / 8;
            assertEquals(g.a, 8 * a8);
            assertEquals(g.northRow, 8 * north8);
            assertEquals(g.southRow, 8 * south8);
            List<Motion> nether = new ArrayList<>(List.of(Motion.translation(a8, 0), Motion.translation(-a8, 0), Motion.translation(a8 / 2, b8),
                Motion.halfTurn(0, 2 * north8), Motion.halfTurn(a8 / 2, 2 * south8), Motion.halfTurn(-a8 / 2, 2 * south8),
                Motion.halfTurn(a8, 2 * north8)));
            for (Motion m : nether) assertTrue(m.chunkAligned(), size.id() + " Nether " + m);
            for (int c : new int[] {a8 / 2, a8 / 4, north8, south8}) assertEquals(0, c % 16, size.id() + " Nether cone point coordinate " + c);
        }
    }

    @Test
    void generatorsComposeAsTheGroup() {
        for (OrbifoldGeometry g : all()) {
            assertTrue(g.northFold.then(g.northFold).isIdentity(), "R_N² = id");
            assertTrue(g.southFold.then(g.southFold).isIdentity(), "R_S² = id");
            assertTrue(g.east.then(g.west).isIdentity(), "T+ T− = id");
            // R_S ∘ R_N is the lattice vector L2 = (a/2, b); R_N ∘ R_S is −L2.
            assertEquals(Motion.translation(g.a / 2, g.b), g.northFold.then(g.southFold));
            assertEquals(Motion.translation(-g.a / 2, -g.b), g.southFold.then(g.northFold));
            for (Motion m : g.generators()) {
                assertTrue(m.then(m.inverse()).isIdentity());
                assertTrue(m.inverse().then(m).isIdentity());
            }
            // Composition is associative and agrees with applying one after the other.
            Motion abc = g.northFold.then(g.east).then(g.southFold);
            assertEquals(abc, g.northFold.then(g.east.then(g.southFold)));
            assertEquals(g.southFold.cellX(g.east.cellX(g.northFold.cellX(123))), abc.cellX(123));
            assertEquals(g.southFold.cellZ(g.east.cellZ(g.northFold.cellZ(-77))), abc.cellZ(-77));
        }
    }

    /** The cell maps of plan §2.2, literally. */
    @Test
    void generatorsAreTheCellMapsOfThePlan() {
        OrbifoldGeometry g = DEFAULT;
        int x = 1234, z = -5250;
        assertEquals(List.of(x + 15360, z), List.of(g.east.cellX(x), g.east.cellZ(z)));
        assertEquals(List.of(-1 - x, 2 * -5248 - 1 - z), List.of(g.northFold.cellX(x), g.northFold.cellZ(z)));
        assertEquals(List.of(7680 - 1 - x, 2 * 1408 - 1 - z), List.of(g.southFold.cellX(x), g.southFold.cellZ(z)));
    }

    @Test
    void everyFootprintCellHasOneSourceInTheTile() {
        for (OrbifoldGeometry g : all()) {
            int[] count = {0};
            forEachBandCell(g, (x, z) -> {
                OrbifoldGeometry.Cell source = g.canon(x, z);
                assertTrue(g.isTile(source.x(), source.z()), "(" + x + ", " + z + ") -> " + source);
                assertEquals(source.x(), source.frame().cellX(x));
                assertEquals(source.z(), source.frame().cellZ(z));
                assertFalse(source.frame().isIdentity());
                assertEquals(source, g.canon(x, z), "canon is a function");
                count[0]++;
                return 0;
            });
            int side = 2 * g.reach;
            assertEquals((long) (g.a + side) * (g.b / 2 + side) - (long) g.a * (g.b / 2), count[0], "band and skirt cells");
        }
        // Tile cells are their own source: all of them at the smallest size, the edges at the others.
        OrbifoldGeometry small = new OrbifoldGeometry(OrbifoldSize.MEDIUM, 4);
        for (int x = small.minX; x < small.maxX; x++) {
            for (int z = small.northRow; z < small.southRow; z++) {
                assertTrue(small.frame(x, z).isIdentity());
            }
        }
        for (OrbifoldGeometry g : all()) {
            forEachEdgeTileCell(g, (x, z) -> {
                assertEquals(new OrbifoldGeometry.Cell(x, z, Motion.IDENTITY), g.canon(x, z));
                return 0;
            });
        }
    }

    /** No two tile cells are the same place: the tile is a fundamental domain. */
    @Test
    void noTwoTileCellsAreIdentified() {
        for (OrbifoldGeometry g : List.of(DEFAULT, new OrbifoldGeometry(OrbifoldSize.MEDIUM, 16), new OrbifoldGeometry(OrbifoldSize.SMALL, 16))) {
            List<Motion> elements = elementsNearTheTile(g);
            forEachEdgeTileCell(g, (x, z) -> {
                for (Motion m : elements) {
                    if (m.isIdentity()) continue;
                    assertFalse(g.isTile(m.cellX(x), m.cellZ(z)), m + " takes tile cell (" + x + ", " + z + ") into the tile");
                }
                return 0;
            });
        }
    }

    /** The elements of Γ that move tile cells near the footprint: generators and their pairwise compositions. */
    private static List<Motion> elementsNearTheTile(OrbifoldGeometry g) {
        Set<Motion> elements = new HashSet<>();
        List<Motion> generators = new ArrayList<>(g.generators());
        generators.add(Motion.IDENTITY);
        for (Motion a : generators) {
            for (Motion b : generators) elements.add(a.then(b));
        }
        return List.copyOf(elements);
    }

    /** Wrapping plan §4: every identification at the default size gives the same canonical cell. */
    @Test
    void wrappingPlanIdentificationsAgree() {
        OrbifoldGeometry g = DEFAULT;
        Random random = new Random(4);
        int checked = 0;
        for (int i = 0; i < 200000; i++) {
            int x = g.minX - g.reach + random.nextInt(g.a + 2 * g.reach);
            int z = g.northRow - g.reach + random.nextInt(g.b / 2 + 2 * g.reach);
            OrbifoldGeometry.Cell source = g.canon(x, z);
            // The cell versions of (x ± 15360, z), (x ± 7680, z ± 13312) and (−x, −10496 − z).
            int[][] images = {{x + 15360, z}, {x - 15360, z}, {x + 7680, z + 13312}, {x - 7680, z - 13312}, {x + 7680, z - 13312},
                {x - 7680, z + 13312}, {-1 - x, -10496 - 1 - z}};
            for (int[] image : images) {
                if (!g.inFootprint(image[0], image[1])) continue;
                OrbifoldGeometry.Cell other = g.canon(image[0], image[1]);
                assertEquals(List.of(source.x(), source.z()), List.of(other.x(), other.z()), "(" + x + ", " + z + ") and " + image[0] + ", " + image[1]);
                checked++;
            }
        }
        assertTrue(checked > 1000, "only " + checked + " identifications landed in the footprint");
        // And for every size: the lattice and the half turn about every cone point.
        for (OrbifoldGeometry k : all()) {
            List<Motion> identifications = new ArrayList<>(List.of(k.east, k.west, Motion.translation(k.a / 2, k.b), Motion.translation(-k.a / 2, -k.b),
                Motion.translation(k.a / 2, -k.b), Motion.translation(-k.a / 2, k.b)));
            for (OrbifoldGeometry.ConePoint cone : k.conePoints()) identifications.add(Motion.halfTurn(2 * cone.x(), 2 * cone.z()));
            for (int i = 0; i < 20000; i++) {
                int x = k.minX - k.reach + random.nextInt(k.a + 2 * k.reach);
                int z = k.northRow - k.reach + random.nextInt(k.b / 2 + 2 * k.reach);
                OrbifoldGeometry.Cell source = k.canon(x, z);
                for (Motion m : identifications) {
                    int ix = m.cellX(x), iz = m.cellZ(z);
                    if (!k.inFootprint(ix, iz)) continue;
                    OrbifoldGeometry.Cell other = k.canon(ix, iz);
                    assertEquals(List.of(source.x(), source.z()), List.of(other.x(), other.z()), m + " at (" + x + ", " + z + ")");
                }
            }
        }
    }

    /** No cell or chunk is its own image: half turns fix only lattice points on chunk corners, never a cell. */
    @Test
    void noCellOrChunkIsItsOwnImage() {
        for (OrbifoldGeometry g : List.of(DEFAULT, new OrbifoldGeometry(OrbifoldSize.MEDIUM, 16), new OrbifoldGeometry(OrbifoldSize.SMALL, 16))) {
            List<Motion> elements = elementsNearTheTile(g);
            List<Motion> turns = new ArrayList<>();
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) turns.add(Motion.halfTurn(2 * cone.x(), 2 * cone.z()));
            // Around each cone point, where a half turn comes closest to fixing something.
            for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
                for (int x = cone.x() - 40; x < cone.x() + 40; x++) {
                    for (int z = cone.z() - 40; z < cone.z() + 40; z++) {
                        for (Motion m : turns) assertFalse(m.cellX(x) == x && m.cellZ(z) == z, m + " fixes (" + x + ", " + z + ")");
                        if (g.inFootprint(x, z)) {
                            Motion frame = g.frame(x, z);
                            assertFalse(!frame.isIdentity() && frame.cellX(x) == x && frame.cellZ(z) == z);
                        }
                    }
                }
                for (int cx = (cone.x() >> 4) - 3; cx < (cone.x() >> 4) + 3; cx++) {
                    for (int cz = (cone.z() >> 4) - 3; cz < (cone.z() >> 4) + 3; cz++) {
                        for (Motion m : turns) assertFalse(m.chunkX(cx) == cx && m.chunkZ(cz) == cz, m + " fixes chunk " + cx + ", " + cz);
                    }
                }
            }
            forEachBandCell(g, (x, z) -> {
                for (Motion m : elements) {
                    if (!m.isIdentity()) assertFalse(m.cellX(x) == x && m.cellZ(z) == z, m + " fixes (" + x + ", " + z + ")");
                }
                return 0;
            });
        }
    }

    /** Copy sets are complete: {@code c ∈ copies(s)} exactly when {@code canon(c) = s}. */
    @Test
    void copySetsAreComplete() {
        for (OrbifoldGeometry g : all()) {
            long[] fromBand = {0};
            forEachBandCell(g, (x, z) -> {
                OrbifoldGeometry.Cell source = g.canon(x, z);
                assertTrue(g.copies(source.x(), source.z()).contains(new OrbifoldGeometry.Cell(x, z, source.frame())),
                    "(" + x + ", " + z + ") is missing from the copies of " + source);
                fromBand[0]++;
                return 0;
            });
            long[] fromTile = {0};
            int[] most = {0};
            forEachEdgeTileCell(g, (x, z) -> {
                List<OrbifoldGeometry.Cell> copies = g.copies(x, z);
                most[0] = Math.max(most[0], copies.size());
                for (OrbifoldGeometry.Cell copy : copies) {
                    assertFalse(g.isTile(copy.x(), copy.z()));
                    assertTrue(g.inFootprint(copy.x(), copy.z()));
                    OrbifoldGeometry.Cell back = g.canon(copy.x(), copy.z());
                    assertEquals(new OrbifoldGeometry.Cell(x, z, copy.frame()), back, "copy " + copy + " of (" + x + ", " + z + ")");
                }
                fromTile[0] += copies.size();
                return 0;
            });
            assertEquals(fromBand[0], fromTile[0], "every band and skirt cell is one copy of one tile cell");
            assertEquals(3, most[0], "near a corner or cone point a cell has three copies");
            int cx = g.minX + g.reach + 5, cz = g.northRow + g.reach + 5;
            // (Unless the tile is barely twice the reach, as the smallest Nether with its deepest band.)
            if (g.b / 2 > 2 * g.reach + 10) assertTrue(g.copies(cx, cz).isEmpty() && g.copies(0, (g.northRow + g.southRow) / 2).isEmpty(), "inner cells have none");
        }
    }

    /** {@code between(a, b)} takes each copy of a cell to each other copy (and to its source), and back by its inverse. */
    @Test
    void betweenTakesCopyToCopy() {
        for (OrbifoldGeometry g : all()) {
            int[] checked = {0};
            forEachEdgeTileCell(g, (x, z) -> {
                List<int[]> cells = new ArrayList<>();
                cells.add(new int[] {x, z});
                for (OrbifoldGeometry.Cell copy : g.copies(x, z)) cells.add(new int[] {copy.x(), copy.z()});
                for (int[] a : cells) {
                    for (int[] b : cells) {
                        Motion m = g.between(a[0], a[1], b[0], b[1]);
                        assertEquals(b[0], m.cellX(a[0]), "between x");
                        assertEquals(b[1], m.cellZ(a[1]), "between z");
                        assertEquals(m.inverse(), g.between(b[0], b[1], a[0], a[1]));
                        if (a == b) assertTrue(m.isIdentity());
                        assertEquals(m, g.between(a[0] & ~15, a[1] & ~15, b[0] & ~15, b[1] & ~15), "the same for the whole chunk");
                        checked[0]++;
                    }
                }
                return 0;
            });
            assertTrue(checked[0] > 0);
        }
    }

    /** Near N the band past the north fold holds the turned cells just south of N: the visible "appears twice". */
    @Test
    void theBandNearNHoldsCellsBesideIt() {
        OrbifoldGeometry g = DEFAULT;
        // Standing at (5, zN + 3), facing north over the fold: (−6, zN − 4) is the same block.
        OrbifoldGeometry.Cell source = g.canon(-6, g.northRow - 4);
        assertEquals(new OrbifoldGeometry.Cell(5, g.northRow + 3, g.northFold), source);
        assertTrue(g.copies(5, g.northRow + 3).contains(new OrbifoldGeometry.Cell(-6, g.northRow - 4, g.northFold)));
    }

    @Test
    void everyChunkMapsToAWholeChunk() {
        for (OrbifoldGeometry g : all()) {
            for (Motion m : elementsNearTheTile(g)) assertTrue(m.chunkAligned(), m.toString());
            int[] bounds = g.footprintChunks();
            for (int cx = bounds[0]; cx <= bounds[2]; cx++) {
                boolean edgeColumn = cx < (g.minX + g.reach >> 4) + 1 || cx > (g.maxX - g.reach >> 4) - 1;
                for (int cz = bounds[1]; cz <= bounds[3]; cz++) {
                    if (!edgeColumn && cz == (g.northRow + g.reach >> 4) + 1) cz = Math.max(cz, (g.southRow - g.reach >> 4) - 1);
                    Motion frame = g.frameChunk(cx, cz);
                    OrbifoldGeometry.Cell source = g.canonChunk(cx, cz);
                    assertTrue(g.isTileChunk(source.x(), source.z()));
                    boolean tile = g.isTileChunk(cx, cz), band = g.isBandChunk(cx, cz), skirt = g.isSkirtChunk(cx, cz);
                    assertEquals(1, (tile ? 1 : 0) + (band ? 1 : 0) + (skirt ? 1 : 0), "chunk " + cx + ", " + cz);
                    int deepest = 0;
                    for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                        for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                            deepest = Math.max(deepest, g.cellDepth(x, z));
                            assertEquals(frame, g.frame(x, z), "frame of (" + x + ", " + z + ")");
                            assertEquals(source.x(), frame.cellX(x) >> 4);
                            assertEquals(source.z(), frame.cellZ(z) >> 4);
                            assertEquals(tile, g.isTile(x, z));
                            assertEquals(band, g.isBand(x, z));
                            assertEquals(skirt, g.isSkirt(x, z));
                        }
                    }
                    assertEquals((deepest + 15) / 16, g.chunkDepth(cx, cz), "depth of chunk " + cx + ", " + cz);
                    if (!tile) assertTrue(g.copiesChunk(source.x(), source.z()).contains(new OrbifoldGeometry.Cell(cx, cz, frame)));
                }
            }
            assertEquals(1, DEFAULT.chunkDepth(DEFAULT.maxX >> 4, 0));
            assertEquals(4, DEFAULT.chunkDepth(-1, (DEFAULT.northRow >> 4) - 4));
            assertTrue(DEFAULT.isBandChunk(-1, (DEFAULT.northRow >> 4) - 4) && DEFAULT.isSkirtChunk(-1, (DEFAULT.northRow >> 4) - 5));
        }
    }

    @Test
    void framesInTheBandAreFew() {
        // Plan §2.4: at most four distinct frames per neighbourhood (id, T, a fold, fold∘T near F).
        OrbifoldGeometry g = DEFAULT;
        Set<Motion> nearF = new HashSet<>();
        for (int x = g.maxX - 100; x < g.maxX + g.reach; x++) {
            for (int z = g.northRow - g.reach; z < g.northRow + 100; z++) nearF.add(g.frame(x, z));
        }
        assertEquals(Set.of(Motion.IDENTITY, g.west, g.northFold, g.northFold.then(g.east)), nearF);
    }

    @Test
    void seamDepthIsChebyshevPastTheEdge() {
        OrbifoldGeometry g = DEFAULT;
        assertEquals(0.5, g.seamDepth(g.maxX + 0.5, 0), 1e-12);
        assertEquals(-10.0, g.seamDepth(0, g.northRow + 10), 1e-12);
        assertEquals(3.0, g.seamDepth(g.minX - 2, g.southRow + 3), 1e-12);
        // Spawn is nearer the south fold than the north one.
        assertEquals(g.spawnZ - (double) g.southRow, g.seamDepth(g.spawnX, g.spawnZ), 1e-12);
    }

    @Test
    void transformRoundTrips() {
        Random random = new Random(7);
        for (Motion m : elementsNearTheTile(DEFAULT)) {
            Transform there = Transform.of(m), back = OrbifoldGeometry.transform(m.inverse());
            for (int i = 0; i < 100; i++) {
                Vec3 p = new Vec3(random.nextDouble() * 20000 - 10000, random.nextDouble() * 300 - 64, random.nextDouble() * 20000 - 10000);
                Vec3 q = back.position(there.position(p));
                assertEquals(p.x, q.x, 1e-9);
                assertEquals(p.y, q.y, 0.0);
                assertEquals(p.z, q.z, 1e-9);
                Vec3 v = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
                assertEquals(v, back.vector(there.vector(v)));
                assertEquals(v.length(), there.vector(v).length(), 1e-12);
                BlockPos cell = BlockPos.containing(p);
                assertEquals(cell, back.block(there.block(cell)));
                // A cell's image holds its points' images.
                assertEquals(there.block(cell), BlockPos.containing(there.position(Vec3.atCenterOf(cell))));
                AABB box = new AABB(p, p.add(random.nextDouble() * 5, random.nextDouble() * 5, random.nextDouble() * 5));
                AABB moved = there.box(box);
                assertEquals(box.getXsize(), moved.getXsize(), 1e-9);
                assertEquals(box.getZsize(), moved.getZsize(), 1e-9);
                AABB round = back.box(moved);
                assertEquals(box.minX, round.minX, 1e-9);
                assertEquals(box.maxZ, round.maxZ, 1e-9);
                assertEquals(box.minY, round.minY, 0.0);
                float yaw = random.nextFloat() * 720 - 360;
                assertEquals(0.0, Math.IEEEremainder(back.yaw(there.yaw(yaw)) - yaw, 360.0), 1e-3);
                // Yaw turns with the look vector: vanilla's (−sin yaw, cos yaw).
                double r = Math.toRadians(there.yaw(yaw));
                Vec3 look = there.vector(new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))));
                assertEquals(look.x, -Math.sin(r), 1e-5);
                assertEquals(look.z, Math.cos(r), 1e-5);
            }
            for (Direction d : Direction.values()) {
                assertEquals(d, back.direction(there.direction(d)));
                Direction turned = there.direction(d);
                Vec3 normal = there.vector(Vec3.atLowerCornerOf(d.getNormal()));
                assertEquals(0.0, normal.distanceTo(Vec3.atLowerCornerOf(turned.getNormal())), 1e-12, d + " under " + m);
            }
            assertEquals(m.turned() ? Rotation.CLOCKWISE_180 : Rotation.NONE, there.rotation());
        }
        assertNotEquals(Motion.IDENTITY, DEFAULT.northFold);
    }

    /** {@code /orbifold info} at spawn and at the four cone points (plan phase 1 "Done when"). */
    @Test
    void infoReadsAtSpawnAndConePoints() {
        OrbifoldGeometry g = DEFAULT;
        List<String> spawn = OrbifoldCommand.lines(g, new Vec3(0.5, 70, -43.5));
        assertTrue(spawn.get(0).startsWith("orbifold normal (15360 x 13312, k=4): tile x -7680..7680, z -5248..1408"), spawn.get(0));
        List<String> small = OrbifoldCommand.lines(new OrbifoldGeometry(OrbifoldSize.SMALL, 4), new Vec3(0.5, 70, 49.5));
        assertTrue(small.get(0).startsWith("orbifold small (3584 x 3072): tile x -1792..1792, z -1152..384"), small.get(0));
        assertTrue(small.get(1).contains("N (0, -1152), F (1792, -1152), E (896, 384), W (-896, 384); spawn (0, 49)"), small.get(1));
        assertTrue(spawn.get(2).startsWith("Cell 0 -44: tile, 1451.5 short of the nearest seam; frame id"), spawn.get(2));
        assertTrue(spawn.get(3).equals("Source (0, -44); its copies: none"), spawn.get(3));
        assertTrue(spawn.get(1).contains("N (0, -5248), F (7680, -5248), E (3840, 1408), W (-3840, 1408)"), spawn.get(1));
        // Just north of N, across the fold: the source is the turned cell south of it, which has this one as a copy.
        List<String> n = OrbifoldCommand.lines(g, new Vec3(0.5, 70, -5248.5));
        assertTrue(n.get(2).startsWith("Cell 0 -5249: band, 0.5 past the nearest seam; frame turn(0, -10496)"), n.get(2));
        assertTrue(n.get(3).startsWith("Source (-1, -5248); its copies: (0, -5249) by turn(0, -10496)"), n.get(3));
        // At F, from the west end of the tile: three copies (east band, north band, and the corner).
        List<String> f = OrbifoldCommand.lines(g, new Vec3(-7679.5, 70, -5247.5));
        assertTrue(f.get(2).startsWith("Cell -7680 -5248: tile, 0.5 short of the nearest seam; frame id"), f.get(2));
        assertEquals(3, f.get(3).split(" by ").length - 1, f.get(3));
        // Past E and W, over the south fold.
        List<String> e = OrbifoldCommand.lines(g, new Vec3(3840.5, 70, 1408.5));
        assertTrue(e.get(2).startsWith("Cell 3840 1408: band, 0.5 past the nearest seam; frame turn(7680, 2816)"), e.get(2));
        assertTrue(e.get(3).startsWith("Source (3839, 1407)"), e.get(3));
        List<String> w = OrbifoldCommand.lines(g, new Vec3(-3839.5, 70, 1408.5));
        assertTrue(w.get(2).startsWith("Cell -3840 1408: band, 0.5 past the nearest seam; frame turn(-7680, 2816)"), w.get(2));
        assertTrue(w.get(3).startsWith("Source (-3841, 1407)"), w.get(3));
    }
}
