package g_mungus.alpha_omega.block;

import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Filler in the band under a column's barrier cell ({@link CubeGeometry#BAND} deep): it looks, occludes and lights
 * exactly like filler, but collides like the neighbouring face's block at the same physical cell, turned into this
 * face's frame. So what is drawn of the next face within a chunk of the edge can be walked into and shot at, on the
 * client and the server alike. It only collides where the neighbour's block is known: with the neighbour's chunk
 * missing it is empty, as plain filler is. Its shape is worked out on every query (a dynamic shape), so its block state caches
 * nothing; plain filler, most of every face, keeps its cache.
 */
public class EdgeFillerBlock extends FillerBlock {

    public EdgeFillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        if (!(getter instanceof Level level)) return Shapes.empty();
        CubeGeometry cube = Cube.of(level);
        CubeFace face = cube == null ? null : cube.faceAt(pos.getX(), pos.getZ());
        CubeGeometry.Cell source = face == null ? null : cube.bandSource(face, pos.getX(), pos.getY(), pos.getZ());
        if (source == null) return Shapes.empty();
        // Never load the neighbour's chunk from inside movement. Until it is here, nothing is known to be there. Not
        // getChunkForCollisions: on the server thread it waits for a chunk whose ticket says it should be loaded but
        // that is still loading (from disk, or generating), which stalls the tick.
        BlockGetter chunk = level.getChunkSource().getChunkNow(source.x() >> 4, source.z() >> 4);
        if (chunk == null) return Shapes.empty();
        BlockPos there = new BlockPos(source.x(), source.y(), source.z());
        // An empty context: the entity's one describes "above" and "descending" in this face's frame, not the neighbour's.
        VoxelShape shape = chunk.getBlockState(there).getCollisionShape(level, there, CollisionContext.empty());
        return NeighbourShapes.rotate(shape, source.face(), face);
    }
}
