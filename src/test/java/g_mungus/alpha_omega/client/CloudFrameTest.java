package g_mungus.alpha_omega.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link CloudFrame}: the clouds over every storage point are the same before and after a transfer by any generator
 * (and composite, as at F), so the first frame after a crossing shows the same clouds; and they drift the same way
 * over the ground on both sides.
 */
class CloudFrameTest {

    private static List<OrbifoldGeometry> sizes() {
        List<OrbifoldGeometry> list = new ArrayList<>();
        for (OrbifoldSize size : OrbifoldSize.PRESETS) list.add(new OrbifoldGeometry(size, 4));
        return list;
    }

    /** Every element a transfer can apply: the generators and the composites near F and the other corners. */
    private static List<Motion> transfers(OrbifoldGeometry g) {
        return List.of(g.east, g.west, g.northFold, g.southFold, g.northFold.then(g.east), g.northFold.then(g.west),
            g.southFold.then(g.east), g.southFold.then(g.west));
    }

    private static final double EPSILON = 1e-9;

    @Test
    void cloudsStayOverTheSameGroundOnEveryTransfer() {
        Random random = new Random(5);
        for (OrbifoldGeometry g : sizes()) {
            // Start from a cloud frame some path has already reached: a few earlier transfers.
            Motion m = Motion.IDENTITY;
            for (int i = 0; i < 5; i++) m = CloudFrame.afterTransfer(m, transfers(g).get(random.nextInt(8)));
            for (Motion t : transfers(g)) {
                Motion after = CloudFrame.afterTransfer(m, t);
                for (int i = 0; i < 50; i++) {
                    // Points round the crossing: the camera and the sky it sees, up to a cloud render distance off.
                    double px = (random.nextDouble() - 0.5) * g.a, pz = g.northRow + random.nextDouble() * g.b / 2;
                    double qx = px + (random.nextDouble() - 0.5) * 1000, qz = pz + (random.nextDouble() - 0.5) * 1000;
                    double ticks = random.nextInt(1_000_000) + random.nextDouble();
                    double[] before = CloudFrame.texture(m, qx, qz, ticks);
                    double[] moved = CloudFrame.texture(after, t.pointX(qx), t.pointZ(qz), ticks);
                    assertEquals(before[0], moved[0], EPSILON, g.size + " " + t + ": cloud x over the same ground");
                    assertEquals(before[1], moved[1], EPSILON, g.size + " " + t + ": cloud z over the same ground");
                    // A tick later, both have drifted to the same cloud too: the drift is continuous across the crossing.
                    double[] later = CloudFrame.texture(m, qx, qz, ticks + 1);
                    double[] laterMoved = CloudFrame.texture(after, t.pointX(qx), t.pointZ(qz), ticks + 1);
                    assertEquals(later[0], laterMoved[0], EPSILON);
                    assertEquals(later[1], laterMoved[1], EPSILON);
                }
                assertEquals(m.turned() != t.turned(), after.turned(), "a fold turns the layer");
            }
        }
    }

    /** What the frame fixes: with vanilla's storage placement (no update), a fold moves every cloud. */
    @Test
    void vanillaPlacementJumpsOnAFold() {
        OrbifoldGeometry g = new OrbifoldGeometry(OrbifoldSize.NORMAL, 4);
        double px = 600.5, pz = g.northRow - 30.5;
        double[] before = CloudFrame.texture(Motion.IDENTITY, px, pz, 0);
        double[] after = CloudFrame.texture(Motion.IDENTITY, g.northFold.pointX(px), g.northFold.pointZ(pz), 0);
        assertTrue(Math.abs(before[0] - after[0]) > 50 || Math.abs(before[1] - after[1]) > 50);
    }

    /** A round trip across a seam and back restores the frame exactly. */
    @Test
    void roundTripRestores() {
        for (OrbifoldGeometry g : sizes()) {
            for (Motion t : transfers(g)) {
                assertEquals(Motion.IDENTITY, CloudFrame.afterTransfer(CloudFrame.afterTransfer(Motion.IDENTITY, t), t.inverse()), t.toString());
            }
        }
    }
}
