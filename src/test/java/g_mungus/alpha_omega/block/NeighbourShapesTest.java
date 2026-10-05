package g_mungus.alpha_omega.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

class NeighbourShapesTest {

    private static final VoxelShape SLAB = Shapes.box(0, 0, 0, 1, 0.5, 1);
    private static final VoxelShape FENCE_POST = Shapes.box(0.375, 0, 0.375, 0.625, 1.5, 0.625);

    private static void assertBox(AABB expected, VoxelShape shape) {
        AABB box = shape.bounds();
        assertEquals(1, shape.toAabbs().size(), "one box");
        assertEquals(expected.minX, box.minX, 1e-9);
        assertEquals(expected.minY, box.minY, 1e-9);
        assertEquals(expected.minZ, box.minZ, 1e-9);
        assertEquals(expected.maxX, box.maxX, 1e-9);
        assertEquals(expected.maxY, box.maxY, 1e-9);
        assertEquals(expected.maxZ, box.maxZ, 1e-9);
    }

    @Test
    void fullAndEmptyStayPut() {
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                assertSame(Shapes.block(), NeighbourShapes.rotate(Shapes.block(), from, to));
                assertTrue(NeighbourShapes.rotate(Shapes.empty(), from, to).isEmpty());
            }
        }
    }

    @Test
    void thereAndBackIsTheSameShape() {
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                assertBox(SLAB.bounds(), NeighbourShapes.rotate(NeighbourShapes.rotate(SLAB, from, to), to, from));
                assertBox(FENCE_POST.bounds(), NeighbourShapes.rotate(NeighbourShapes.rotate(FENCE_POST, from, to), to, from));
            }
        }
    }

    /** A bottom slab lies on the side of the cell that is down for its own face; a fence post sticks out its own up. */
    @Test
    void shapesKeepTheirOwnFacesDown() {
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                if (!from.isNeighbour(to)) continue;
                double[] up = CubeGeometry.rotate(from, to, 0, 1, 0);
                VoxelShape slab = NeighbourShapes.rotate(SLAB, from, to);
                AABB box = slab.bounds();
                double[] min = {box.minX, box.minY, box.minZ}, max = {box.maxX, box.maxY, box.maxZ};
                VoxelShape post = NeighbourShapes.rotate(FENCE_POST, from, to);
                AABB postBox = post.bounds();
                double[] postMin = {postBox.minX, postBox.minY, postBox.minZ}, postMax = {postBox.maxX, postBox.maxY, postBox.maxZ};
                for (int axis = 0; axis < 3; axis++) {
                    long direction = Math.round(up[axis]);
                    if (direction == 0) {
                        assertEquals(0.0, min[axis], 1e-9);
                        assertEquals(1.0, max[axis], 1e-9);
                    } else {
                        // Half the cell, on the side away from the slab's own up; the post 0.5 past the side toward it.
                        assertEquals(direction > 0 ? 0.0 : 0.5, min[axis], 1e-9, from + " -> " + to);
                        assertEquals(direction > 0 ? 0.5 : 1.0, max[axis], 1e-9, from + " -> " + to);
                        assertEquals(direction > 0 ? 0.0 : -0.5, postMin[axis], 1e-9, from + " -> " + to);
                        assertEquals(direction > 0 ? 1.5 : 1.0, postMax[axis], 1e-9, from + " -> " + to);
                    }
                }
            }
        }
    }
}
