package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Terrain meets at the edges (design §4): every barrier cell is the same block in both faces' storage, and the
 * ground on each side of an edge meets the other side's at the ridge.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int[] LATERALS = {-96, -40, 0, 40, 96};

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /** The 12 edges, each once. */
    private static List<CubeFace[]> edges() {
        List<CubeFace[]> edges = new ArrayList<>();
        for (CubeFace a : CubeFace.values()) {
            for (CubeFace b : CubeFace.values()) {
                if (a.ordinal() < b.ordinal() && a.isNeighbour(b)) edges.add(new CubeFace[] {a, b});
            }
        }
        return edges;
    }

    /** A block column of {@code face}'s storage {@code fromCentre} blocks toward {@code toward}, {@code lateral} along the edge. */
    private static BlockPos column(CubeGeometry geometry, CubeFace face, CubeFace toward, int fromCentre, int lateral) {
        double[] d = face.toward(toward);
        return new BlockPos(geometry.centerX(face) + (int) Math.round(d[0] * fromCentre + d[2] * lateral), 0,
            geometry.centerZ() + (int) Math.round(d[2] * fromCentre + d[0] * lateral));
    }

    private static String kind(BlockState state) {
        if (state.is(Blocks.BEDROCK)) return "bedrock";
        if (state.is(CubeBlocks.EDGE_AIR.get())) return "edge air";
        return state.toString();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void barriersAgreeAcrossEveryEdge(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        int checked = 0, solid = 0;
        for (CubeFace[] edge : edges()) {
            CubeFace a = edge[0], b = edge[1];
            for (int lateral : LATERALS) {
                for (int y = geometry.planeY - 20; y <= geometry.planeY + 60; y += 4) {
                    // The barrier in a column n blocks out (n = R + height) sits at that height, on whichever side.
                    int n = geometry.radius + y - geometry.planeY;
                    BlockPos pos = null;
                    for (int fromCentre : new int[] {n, n + 1, -n, -n - 1}) {
                        BlockPos column = column(geometry, a, b, Math.abs(fromCentre), lateral);
                        if (geometry.isBarrier(a, column.getX(), y, column.getZ()) && geometry.barrierPartner(a, column.getX(), y, column.getZ()) == b) {
                            pos = column.atY(y);
                            break;
                        }
                    }
                    helper.assertTrue(pos != null, a + "/" + b + ": no barrier cell found at y " + y + ", lateral " + lateral);
                    int[] there = geometry.transformBlock(a, b, pos.getX(), pos.getY(), pos.getZ());
                    BlockPos other = new BlockPos(there[0], there[1], there[2]);
                    BlockState mine = level.getChunk(pos).getBlockState(pos);
                    BlockState theirs = level.getChunk(other).getBlockState(other);
                    helper.assertTrue(kind(mine).equals(kind(theirs)), a + "/" + b + " barrier at " + pos.toShortString() + " is " + kind(mine)
                        + " but " + kind(theirs) + " at " + other.toShortString());
                    checked++;
                    if (mine.is(Blocks.BEDROCK)) solid++;
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Barrier cells agree: {} checked, {} bedrock", checked, solid);
        helper.assertTrue(solid > 0 && solid < checked, "expected both bedrock and edge air, got " + solid + " of " + checked);
        helper.succeed();
    }

    /** Height of the ground (the top owned solid block, trees aside) in a column, above the face plane. */
    private static int ground(ServerLevel level, CubeGeometry geometry, CubeFace face, BlockPos column) {
        var chunk = level.getChunk(column);
        BlockPos.MutableBlockPos pos = column.mutable();
        for (int y = geometry.maxY - 1; y > geometry.minY; y--) {
            pos.setY(y);
            if (!geometry.isOwned(face, pos.getX(), y, pos.getZ())) break;
            BlockState state = chunk.getBlockState(pos);
            if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            if (!state.isAir() && state.getFluidState().isEmpty() && state.blocksMotion()) return y - geometry.planeY;
        }
        return Integer.MIN_VALUE;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void groundMeetsAtTheRidge(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        int samples = 0;
        long totalDifference = 0;
        int worst = 0;
        StringBuilder report = new StringBuilder();
        for (CubeFace[] edge : edges()) {
            CubeFace a = edge[0], b = edge[1];
            for (int lateral : LATERALS) {
                BlockPos mine = column(geometry, a, b, geometry.radius - 1, lateral);
                double[] edgePoint = geometry.transform(a, b, mine.getX() + 0.5, geometry.planeY, mine.getZ() + 0.5);
                BlockPos theirs = BlockPos.containing(edgePoint[0], 0, edgePoint[2]);
                int ha = ground(level, geometry, a, mine);
                int hb = ground(level, geometry, b, theirs);
                if (ha == Integer.MIN_VALUE || hb == Integer.MIN_VALUE) continue;
                int difference = Math.abs(ha - hb);
                samples++;
                totalDifference += difference;
                worst = Math.max(worst, difference);
                report.append(' ').append(a).append('/').append(b).append(':').append(ha).append('/').append(hb);
            }
        }
        double mean = (double) totalDifference / samples;
        AlphaOmegaMod.LOGGER.info("Ground at the ridge: {} samples, mean difference {}, worst {};{}", samples, mean, worst, report);
        helper.assertTrue(samples >= 30, "too few ground samples: " + samples);
        helper.assertTrue(mean <= 4.0, "ground does not meet at the ridge: mean height difference " + mean + " (worst " + worst + ")");
        helper.succeed();
    }
}
