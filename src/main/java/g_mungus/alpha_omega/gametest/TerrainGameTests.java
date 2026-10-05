package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.block.EdgeBedrockBlock;
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
        if (state.is(CubeBlocks.EDGE_BEDROCK.get())) return "bedrock";
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
                    if (mine.is(CubeBlocks.EDGE_BEDROCK.get())) {
                        solid++;
                        boolean primaryHere = mine.getValue(EdgeBedrockBlock.PRIMARY), primaryThere = theirs.getValue(EdgeBedrockBlock.PRIMARY);
                        helper.assertTrue(primaryHere != primaryThere, a + "/" + b + " barrier at " + pos.toShortString() + " should have exactly one primary copy");
                    }
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Barrier cells agree: {} checked, {} bedrock", checked, solid);
        helper.assertTrue(solid > 0 && solid < checked, "expected both bedrock and edge air, got " + solid + " of " + checked);
        helper.succeed();
    }

    /**
     * Next to the barrier both faces build one terrain: every cell of the shared band is solid exactly where the mean
     * of the two faces' densities at its smallest cube corner is positive, whichever face stores it (the densities the
     * barrier pass uses, interpolated between noise cell corners). Blocks placed by features (trees, ice) are allowed
     * to differ.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void sharedBandIsOneTerrain(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        var densities = ((g_mungus.alpha_omega.worldgen.CubeChunkGenerator) level.getChunkSource().getGenerator())
            .densities(level.getChunkSource().randomState());
        int checked = 0, solid = 0;
        for (CubeFace[] edge : edges()) {
            for (CubeFace[] pair : new CubeFace[][] {edge, {edge[1], edge[0]}}) {
                CubeFace a = pair[0], b = pair[1];
                for (int lateral : LATERALS) {
                    for (int fromCentre = geometry.radius - 24; fromCentre < geometry.radius + 48; fromCentre += 3) {
                        BlockPos column = column(geometry, a, b, fromCentre, lateral);
                        int barrierY = geometry.barrierY(a, column.getX(), column.getZ());
                        if (barrierY < geometry.planeY - 24 || geometry.barrierPartner(a, column.getX(), barrierY, column.getZ()) != b) continue;
                        var chunk = level.getChunk(column);
                        for (int k = 1; k <= 8; k++) {
                            BlockPos pos = column.atY(barrierY + k);
                            BlockState state = chunk.getBlockState(pos);
                            if (state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)
                                || state.is(net.minecraft.tags.BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) continue;
                            int[] corner = geometry.cubeMinCorner(a, pos.getX(), pos.getY(), pos.getZ());
                            int[] there = geometry.transformCorner(a, b, corner[0], corner[1], corner[2]);
                            double mean = 0.5 * densities.at(corner[0], corner[1], corner[2]) + 0.5 * densities.at(there[0], there[1], there[2]);
                            boolean isSolid = !state.isAir() && state.getFluidState().isEmpty() && state.blocksMotion();
                            boolean plant = !state.isAir() && state.getFluidState().isEmpty() && !state.blocksMotion();
                            if (plant) continue;
                            helper.assertTrue(isSolid == mean > 0.0, a + "/" + b + " shared band cell " + pos.toShortString() + " is " + state + " but the shared density is " + mean);
                            checked++;
                            if (isSolid) solid++;
                        }
                    }
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Shared band: {} cells checked, {} solid", checked, solid);
        helper.assertTrue(solid > 100 && checked - solid > 100, "expected both ground and open cells in the band: " + solid + " of " + checked);
        helper.succeed();
    }

    /** Along the lines from the centre through the cube's corners, above the ground, the barrier is open air. */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void cornersAreOpenAboveTheGround(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace up = CubeFace.UP;
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                for (int k = 60; k < 200; k += 5) {
                    // UP's cell at height k on the line through the corner (sx, +1, sz): a = R + k on the positive side.
                    int x = geometry.centerX(up) + (sx > 0 ? geometry.radius + k : -geometry.radius - k - 1);
                    int z = geometry.centerZ() + (sz > 0 ? geometry.radius + k : -geometry.radius - k - 1);
                    int y = geometry.planeY + k;
                    helper.assertTrue(geometry.barrierFaces(up, x, y, z).size() == 3, "not a corner cell at " + x + " " + y + " " + z);
                    BlockState state = level.getChunk(new BlockPos(x, y, z)).getBlockState(new BlockPos(x, y, z));
                    helper.assertTrue(state.is(CubeBlocks.EDGE_AIR.get()), "corner line at " + k + " above the plane is " + state);
                }
            }
        }
        helper.succeed();
    }
}
