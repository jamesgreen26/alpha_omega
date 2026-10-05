package g_mungus.alpha_omega.block;

import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block shapes of one face, turned into another face's storage axes about the cell's centre. The faces' frames differ
 * by quarter turns, so boxes stay boxes. Most blocks share a few constant shapes, so results are cached per pair of
 * faces, keyed (weakly, by identity) by the shape turned.
 */
public final class NeighbourShapes {

    @SuppressWarnings("unchecked")
    private static final Map<VoxelShape, VoxelShape>[] CACHE = new Map[36];

    static {
        for (int i = 0; i < CACHE.length; i++) CACHE[i] = Collections.synchronizedMap(new WeakHashMap<>());
    }

    private NeighbourShapes() {
    }

    /** A shape of a cell of {@code from}'s storage, as the same cell in {@code to}'s storage. */
    public static VoxelShape rotate(VoxelShape shape, CubeFace from, CubeFace to) {
        if (from == to || shape.isEmpty() || shape == Shapes.block()) return shape;
        return CACHE[from.slot() * 6 + to.slot()].computeIfAbsent(shape, s -> turn(s, from, to));
    }

    private static VoxelShape turn(VoxelShape shape, CubeFace from, CubeFace to) {
        VoxelShape turned = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double[] a = CubeGeometry.rotate(from, to, box.minX - 0.5, box.minY - 0.5, box.minZ - 0.5);
            double[] b = CubeGeometry.rotate(from, to, box.maxX - 0.5, box.maxY - 0.5, box.maxZ - 0.5);
            turned = Shapes.or(turned, Shapes.create(new AABB(a[0] + 0.5, a[1] + 0.5, a[2] + 0.5, b[0] + 0.5, b[1] + 0.5, b[2] + 0.5)));
        }
        return turned.optimize();
    }
}
