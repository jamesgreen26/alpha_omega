package g_mungus.alpha_omega.orbifold;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.item.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link NearestImages} (compasses and maps aim through it) and {@link Maps}' marker and centre rules. */
class NearestImagesTest {

    private static List<OrbifoldGeometry> sizes() {
        List<OrbifoldGeometry> list = new ArrayList<>();
        for (OrbifoldSize size : OrbifoldSize.PRESETS) list.add(new OrbifoldGeometry(size, 4));
        return list;
    }

    /** Every element of Γ with a lattice part within {@code r} steps: translations and half turns. */
    private static List<Motion> elements(OrbifoldGeometry g, int r) {
        List<Motion> list = new ArrayList<>();
        for (int m = -r; m <= r; m++) {
            for (int n = -r; n <= r; n++) {
                int lx = m * g.a + n * g.a / 2, lz = n * g.b;
                list.add(Motion.translation(lx, lz));
                list.add(Motion.halfTurn(lx, 2 * g.northRow + lz));
            }
        }
        return list;
    }

    private static double distance(Motion h, double tx, double tz, double px, double pz) {
        return Math.hypot(h.pointX(tx) - px, h.pointZ(tz) - pz);
    }

    @Test
    void generatorsAreInTheGroupEnumerated() {
        for (OrbifoldGeometry g : sizes()) {
            List<Motion> all = elements(g, 2);
            for (Motion generator : g.generators()) assertTrue(all.contains(generator), g.size + ": " + generator);
            assertTrue(all.contains(g.northFold.then(g.east)), g.size + ": a turn about F");
        }
    }

    /** The search finds the nearest image over a wide brute-force enumeration, wherever the two points are. */
    @Test
    void nearestMatchesBruteForce() {
        Random random = new Random(12);
        for (OrbifoldGeometry g : sizes()) {
            List<Motion> all = elements(g, 6);
            for (int i = 0; i < 400; i++) {
                // Anywhere within a couple of laps of the tile, not only in the footprint.
                double tx = (random.nextDouble() - 0.5) * 3 * g.a, tz = g.northRow + (random.nextDouble() - 0.5) * 3 * g.b;
                double px = (random.nextDouble() - 0.5) * 3 * g.a, pz = g.northRow + (random.nextDouble() - 0.5) * 3 * g.b;
                double best = Double.MAX_VALUE;
                for (Motion h : all) best = Math.min(best, distance(h, tx, tz, px, pz));
                Motion found = NearestImages.toward(g, tx, tz, px, pz);
                assertEquals(best, distance(found, tx, tz, px, pz), 1e-6, g.size + ": " + tx + "," + tz + " near " + px + "," + pz);
            }
        }
    }

    /** A point near another is its own nearest image: the identity, not some equally near lap. */
    @Test
    void nearbyIsIdentity() {
        for (OrbifoldGeometry g : sizes()) {
            assertEquals(Motion.IDENTITY, NearestImages.toward(g, 10.5, g.spawnZ + 3.5, 0.5, g.spawnZ + 0.5));
            assertEquals(Motion.IDENTITY, NearestImages.towardCell(g, g.maxX - 3, -100 + g.northRow + 300, g.maxX - 10.0, g.northRow + 200.0));
        }
    }

