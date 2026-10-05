package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Within a chunk of an edge, the neighbouring face's blocks collide where they are drawn (design: neighbour
 * collision): the band of edge filler under the barrier takes its shape from the neighbour's cell at the same place.
 * The tests work high over UP's edge with EAST, in open air, on the band cell just past the diagonal.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class CollisionGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int HEIGHT = 120;

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /** The band cell of UP just under the barrier, HEIGHT up over the edge with EAST, {@code lateral} along it. */
    private static BlockPos bandCell(CubeGeometry geometry, int lateral) {
        int y = geometry.planeY + HEIGHT;
        // UP's barrier rises one block per block outward: the column whose barrier is at y + 1.
        int x = geometry.centerX(CubeFace.UP) + y + 1 - geometry.planeY + geometry.radius;
        return new BlockPos(x, y, geometry.centerZ() + lateral);
    }

    /** The EAST cell a band cell takes its collision from. */
    private static BlockPos source(CubeGeometry geometry, BlockPos band) {
        CubeGeometry.Cell cell = geometry.bandSource(CubeFace.UP, band.getX(), band.getY(), band.getZ());
        if (cell == null || cell.face() != CubeFace.EAST) throw new IllegalStateException("band cell " + band + " reads " + cell);
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static Set<ChunkPos> load(ServerLevel level, BlockPos... cells) {
        Set<ChunkPos> forced = new java.util.HashSet<>();
        for (BlockPos cell : cells) {
            ChunkPos chunk = new ChunkPos(cell);
            if (forced.add(chunk)) TestChunks.force(level, chunk);
        }
        return forced;
    }

    @GameTest(template = TEMPLATE)
    public static void bandCollidesLikeTheNeighbour(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos band = bandCell(geometry, -30);
        BlockPos source = source(geometry, band);
        Set<ChunkPos> forced = load(level, band, source);
        helper.assertTrue(level.getBlockState(band).is(CubeBlocks.EDGE_FILLER.get()), "band cell is " + level.getBlockState(band));
        AABB inside = new AABB(band).deflate(0.1);

        level.setBlockAndUpdate(source, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!level.noCollision(inside), "stone on EAST should make the band cell solid");

        level.setBlockAndUpdate(source, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        VoxelShape slab = level.getBlockState(band).getCollisionShape(level, band, CollisionContext.empty());
        AABB box = slab.bounds();
        // EAST's up is sideways in UP's frame: its bottom slab stands upright, full height, half the cell across.
        helper.assertTrue(box.minY == 0.0 && box.maxY == 1.0 && Math.abs((box.maxX - box.minX) * (box.maxZ - box.minZ) - 0.5) < 1e-9,
            "EAST's bottom slab should stand upright in UP's frame: " + box);

        // Standing on it is invisible: no landing or running particles from the filler.
        BlockState edge = level.getBlockState(band);
        net.minecraft.world.entity.monster.Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(edge.addLandingEffects(level, band, edge, zombie, 10) && edge.addRunningEffects(level, band, zombie),
            "edge filler should suppress landing and running particles");

        level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.noCollision(inside), "air on EAST should leave the band cell open");
        TestChunks.release(level, forced);
        helper.succeed();
    }

    /** A thrown item stops at a neighbour's wall; once the wall is gone it crosses. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void itemsStopAtTheNeighboursBlocks(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos band = bandCell(geometry, -20);
        BlockPos source = source(geometry, band);
        Set<ChunkPos> forced = load(level, band.west(4), band, source);
        level.setBlockAndUpdate(source, Blocks.STONE.defaultBlockState());
        ItemEntity blocked = throwItem(level, band.west(4));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(geometry.faceAt(blocked.getX(), blocked.getZ()) == CubeFace.UP && blocked.getX() < band.getX(),
                "the item should have stopped at EAST's stone, at " + blocked.position());
            blocked.discard();
            level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
            ItemEntity free = throwItem(level, band.west(4));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(geometry.faceAt(free.getX(), free.getZ()) == CubeFace.EAST, "with the stone gone the item should cross, at " + free.position());
                free.discard();
                TestChunks.release(level, forced);
                helper.succeed();
            });
        });
    }

    private static ItemEntity throwItem(ServerLevel level, BlockPos from) {
        ItemEntity item = new ItemEntity(level, from.getX() + 0.5, from.getY() + 0.3, from.getZ() + 0.5, new ItemStack(Items.DIAMOND), 0.6, 0, 0);
        item.setNoGravity(true);
        item.setNeverPickUp();
        level.addFreshEntity(item);
        return item;
    }

    /** An arrow shot at a neighbour's block sticks in it on this side of the edge. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void arrowsHitTheNeighboursBlocks(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos band = bandCell(geometry, -10);
        BlockPos source = source(geometry, band);
        Set<ChunkPos> forced = load(level, band.west(6), band, source);
        level.setBlockAndUpdate(source, Blocks.STONE.defaultBlockState());
        Arrow arrow = EntityType.ARROW.create(level);
        arrow.moveTo(band.getX() - 5.5, band.getY() + 0.5, band.getZ() + 0.5);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(1.5, 0, 0);
        level.addFreshEntity(arrow);
        helper.runAfterDelay(20, () -> {
            Vec3 stuck = arrow.position();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(geometry.faceAt(arrow.getX(), arrow.getZ()) == CubeFace.UP, "the arrow should not have crossed, at " + arrow.position());
                // A stuck arrow keeps its last velocity but no longer moves.
                helper.assertTrue(arrow.getX() <= band.getX() + 0.01 && arrow.position().distanceTo(stuck) < 1e-6,
                    "the arrow should be stuck at EAST's stone, at " + arrow.position() + ", was " + stuck);
                arrow.discard();
                level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
                TestChunks.release(level, forced);
                helper.succeed();
            });
        });
    }

    /** The crosshair still only reaches this face: a neighbour's block in the band can't be targeted. */
    @GameTest(template = TEMPLATE)
    public static void crosshairPassesThroughTheBand(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos band = bandCell(geometry, 0);
        BlockPos source = source(geometry, band);
        Set<ChunkPos> forced = load(level, band.west(4), band, source);
        level.setBlockAndUpdate(source, Blocks.STONE.defaultBlockState());
        Vec3 from = Vec3.atCenterOf(band.west(3)), to = Vec3.atCenterOf(band);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        helper.assertTrue(hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(band), "the crosshair hit the neighbour's block: " + hit.getBlockPos());
        level.setBlockAndUpdate(source, Blocks.AIR.defaultBlockState());
        TestChunks.release(level, forced);
        helper.succeed();
    }

    /** Where the neighbour's chunk isn't loaded, nothing is known to be there: the band is open. */
    @GameTest(template = TEMPLATE)
    public static void missingNeighbourIsOpen(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        // Far along the edge from the other tests, so nothing else holds EAST's chunk there.
        BlockPos band = bandCell(geometry, 90);
        BlockPos source = source(geometry, band);
        Set<ChunkPos> forced = load(level, band);
        helper.assertTrue(level.getChunkForCollisions(source.getX() >> 4, source.getZ() >> 4) == null, "EAST's chunk should not be loaded for this test");
        BlockState state = level.getBlockState(band);
        helper.assertTrue(state.getCollisionShape(level, band, CollisionContext.empty()).isEmpty(), "a band cell over a missing chunk should not collide");
        TestChunks.release(level, forced);
        helper.succeed();
    }
}
