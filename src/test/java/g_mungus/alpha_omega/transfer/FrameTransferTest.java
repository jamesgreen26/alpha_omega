package g_mungus.alpha_omega.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import g_mungus.alpha_omega.orbifold.Transform;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Transfers' pure rules ({@code orbifold-implementation.md} phase 5): motions on state, crossings, expressions, groups. */
class FrameTransferTest {

    private static final OrbifoldGeometry G = new OrbifoldGeometry(OrbifoldSize.DEFAULT, 4);
    private static final double EPS = 1e-9;

    private static List<Motion> elements() {
        return List.of(G.east, G.west, G.northFold, G.southFold, G.northFold.then(G.east), G.southFold.then(G.west));
    }

    private static void close(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < EPS, "expected " + expected + ", got " + actual);
    }

    @Test
    void motionMovesPositionYawAndVelocity() {
        Transform east = Transform.of(G.east);
        close(new Vec3(100.25 + G.a, 70, -3.5), east.position(new Vec3(100.25, 70, -3.5)));
        close(new Vec3(0.3, -0.1, 0.2), east.vector(new Vec3(0.3, -0.1, 0.2)));
        assertEquals(45.0F, east.yaw(45.0F));

        Transform fold = Transform.of(G.northFold);
        // A half turn about N: the point reflects through N, velocity and facing turn round, height stays.
        close(new Vec3(-10.5, 64, 2 * G.northRow - (G.northRow - 3.25)), fold.position(new Vec3(10.5, 64, G.northRow - 3.25)));
        close(new Vec3(-0.3, 0.4, 0.2), fold.vector(new Vec3(0.3, 0.4, -0.2)));
        assertEquals(225.0F, fold.yaw(45.0F));
        float[] look = FrameTransfer.rotateLook(G.northFold, 45.0F, -20.0F);
        assertArrayEquals(new float[] {225.0F, -20.0F}, look);
    }

    @Test
    void motionsRoundTrip() {
        Random random = new Random(5);
        for (Motion g : elements()) {
            Transform there = Transform.of(g), back = Transform.of(g.inverse());
            for (int i = 0; i < 100; i++) {
                Vec3 p = new Vec3(random.nextDouble() * 20000 - 10000, random.nextDouble() * 300, random.nextDouble() * 10000 - 6000);
                Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
                close(p, back.position(there.position(p)));
                close(v, back.vector(there.vector(v)));
                float yaw = random.nextFloat() * 360.0F;
                float turned = back.yaw(there.yaw(yaw));
                assertEquals(0.0, Math.IEEEremainder(turned - yaw, 360.0), 1e-3, g + " yaw " + yaw + " -> " + turned);
            }
        }
    }

    @Test
    void motionsComposeAsTheGroup() {
        // A turn about F is T then the north fold; a point moved by the composite equals moved by each in turn.
        Motion aboutF = G.northFold.then(G.east);
        Vec3 p = new Vec3(G.maxX + 5.5, 80, G.northRow - 7.25);
        close(Transform.of(G.east).position(Transform.of(G.northFold).position(p)), Transform.of(aboutF).position(p));
        assertTrue(aboutF.then(aboutF).isIdentity(), "a half turn twice is the identity");
        assertTrue(G.east.then(G.west).isIdentity());
    }

    @Test
    void playersCrossPastClaimPlusAHalfOthersAtTheBand() {
        double claim = FrameTransfer.playerDepth(G);
        assertEquals(G.claim + 0.5, claim);
        double z = -2000.5;
        assertNull(FrameTransfer.destination(G, G.maxX + claim - 0.01, z, claim));
        Motion g = FrameTransfer.destination(G, G.maxX + claim + 0.01, z, claim);
        assertEquals(G.west, g);
        // It lands as deep inside the tile on the other side: short of crossing back.
        Vec3 landed = Transform.of(g).position(new Vec3(G.maxX + claim + 0.01, 0, z));
        assertTrue(G.seamDepth(landed.x, landed.z) < -G.claim, "landed at depth " + G.seamDepth(landed.x, landed.z));
        assertNull(FrameTransfer.destination(G, G.maxX + G.band - 0.1, z, G.band));
        assertEquals(G.west, FrameTransfer.destination(G, G.maxX + G.band + 0.1, z, G.band));
        // Past the north fold, the fold takes it back; at F, the fold and a translation.
        assertEquals(G.northFold, FrameTransfer.destination(G, 300.5, G.northRow - claim - 0.1, claim));
        Motion atF = FrameTransfer.destination(G, G.maxX + claim + 1, G.northRow - claim - 1, claim);
        assertNotNull(atF);
        Vec3 fromF = Transform.of(atF).position(new Vec3(G.maxX + claim + 1, 0, G.northRow - claim - 1));
        assertTrue(G.isTile((int) Math.floor(fromF.x), (int) Math.floor(fromF.z)), "past F lands in the tile: " + fromF);
    }

    @Test
    void standingAtAConePointDoesNotCross() {
        for (OrbifoldGeometry.ConePoint cone : G.conePoints()) {
            for (double dx : new double[] {-0.4, 0.0, 0.4}) {
                for (double dz : new double[] {-0.4, 0.0, 0.4}) {
                    assertNull(FrameTransfer.destination(G, cone.x() + dx, cone.z() + dz, FrameTransfer.playerDepth(G)), cone.name());
                }
            }
        }
    }

    @Test
    void circlingNAtTwentyBlocksNeverCrosses() {
        for (int i = 0; i < 720; i++) {
            double a = Math.toRadians(i * 0.5);
            double x = 20 * Math.cos(a), z = G.northRow + 20 * Math.sin(a);
            assertNull(FrameTransfer.destination(G, x, z, FrameTransfer.playerDepth(G)));
        }
    }

    @Test
    void expressionsAreThePlacesOfOnePoint() {
        // Ten blocks inside the east seam: itself, and ten blocks into the west band.
        List<Frames.Expression> e = Frames.expressions(G, G.maxX - 10.5, -2000.5, G.band);
        assertEquals(2, e.size(), e.toString());
        assertTrue(e.get(0).motion().isIdentity());
        assertEquals(G.west, e.get(1).motion());
        assertEquals(G.minX - 10.5, e.get(1).x(), EPS);
        // Too far in for the band to hold it.
        assertEquals(1, Frames.expressions(G, G.maxX - 70.5, -2000.5, G.band).size());
        // Near F there are copies across both seams and across the corner.
        List<Frames.Expression> nearF = Frames.expressions(G, G.maxX - 5.5, G.northRow + 5.5, G.band);
        assertEquals(4, nearF.size(), nearF.toString());
        for (Frames.Expression x : nearF) {
            Vec3 p = Transform.of(x.motion()).position(new Vec3(G.maxX - 5.5, 0, G.northRow + 5.5));
            assertEquals(x.x(), p.x, EPS);
            assertEquals(x.z(), p.z, EPS);
            assertTrue(G.seamDepth(x.x(), x.z()) <= G.band);
        }
        // From the band, the source in the tile.
        List<Frames.Expression> fromBand = Frames.expressions(G, G.maxX + 3.5, -2000.5, G.band);
        assertEquals(G.west, fromBand.get(1).motion());
    }

    @Test
    void worldDistanceIsAcrossSeams() {
        assertEquals(20.0, Frames.distance(G, G.maxX - 10, -2000, G.minX + 10, -2000), EPS);
        // Near N, (5, zN + 3) and (−5, zN + 3) are 10 apart in storage, but only 6 in the world: the fold puts the
        // second at (5, zN − 3), round the other side of the cone point.
        assertEquals(6.0, Frames.distance(G, 5, G.northRow + 3, -5, G.northRow + 3), EPS);
    }

    @Test
    void crossingFindsTheElementOfAJump() {
        assertEquals(G.west, Frames.crossing(G, G.maxX + 40.2, -2000.5, G.minX + 40.4, -2000.5, 8.0));
        assertNull(Frames.crossing(G, 100, 100, 103, 100, 8.0), "ordinary movement");
        assertNull(Frames.crossing(G, 100, 100, 3000, 100, 8.0), "a teleport elsewhere");
        Vec3 p = new Vec3(200.5, 0, G.northRow - 40.5);
        Vec3 q = Transform.of(G.northFold).position(p);
        assertEquals(G.northFold, Frames.crossing(G, p.x, p.z, q.x + 0.5, q.z, 8.0));
    }

    @Test
    void twoPlayersAcrossASeamShareOneFrame() {
        double[][] players = {{G.maxX - 10, -2000}, {G.minX + 12, -2000}};
        Motion[] frame = Frames.groupFrame(G, players, G.claim, 64);
        assertNotNull(frame);
        // One moves, by the element that puts it beside the other.
        int moved = (frame[0].isIdentity() ? 0 : 1) + (frame[1].isIdentity() ? 0 : 1);
        assertEquals(1, moved);
        Vec3 a = Transform.of(frame[0]).position(new Vec3(players[0][0], 0, players[0][1]));
        Vec3 b = Transform.of(frame[1]).position(new Vec3(players[1][0], 0, players[1][1]));
        assertEquals(22.0, a.distanceTo(b), EPS);
        // The one less deep stays: the first is 10 in, the second 12, so the first moves 10 out and the other stays.
        assertTrue(frame[1].isIdentity());
    }

    @Test
    void groupsPreferTheFrameMostMembersHave() {
        // Two on the east side, one across: the one across moves.
        double[][] players = {{G.minX + 5, -2000}, {G.maxX - 6, -2000}, {G.maxX - 20, -1990}};
        Motion[] frame = Frames.groupFrame(G, players, G.claim, 64);
        assertNotNull(frame);
        assertEquals(G.east, frame[0]);
        assertTrue(frame[1].isIdentity() && frame[2].isIdentity());
    }

    @Test
    void aGroupNoFrameHoldsDoesNotMove() {
        // A chain from deep on one side to deep on the other: no frame keeps all of them within C.
        double[][] players = {{G.maxX - 50, -2000}, {G.maxX - 2, -2000}, {G.minX + 50, -2000}};
        assertNull(Frames.groupFrame(G, players, G.claim, 64));
    }

    @Test
    void groupFrameAcrossTheNorthFold() {
        // Across the fold near x = 400: one 5 blocks south of it, one 9 blocks past it on the other side.
        double[][] players = {{400.5, G.northRow + 5.5}, {-400.5, G.northRow + 9.5}};
        Motion[] frame = Frames.groupFrame(G, players, G.claim, 64);
        assertNotNull(frame);
        Vec3 a = Transform.of(frame[0]).position(new Vec3(players[0][0], 0, players[0][1]));
        Vec3 b = Transform.of(frame[1]).position(new Vec3(players[1][0], 0, players[1][1]));
        assertTrue(a.distanceTo(b) < 20, a + " and " + b);
    }
}
