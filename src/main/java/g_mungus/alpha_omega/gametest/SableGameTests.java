package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.compat.sable.SableTestOps;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Sable compatibility (design §8.1). Each test passes trivially without Sable; Sable types stay behind
 * {@link SableTestOps} so this class loads either way. Run with {@code -PwithSable}.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class SableGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static boolean sable(GameTestHelper helper) {
        if (ModList.get().isLoaded("sable")) return true;
        helper.succeed();
        return false;
    }

    /** Blocks on a face assemble into a sub-level: they leave the face and can be read in the plot. */
    @GameTest(template = TEMPLATE)
    public static void assemblesOnAFace(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> blocks = List.of(anchor, anchor.east(), anchor.above());
        for (BlockPos pos : blocks) level.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, blocks);
        helper.assertTrue(level.getBlockState(anchor).isAir(), "assembled block left behind at " + anchor);
        helper.assertTrue(level.getBlockState(plot).is(Blocks.GOLD_BLOCK), "plot block not readable at " + plot + ": " + level.getBlockState(plot));
        helper.assertTrue(SableTestOps.exists(level, plot), "no sub-level owns the plot");
        SableTestOps.remove(level, plot);
        helper.succeed();
    }

    /** A sub-level dropped over a face falls onto it and comes to rest. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void fallsOntoTheFace(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlockAndUpdate(anchor, Blocks.IRON_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, List.of(anchor));
        double start = SableTestOps.pose(level, plot)[1];
        helper.runAfterDelay(100, () -> {
            double y = SableTestOps.pose(level, plot)[1];
            // The template's floor is its bottom layer: the block comes to rest on it, one cell lower than it started... or more.
            helper.assertTrue(y < start - 0.5, "the sub-level did not fall: " + start + " -> " + y);
            helper.assertTrue(y > helper.absolutePos(BlockPos.ZERO).getY(), "the sub-level fell through the floor: " + y);
            SableTestOps.remove(level, plot);
            helper.succeed();
        });
    }

    /**
     * A sub-level pushed over an edge, high in the air, carries on onto the next face (design §8.1): its pose moves
     * into that face's storage, where that face owns it, turned rigidly with the edge, and it keeps its momentum in
     * world space (moving on away from the edge it crossed).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void crossesAnEdge(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        CubeGeometry geometry = Cube.of(level);
        int height = 140;
        int y = geometry.planeY + height;
        // A few blocks short of UP's diagonal with EAST at this height, along +x.
        BlockPos anchor = new BlockPos(geometry.centerX(CubeFace.UP) + geometry.radius + height - 6, y, geometry.centerZ() - 50);
        double[] there = geometry.transform(CubeFace.UP, CubeFace.EAST, anchor.getX() + 8, y, anchor.getZ());
        Set<ChunkPos> forced = new HashSet<>();
        for (BlockPos pos : new BlockPos[] {anchor, anchor.east(8), BlockPos.containing(there[0], there[1], there[2])}) {
            if (forced.add(new ChunkPos(pos))) TestChunks.force(level, new ChunkPos(pos));
        }
        level.setBlockAndUpdate(anchor, Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(anchor.north(), Blocks.GOLD_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, List.of(anchor, anchor.north()));
        double[] a = SableTestOps.world(level, plot, plot), b = SableTestOps.world(level, plot, plot.north());
        double[] shape = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        SableTestOps.push(level, plot, 12.0, 4.0, 0.0);
        // Away from UP's edge, in EAST's storage axes.
        double[] outward = CubeGeometry.rotate(CubeFace.UP, CubeFace.EAST, 1, 0, 0);
        double[][] arrived = {null};
        long[] arrivedAt = {-1};
        helper.onEachTick(() -> {
            if (arrived[0] != null || !SableTestOps.exists(level, plot)) return;
            double[] pose = SableTestOps.pose(level, plot);
            if (geometry.faceAt(pose[0], pose[2]) != CubeFace.EAST) return;
            arrived[0] = pose;
            arrivedAt[0] = helper.getTick();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(arrived[0] != null, "the sub-level has not reached EAST");
            helper.assertTrue(helper.getTick() >= arrivedAt[0] + 5, "waiting");
            double[] pose = SableTestOps.pose(level, plot);
            helper.assertTrue(geometry.faceAt(pose[0], pose[2]) == CubeFace.EAST && geometry.ownerAt(CubeFace.EAST, pose[0], pose[1], pose[2]) == CubeFace.EAST,
                "the sub-level should be on EAST, owned there, at " + pose[0] + " " + pose[1] + " " + pose[2]);
            double[] a2 = SableTestOps.world(level, plot, plot), b2 = SableTestOps.world(level, plot, plot.north());
            double[] turned = CubeGeometry.rotate(CubeFace.UP, CubeFace.EAST, shape[0], shape[1], shape[2]);
            double error = Math.abs(b2[0] - a2[0] - turned[0]) + Math.abs(b2[1] - a2[1] - turned[1]) + Math.abs(b2[2] - a2[2] - turned[2]);
            helper.assertTrue(error < 0.05, "the sub-level did not turn with the edge: shape " + (b2[0] - a2[0]) + " " + (b2[1] - a2[1]) + " " + (b2[2] - a2[2])
                + ", expected " + turned[0] + " " + turned[1] + " " + turned[2]);
            double moved = (pose[0] - arrived[0][0]) * outward[0] + (pose[1] - arrived[0][1]) * outward[1] + (pose[2] - arrived[0][2]) * outward[2];
            helper.assertTrue(moved > 0.5, "the sub-level should keep moving away from the edge it crossed: " + moved);
            SableTestOps.remove(level, plot);
            TestChunks.release(level, forced);
        });
    }

    /**
     * A player near an edge sees sub-levels on the other side (design §6.1): one on EAST just past UP's edge is
     * tracked by a player standing on UP near it, thousands of blocks away in storage.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void seenFromTheNeighbouringFace(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        CubeGeometry geometry = Cube.of(level);
        int height = 100;
        int y = geometry.planeY + height;
        // On UP 20 blocks short of the diagonal with EAST, and the EAST cell 10 blocks past it at the same height.
        BlockPos standing = new BlockPos(geometry.centerX(CubeFace.UP) + geometry.radius + height - 20, y, geometry.centerZ() + 40);
        double[] east = geometry.transform(CubeFace.UP, CubeFace.EAST, standing.getX() + 30, y, standing.getZ());
        BlockPos anchor = BlockPos.containing(east[0], east[1], east[2]);
        helper.assertTrue(geometry.faceAt(anchor.getX(), anchor.getZ()) == CubeFace.EAST, "anchor should be on EAST: " + anchor);
        Set<ChunkPos> forced = new HashSet<>();
        for (BlockPos pos : new BlockPos[] {standing, anchor}) {
            if (forced.add(new ChunkPos(pos))) TestChunks.force(level, new ChunkPos(pos));
        }
        // Resting on a stone block, so it stays on EAST rather than falling back over the diagonal.
        level.setBlockAndUpdate(anchor.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(anchor, Blocks.IRON_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, List.of(anchor));
        ServerPlayer player = TestPlayers.mock(helper);
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5, 0.0F, 0.0F);
        helper.succeedWhen(() -> {
            double[] pose = SableTestOps.pose(level, plot);
            helper.assertTrue(geometry.faceAt(pose[0], pose[2]) == CubeFace.EAST, "the sub-level left EAST: " + pose[0] + " " + pose[1] + " " + pose[2]);
            helper.assertTrue(SableTestOps.tracks(level, plot, player), "the player on UP does not track the sub-level just over the edge on EAST");
            SableTestOps.remove(level, plot);
            level.setBlockAndUpdate(anchor.below(), Blocks.AIR.defaultBlockState());
            level.getServer().getPlayerList().remove(player);
            TestChunks.release(level, forced);
        });
    }

    /**
     * Entities resting on a sub-level go over the edge with it (design §8.1): an item lying on a platform that crosses
     * ends up on the next face, still on the platform.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void ridersCrossWithIt(GameTestHelper helper) {
        if (!sable(helper)) return;
        ServerLevel level = helper.getLevel();
        CubeGeometry geometry = Cube.of(level);
        int height = 140;
        int y = geometry.planeY + height;
        BlockPos anchor = new BlockPos(geometry.centerX(CubeFace.UP) + geometry.radius + height - 8, y, geometry.centerZ() + 60);
        double[] there = geometry.transform(CubeFace.UP, CubeFace.EAST, anchor.getX() + 8, y, anchor.getZ());
        Set<ChunkPos> forced = new HashSet<>();
        for (BlockPos pos : new BlockPos[] {anchor, anchor.east(8), BlockPos.containing(there[0], there[1], there[2])}) {
            if (forced.add(new ChunkPos(pos))) TestChunks.force(level, new ChunkPos(pos));
        }
        List<BlockPos> platform = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) platform.add(anchor.offset(dx, 0, dz));
        for (BlockPos pos : platform) level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        BlockPos plot = SableTestOps.assemble(level, anchor, platform);
        double[] top = SableTestOps.world(level, plot, plot);
        // An item: it lands and is carried like any entity in world space, and does not wander off.
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, top[0], top[1] + 0.6, top[2],
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT), 0, 0, 0);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
        boolean[] pushed = {false};
        helper.runAfterDelay(20, () -> {
            // Landed on the platform before it is pushed.
            SableTestOps.push(level, plot, 12.0, 4.0, 0.0);
            pushed[0] = true;
        });
        // Judged once, a few ticks after it reaches EAST: later it may arc back over the ridge, as anything thrown over one does.
        long[] arrivedAt = {-1};
        String[] verdict = {null};
        helper.onEachTick(() -> {
            if (verdict[0] != null || !pushed[0] || !SableTestOps.exists(level, plot)) return;
            double[] pose = SableTestOps.pose(level, plot);
            if (arrivedAt[0] < 0) {
                if (geometry.faceAt(pose[0], pose[2]) == CubeFace.EAST) arrivedAt[0] = helper.getTick();
                return;
            }
            if (helper.getTick() < arrivedAt[0] + 5) return;
            double[] centre = SableTestOps.world(level, plot, plot);
            double distance = Math.sqrt(Math.pow(item.getX() - centre[0], 2) + Math.pow(item.getY() - centre[1], 2) + Math.pow(item.getZ() - centre[2], 2));
            verdict[0] = geometry.faceAt(pose[0], pose[2]) != CubeFace.EAST ? "the platform left EAST again too soon"
                : geometry.faceAt(item.getX(), item.getZ()) == CubeFace.EAST && distance < 3.0 ? ""
                : "the item did not cross with its platform: item " + item.position() + ", platform centre " + centre[0] + " " + centre[1] + " " + centre[2];
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(verdict[0] != null, "the platform has not reached EAST");
            item.discard();
            SableTestOps.remove(level, plot);
            TestChunks.release(level, forced);
            helper.assertTrue(verdict[0].isEmpty(), verdict[0]);
        });
    }
}