    /**
     * A lodestone just inside the east edge, seen from the west band: the nearest image is its band copy beside the
     * holder (T−), not the stored lodestone a lap away. Across the north fold: its turned copy (R_N). Near F: the turn
     * about F.
     */
    @Test
    void compassTargetsAcrossSeams() {
        for (OrbifoldGeometry g : sizes()) {
            int z = g.northRow + 300;
            int[] east = NearestImages.nearestCell(g, g.maxX - 5, z, g.minX - 20.5, z + 0.5);
            assertArrayEquals(new int[] {g.minX - 5, z}, east, g.size + ": east target from the west band");
            assertTrue(g.copies(g.maxX - 5, z).stream().anyMatch(c -> c.x() == east[0] && c.z() == east[1]), g.size + ": that is a band copy");
            // A lodestone 10 south of the north fold at x = 400, seen from 20 north of the fold at x = −400.
            int[] north = NearestImages.nearestCell(g, 400, g.northRow + 10, -400.5, g.northRow - 20.5);
            assertArrayEquals(new int[] {g.northFold.inverse().cellX(400), g.northFold.inverse().cellZ(g.northRow + 10)}, north, g.size + ": across the north fold");
            // From the tile, a target stored in the band resolves to its source when that is nearer.
            int[] back = NearestImages.nearestCell(g, g.minX - 5, z, g.maxX - 20.5, z + 0.5);
            assertArrayEquals(new int[] {g.maxX - 5, z}, back, g.size + ": band target from the tile");
            // Near F: a target just inside the west edge by the north fold, from past the fold by the east edge.
            Motion f = NearestImages.towardCell(g, g.minX + 3, g.northRow + 3, g.maxX - 2.5, g.northRow - 2.5);
            assertTrue(f.turned() && distance(f, g.minX + 3.5, g.northRow + 3.5, g.maxX - 2.5, g.northRow - 2.5) < 10, g.size + ": turn about F, " + f);
        }
    }

    /** Spawn and its far side: a target a lap and a half away resolves within half a lattice cell. */
    @Test
    void farTargetsResolveWithinACell() {
        for (OrbifoldGeometry g : sizes()) {
            double d = NearestImages.distance(g, g.spawnX + 1.5 * g.a, g.spawnZ, g.spawnX, g.spawnZ);
            // Half a lap by translation, or nearer by a turn when spawn is near a fold.
            assertTrue(d <= g.a / 2.0 + 1e-9, g.size + ": half a lap or less, " + d);
            assertTrue(NearestImages.distance(g, 123.0, g.northRow + 456.0, -5000.0, g.southRow + 900.0) <= Math.hypot(g.a, g.b) / 2, g.size.toString());
        }
    }

    @Test
    void mapsCentreOnSourcesAndTurnMarkers() {
        for (OrbifoldGeometry g : sizes()) {
            int z = g.northRow + 300;
            // A map made in the west band is centred from its source by the east edge.
            assertArrayEquals(new int[] {g.maxX - 10, z}, Maps.centreFrom(g, g.minX - 10, z), g.size.toString());
            // A map made past the north fold: from the turned source.
            int[] folded = Maps.centreFrom(g, 400, g.northRow - 10);
            assertArrayEquals(new int[] {g.northFold.cellX(400), g.northFold.cellZ(g.northRow - 10)}, folded, g.size.toString());
            assertTrue(g.isTile(folded[0], folded[1]));
            // A map centred just south of the north fold at x = 600: a player 20 blocks past the fold at x = −600
            // (stored in the tile, a turn away) is marked 20 north of the fold at x = 600.5, facing the other way.
            double[] marker = Maps.decoration(g, 600, g.northRow + 40, -600.5, g.northRow + 20.0, 90.0);
            assertEquals(600.5, marker[0], 1e-9, g.size + ": marker x");
            assertEquals(g.northRow - 20.0, marker[1], 1e-9, g.size + ": marker z");
            assertEquals(270.0, marker[2], 1e-9, g.size + ": marker turned");
            // Beside the centre, nothing changes.
            assertArrayEquals(new double[] {610.0, g.northRow + 50.0, 45.0}, Maps.decoration(g, 600, g.northRow + 40, 610.0, g.northRow + 50.0, 45.0), 1e-9);
            // Across the east seam: by translation, no turn.
            double[] east = Maps.decoration(g, g.maxX - 30, z, g.minX + 10.0, z, 10.0);
            assertArrayEquals(new double[] {g.maxX + 10.0, z, 10.0}, east, 1e-9);
        }
    }
}
