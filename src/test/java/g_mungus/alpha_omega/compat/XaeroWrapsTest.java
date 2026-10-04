package g_mungus.alpha_omega.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import g_mungus.alpha_omega.wrap.Wrap;
import org.junit.jupiter.api.Test;

class XaeroWrapsTest {

    private static final Wrap OVERWORLD = new Wrap(12288);
    private static final Wrap NETHER = new Wrap(1536);
    private static final Wrap CENTERED = new Wrap(12288, true);

    @Test
    void regionPeriods() {
        assertEquals(24, XaeroWraps.regionPeriod(OVERWORLD));
        assertEquals(3, XaeroWraps.regionPeriod(NETHER));
        assertEquals(0, XaeroWraps.regionPeriod(Wrap.NONE));
        assertEquals(0, XaeroWraps.regionPeriod(new Wrap(1000 * 16)), "not a whole number of regions");
    }

    @Test
    void leafRegionsCanonicalize() {
        assertEquals(0, XaeroWraps.canonRegion(24, 24, 0, 0));
        assertEquals(23, XaeroWraps.canonRegion(-1, 24, 0, 0));
        assertEquals(5, XaeroWraps.canonRegion(5 - 3 * 24, 24, 0, 0));
        assertEquals(-7, XaeroWraps.canonRegion(-7, 0, 0, 0), "unwrapped");
    }

    @Test
    void canonicalRegionsHoldTheCanonicalWindow() {
        // A centered Overworld is blocks [-6144, 6144): regions [-12, 12), the world save's region files.
        assertEquals(-12, XaeroWraps.regionOrigin(CENTERED));
        assertEquals(-1, XaeroWraps.canonRegion(-1, 24, -12, 0), "lap 0 is canonical");
        assertEquals(-1, XaeroWraps.canonRegion(23, 24, -12, 0));
        assertEquals(11, XaeroWraps.canonRegion(11 - 24, 24, -12, 0));
        assertEquals(0, XaeroWraps.regionOrigin(OVERWORLD));
        // A centered Nether starts half way into region -2.
        assertEquals(-2, XaeroWraps.regionOrigin(new Wrap(1536, true)));
    }

    @Test
    void leveledRegionsCanonicalizeWhenTheyTile() {
        // Level 3 regions span 8 leaves: 24 leaves are 3 of them.
        assertEquals(0, XaeroWraps.canonRegion(3, 24, 0, 3));
        assertEquals(2, XaeroWraps.canonRegion(-1, 24, 0, 3));
        // Level 2 regions span 4: a window from leaf -12 is regions [-3, 3).
        assertEquals(-3, XaeroWraps.canonRegion(3, 24, -12, 2));
        // From leaf -12 the window does not start on a level 3 region; leave them alone.
        assertEquals(3, XaeroWraps.canonRegion(3, 24, -12, 3));
        // The Nether's 3 leaves are not a whole number of level-1 regions; leave them alone.
        assertEquals(5, XaeroWraps.canonRegion(5, 3, 0, 1));
    }

    @Test
    void tileChunksFoldIntoTheCanonicalRegions() {
        // 8 tile chunks per region: a centered Overworld's are [-96, 96).
        assertEquals(-1, XaeroWraps.canonTileChunk(-1, CENTERED));
        assertEquals(-96, XaeroWraps.canonTileChunk(96, CENTERED));
        assertEquals(95, XaeroWraps.canonTileChunk(-97, CENTERED));
        assertEquals(191, XaeroWraps.canonTileChunk(-1, OVERWORLD));
        assertEquals(-1, XaeroWraps.canonTileChunk(-1, Wrap.NONE));
    }

    @Test
    void tileChunksDrawAtTheImageNearestThePlayer() {
        // A canonical tile chunk seen from the next lap (192 tile chunks per lap).
        assertEquals(190, XaeroWraps.nearestTileChunk(CENTERED, -2, 185));
        assertEquals(-2, XaeroWraps.nearestTileChunk(CENTERED, -2, 3));
        assertEquals(-2, XaeroWraps.nearestTileChunk(Wrap.NONE, -2, 185));
    }

    @Test
    void elementsMoveToTheImageNearestTheView() {
        // A waypoint at canonical x = 100, viewed from the next lap.
        assertEquals(12388.0, XaeroWraps.nearest(OVERWORLD, 100, 1, 12000), 1e-9);
        // In Overworld coordinates (divider 8) on a Nether map viewed one Nether lap over.
        assertEquals((1536 + 100) * 8.0, XaeroWraps.nearest(NETHER, 800, 8, 1600), 1e-9);
        assertEquals(100.0, XaeroWraps.nearest(Wrap.NONE, 100, 1, 12000), 1e-9);
    }

    @Test
    void coordinateTextIsCanonicalized() {
        assertEquals("X: 12 Y: 64 Z: 12287", XaeroWraps.canonicalCoordinates("X: 12300 Y: 64 Z: -1", OVERWORLD));
        assertEquals("X: 12 Y: 64 (70) Z: 5", XaeroWraps.canonicalCoordinates("X: -12276 Y: 64 (70) Z: 5", OVERWORLD));
        assertEquals("X: 1 Z: 2", XaeroWraps.canonicalCoordinates("X: 1 Z: 2", OVERWORLD));
        assertEquals("Nether Roof", XaeroWraps.canonicalCoordinates("Nether Roof", OVERWORLD));
    }
}
