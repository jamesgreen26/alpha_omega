package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.worldgen.CubeChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Faces v1 (design §3, §4): the barrier and filler are generated where the geometry says, nothing can be written
 * across them, and structures stay inside a face. The tests work high in UP's overhang past its east edge, where
 * the barrier is open air (edge air).
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class FaceGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Blocks past UP's east edge (and so above the face plane) of the open barrier the behaviour tests use. */
    private static final int OVERHANG = 150;

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /**
     * The edge air cell {@code OVERHANG} blocks past UP's east edge, its chunk forced so that it ticks. Each test
     * passes its own {@code lane}, a chunk row of its own, since tests run at the same time.
     */
    private static BlockPos openBarrier(GameTestHelper helper, CubeGeometry geometry, int lane) {
        int x = geometry.centerX(CubeFace.UP) + geometry.radius + OVERHANG;
        int z = geometry.centerZ() + 3 + 32 * lane;
        BlockPos pos = new BlockPos(x, geometry.barrierY(CubeFace.UP, x, z), z);
        ChunkPos chunk = new ChunkPos(pos);
        helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        helper.getLevel().setChunkForced(chunk.x - 1, chunk.z, true);
        return pos;
    }

    private static void release(GameTestHelper helper, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        helper.getLevel().setChunkForced(chunk.x - 1, chunk.z, false);
    }

    private static void assertState(GameTestHelper helper, BlockPos pos, BlockState expected, String what) {
        BlockState actual = helper.getLevel().getBlockState(pos);
        helper.assertTrue(actual == expected, what + " at " + pos.toShortString() + ": expected " + expected + ", found " + actual);
    }

    /** Every cell of generated chunks near an edge matches the geometry: owned terrain, barrier, filler. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void barrierAndFillerFollowTheGeometry(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace up = CubeFace.UP;
        int edge = geometry.centerX(up) + geometry.radius;
        int bedrock = 0, edgeAir = 0, filler = 0;
        // Inside the base square near the edge, just past it, and far out in the overhang; and the north edge.
        int[][] columns = {{edge - 8, geometry.centerZ()}, {edge + 8, geometry.centerZ() + 20}, {edge + 120, geometry.centerZ()},
            {geometry.centerX(up), geometry.centerZ() - geometry.radius - 4}};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] column : columns) {
            ChunkAccess chunk = level.getChunk(column[0] >> 4, column[1] >> 4);
            ChunkPos chunkPos = chunk.getPos();
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    int barrierY = geometry.barrierY(up, x, z);
                    for (int y = geometry.minY; y < geometry.maxY; y++) {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (y == barrierY) {
                            boolean isBedrock = state.is(Blocks.BEDROCK);
                            helper.assertTrue(isBedrock || state.is(CubeBlocks.EDGE_AIR.get()), "barrier at " + pos.toShortString() + " is " + state);
                            if (isBedrock) bedrock++;
                            else edgeAir++;
                        } else if (y < barrierY) {
                            helper.assertTrue(state.is(CubeBlocks.FILLER.get()), "foreign cell at " + pos.toShortString() + " is " + state);
                            filler++;
                        } else {
                            helper.assertTrue(!state.is(CubeBlocks.FILLER.get()) && !state.is(CubeBlocks.EDGE_AIR.get()),
                                "owned cell at " + pos.toShortString() + " is " + state);
                        }
                    }
                }
            }
        }
        helper.assertTrue(bedrock > 0 && edgeAir > 0 && filler > 0, "expected all three kinds: bedrock " + bedrock + ", edge air " + edgeAir + ", filler " + filler);
        helper.succeed();
    }

    /** Between the faces' storage areas, chunks generate empty. */
    @GameTest(template = TEMPLATE)
    public static void chunksBetweenFacesAreEmpty(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        int x = geometry.centerX(CubeFace.UP);
        int z = geometry.centerZ() + 8 * geometry.spacingChunks + 40;
        helper.assertTrue(geometry.faceAt(x, z) == null, "test column should lie outside every face");
        ChunkAccess chunk = helper.getLevel().getChunk(x >> 4, z >> 4);
        for (int y = geometry.minY; y < geometry.maxY; y += 7) {
            helper.assertTrue(chunk.getBlockState(new BlockPos(x, y, z)).isAir(), "expected air between faces at y " + y);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nothingIsWrittenToTheBarrierOrFiller(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos barrier = openBarrier(helper, geometry, 0);
        BlockState edgeAir = level.getBlockState(barrier);
        helper.assertTrue(edgeAir.is(CubeBlocks.EDGE_AIR.get()), "expected edge air high in the overhang, found " + edgeAir);
        helper.assertTrue(!level.setBlockAndUpdate(barrier, Blocks.STONE.defaultBlockState()), "barrier write should fail");
        helper.assertTrue(!level.setBlockAndUpdate(barrier.below(), Blocks.STONE.defaultBlockState()), "filler write should fail");
        helper.assertTrue(!level.destroyBlock(barrier.below(), false), "filler should not break");
        assertState(helper, barrier, edgeAir, "barrier");
        assertState(helper, barrier.below(), CubeBlocks.FILLER.get().defaultBlockState(), "filler");
        BlockPos owned = barrier.above();
        helper.assertTrue(level.setBlockAndUpdate(owned, Blocks.STONE.defaultBlockState()), "owned write should work");
        level.setBlockAndUpdate(owned, Blocks.AIR.defaultBlockState());
        release(helper, barrier);
        helper.succeed();
    }

    /** Water poured beside the barrier spreads on its own face but never into edge air or filler. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fluidsStopAtTheBarrier(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos barrier = openBarrier(helper, geometry, 1);
        BlockPos source = barrier.west();
        // A ledge under the source, so the water spreads sideways toward the barrier.
        level.setBlockAndUpdate(source.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(source.below().west(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        helper.runAfterDelay(60, () -> {
            BlockState state = level.getBlockState(barrier);
            helper.assertTrue(state.is(CubeBlocks.EDGE_AIR.get()) && state.getFluidState().isEmpty(), "edge air took water: " + state);
            helper.assertTrue(level.getBlockState(barrier.below()).is(CubeBlocks.FILLER.get()), "filler took water");
            helper.assertTrue(!level.getFluidState(source.west()).isEmpty(), "water should still spread on its own face");
            level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(source.west(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(source.below(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(source.below().west(), Blocks.AIR.defaultBlockState());
            release(helper, barrier);
            helper.succeed();
        });
    }

    /** A piston cannot push a block into the barrier. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void pistonsCannotPushIntoTheBarrier(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos barrier = openBarrier(helper, geometry, 2);
        BlockPos block = barrier.west();
        BlockPos piston = block.west();
        level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(piston, Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(piston.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(10, () -> {
            assertState(helper, block, Blocks.STONE.defaultBlockState(), "pushed block");
            helper.assertTrue(level.getBlockState(barrier).is(CubeBlocks.EDGE_AIR.get()), "barrier changed");
            for (BlockPos pos : new BlockPos[] {block, piston, piston.west()}) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            release(helper, barrier);
            helper.succeed();
        });
    }

    /** A player clicking the side of a block facing the barrier places nothing. */
    @GameTest(template = TEMPLATE)
    public static void playersCannotPlaceIntoTheBarrier(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos barrier = openBarrier(helper, geometry, 3);
        BlockPos clicked = barrier.west();
        level.setBlockAndUpdate(clicked, Blocks.STONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(Vec3.atCenterOf(clicked.west()));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).relative(Direction.EAST, 0.5), Direction.EAST, clicked, false);
        InteractionResult result = Items.STONE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(!result.consumesAction(), "placing into edge air should fail, got " + result);
        helper.assertTrue(level.getBlockState(barrier).is(CubeBlocks.EDGE_AIR.get()), "barrier changed");
        level.setBlockAndUpdate(clicked, Blocks.AIR.defaultBlockState());
        release(helper, barrier);
        helper.succeed();
    }

    /** Every structure started on or around any face lies wholly inside it, clear of the barrier. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void structuresStayInsideAFace(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeChunkGenerator generator = (CubeChunkGenerator) level.getChunkSource().getGenerator();
        int ring = 3;
        int kept = 0;
        for (CubeFace face : CubeFace.values()) {
            for (int cx = geometry.minChunkX(face) - ring; cx < geometry.minChunkX(face) + geometry.faceChunks + ring; cx++) {
                for (int cz = geometry.minChunkZ() - ring; cz < geometry.minChunkZ() + geometry.faceChunks + ring; cz++) {
                    ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
                    for (StructureStart start : chunk.getAllStarts().values()) {
                        kept++;
                        helper.assertTrue(generator.fitsOnFace(start.getBoundingBox(), level),
                            "structure " + start.getStructure() + " at " + start.getBoundingBox() + " crosses the barrier");
                    }
                }
            }
        }
        int rejected = CubeChunkGenerator.REJECTED_STRUCTURES.get();
        AlphaOmegaMod.LOGGER.info("Structure starts: {} kept, {} rejected for crossing the barrier", kept, rejected);
        // A 16-chunk face is too small for most structures (mineshafts alone span 100+ blocks), so none may be kept.
        helper.assertTrue(rejected > 0, "expected structures crossing the barrier to be rejected");
        helper.succeed();
    }
}
