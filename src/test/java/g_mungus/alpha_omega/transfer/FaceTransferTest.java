package g_mungus.alpha_omega.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FaceTransferTest {

    private static final CubeGeometry GEOMETRY = new CubeGeometry(CubeSettings.DEFAULT, 63, -64, 320);

    /** Up stays up; heading for the edge becomes heading away from it on the next face. */
    @Test
    void uprightKeepsUpAndCarriesOnPastTheEdge() {
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                if (!from.isNeighbour(to)) continue;
                assertArrayEquals(new double[] {0, 1, 0}, CubeGeometry.rotateUpright(from, to, 0, 1, 0), 1e-12);
                double[] towardEdge = from.toward(to);
                double[] awayFromEdge = to.toward(from.opposite());
                assertArrayEquals(awayFromEdge, CubeGeometry.rotateUpright(from, to, towardEdge[0], towardEdge[1], towardEdge[2]), 1e-12);
                double[] v = {0.3, -0.7, 1.9};
                double[] r = CubeGeometry.rotateUpright(from, to, v[0], v[1], v[2]);
                assertArrayEquals(v, CubeGeometry.rotateUpright(to, from, r[0], r[1], r[2]), 1e-12);
                // The unfold's linear part.
                double x = GEOMETRY.centerX(from) + 20, y = 100, z = GEOMETRY.centerZ() - 30;
                double[] a = GEOMETRY.unfold(from, to, x, y, z);
                double[] b = GEOMETRY.unfold(from, to, x + v[0], y + v[1], z + v[2]);
                assertArrayEquals(r, new double[] {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, 1e-9);
            }
        }
    }

    @Test
    void lookRoundTripsThroughYawAndPitch() {
        Random random = new Random(5);
        for (int i = 0; i < 500; i++) {
            float yRot = random.nextFloat() * 720 - 360, xRot = random.nextFloat() * 170 - 85;
            float[] same = FaceTransfer.rotateLook(FaceTransfer.Mode.WORLD, CubeFace.EAST, CubeFace.EAST, yRot, xRot);
            assertEquals(yRot, same[0], 1e-3);
            assertEquals(xRot, same[1], 1e-3);
        }
    }

    /** Walking toward an edge, level, you carry on level and away from it; the yaw keeps its winding. */
    @Test
    void uprightLookStaysLevel() {
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                if (!from.isNeighbour(to)) continue;
                double[] towardEdge = from.toward(to);
                float yaw = (float) Math.toDegrees(Math.atan2(-towardEdge[0], towardEdge[2])) + 720;
                float[] look = FaceTransfer.rotateLook(FaceTransfer.Mode.UPRIGHT, from, to, yaw, 0);
                assertEquals(0.0, look[1], 1e-4);
                assertTrue(Math.abs(look[0] - yaw) <= 180, "yaw should not spin round");
                double[] d = FaceTransfer.look(look[1], look[0]);
                assertArrayEquals(to.toward(from.opposite()), d, 1e-5);
                // The same look kept in world space points straight up on the new face.
                float[] world = FaceTransfer.rotateLook(FaceTransfer.Mode.WORLD, from, to, yaw, 0);
                assertEquals(-90.0, world[1], 1e-3);
            }
        }
    }

    /** A position transfers only past the margin, and lands short of the margin back. */
    @Test
    void hysteresis() {
        CubeFace up = CubeFace.UP, east = CubeFace.EAST;
        double y = GEOMETRY.planeY + 40.0, z = GEOMETRY.centerZ();
        double onDiagonal = GEOMETRY.centerX(up) + GEOMETRY.radius + 40.0;
        // Depth across the diagonal is horizontal offset / √2.
        assertNull(FaceTransfer.destination(GEOMETRY, up, onDiagonal + 0.6, y, z));
        double x = onDiagonal + 1.0;
        assertEquals(east, FaceTransfer.destination(GEOMETRY, up, x, y, z));
        double[] p = GEOMETRY.transform(up, east, x, y, z);
        assertEquals(-1.0 / Math.sqrt(2), GEOMETRY.depthInto(east, up, p[0], p[1], p[2]), 1e-9);
        assertNull(FaceTransfer.destination(GEOMETRY, east, p[0], p[1], p[2]));
    }
}
