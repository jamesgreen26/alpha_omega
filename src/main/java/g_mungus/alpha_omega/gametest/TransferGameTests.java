package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.network.FaceTransferPayload;
import g_mungus.alpha_omega.transfer.FaceTransfer;
import g_mungus.alpha_omega.transfer.FaceTransfers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Crossing edges (design §5): things past the diagonal move to the same cube point on the next face, upright or with
 * their world-space momentum, and do not come straight back. The tests work in the open air over the middle of
 * each edge, where only edge air and filler are in the way.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TransferGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Height above the face plane of the crossings: over the ridge, in open air. */
    private static final double HEIGHT = 120.0;

    private static CubeGeometry geometry(GameTestHelper helper) {
        CubeGeometry geometry = Cube.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        return geometry;
    }

    /**
     * A point of {@code from}'s storage {@code depth} blocks past the diagonal toward {@code to}, over the middle of
     * the edge ({@code lateral} blocks along it).
     */
    private static Vec3 crossing(CubeGeometry geometry, CubeFace from, CubeFace to, double depth, double lateral) {
        double[] toward = from.toward(to);
        double along = geometry.radius + HEIGHT + depth * Math.sqrt(2.0);
        return new Vec3(geometry.centerX(from) + toward[0] * along + toward[2] * lateral, geometry.planeY + HEIGHT,
            geometry.centerZ() + toward[2] * along + toward[0] * lateral);
    }

    /** Generates the chunks around each point and forces their middle, so entities there tick. */
    private static Set<ChunkPos> load(ServerLevel level, Vec3... points) {
        Set<ChunkPos> forced = new HashSet<>();
        for (Vec3 point : points) {
            ChunkPos centre = new ChunkPos(BlockPos.containing(point));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) level.getChunk(centre.x + dx, centre.z + dz);
            }
            if (forced.add(centre)) level.setChunkForced(centre.x, centre.z, true);
        }
        return forced;
    }

    private static void release(ServerLevel level, Set<ChunkPos> forced) {
        for (ChunkPos chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
    }

    private static Vec3 transformed(CubeGeometry geometry, CubeFace from, CubeFace to, Vec3 pos) {
        double[] p = geometry.transform(from, to, pos.x, pos.y, pos.z);
        return new Vec3(p[0], p[1], p[2]);
    }

    private static ItemEntity floatingItem(ServerLevel level, Vec3 pos) {
        ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(Items.DIAMOND), 0, 0, 0);
        item.setNoGravity(true);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
        return item;
    }

    /** An item past each of the 24 directed edges lands at the same cube point on the next face, and stays. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void itemsCrossEveryEdge(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        List<Vec3> points = new ArrayList<>();
        List<CubeFace[]> edges = new ArrayList<>();
        for (CubeFace from : CubeFace.values()) {
            for (CubeFace to : CubeFace.values()) {
                if (!from.isNeighbour(to)) continue;
                Vec3 pos = crossing(geometry, from, to, 1.0, 3.0);
                points.add(pos);
                points.add(transformed(geometry, from, to, pos));
                edges.add(new CubeFace[] {from, to});
            }
        }
        Set<ChunkPos> forced = load(level, points.toArray(Vec3[]::new));
        List<ItemEntity> items = new ArrayList<>();
        for (int i = 0; i < edges.size(); i++) items.add(floatingItem(level, points.get(2 * i)));
        helper.runAfterDelay(10, () -> {
            for (int i = 0; i < edges.size(); i++) {
                CubeFace from = edges.get(i)[0], to = edges.get(i)[1];
                ItemEntity item = items.get(i);
                Vec3 expected = points.get(2 * i + 1);
                helper.assertTrue(geometry.faceAt(item.getX(), item.getZ()) == to, from + " to " + to + ": item is over " + geometry.faceAt(item.getX(), item.getZ()));
                helper.assertTrue(item.position().distanceTo(expected) < 1e-3, from + " to " + to + ": item at " + item.position() + ", expected " + expected);
                helper.assertTrue(geometry.ownerAt(to, item.getX(), item.getY(), item.getZ()) == to, from + " to " + to + ": not owned where it landed");
                item.discard();
            }
            release(level, forced);
            helper.succeed();
        });
    }

    /** An arrow keeps its momentum in world space: flying level toward an edge, it climbs away from the next face. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void arrowsKeepWorldVelocity(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace from = CubeFace.UP, to = CubeFace.EAST;
        Vec3 start = crossing(geometry, from, to, 0.2, -5.0);
        Set<ChunkPos> forced = load(level, start, transformed(geometry, from, to, start));
        Arrow arrow = EntityType.ARROW.create(level);
        arrow.setNoGravity(true);
        arrow.moveTo(start.x, start.y, start.z);
        double[] toward = from.toward(to);
        Vec3 velocity = new Vec3(toward[0], 0.0, toward[2]).scale(0.5);
        arrow.setDeltaMovement(velocity);
        level.addFreshEntity(arrow);
        double[] expected = CubeGeometry.rotate(from, to, velocity.x, velocity.y, velocity.z);
        helper.succeedWhen(() -> {
            helper.assertTrue(geometry.faceAt(arrow.getX(), arrow.getZ()) == to, "arrow has not crossed");
            Vec3 v = arrow.getDeltaMovement().normalize();
            helper.assertTrue(v.dot(new Vec3(expected[0], expected[1], expected[2]).normalize()) > 0.999, "arrow velocity " + v);
            helper.assertTrue(v.y > 0.99, "an arrow flying level off an edge rises from the next face, got " + v);
            arrow.discard();
            release(level, forced);
        });
    }

    /** A mob crosses upright: looking level at the edge, it ends up looking level away from it on the next face. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void mobsTransferUpright(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace from = CubeFace.UP, to = CubeFace.SOUTH;
        Vec3 start = crossing(geometry, from, to, 1.0, 7.0);
        Vec3 expected = transformed(geometry, from, to, start);
        Set<ChunkPos> forced = load(level, start, expected);
        double[] toward = from.toward(to);
        float yaw = (float) Math.toDegrees(Math.atan2(-toward[0], toward[2]));
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.moveTo(start.x, start.y, start.z, yaw, 0.0F);
        level.addFreshEntity(zombie);
        double[] away = to.toward(from.opposite());
        helper.succeedWhen(() -> {
            helper.assertTrue(geometry.faceAt(zombie.getX(), zombie.getZ()) == to, "zombie has not crossed");
            helper.assertTrue(zombie.position().distanceTo(expected) < 0.05, "zombie at " + zombie.position() + ", expected " + expected);
            Vec3 look = zombie.getViewVector(1.0F);
            helper.assertTrue(look.dot(new Vec3(away[0], away[1], away[2])) > 0.999, "zombie looks " + look);
            zombie.discard();
            release(level, forced);
        });
    }

    /** Short of the margin nothing moves; past it, the arrival is short of the margin back, so it stays. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void noFlipFlopping(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace from = CubeFace.NORTH, to = CubeFace.WEST;
        Vec3 near = crossing(geometry, from, to, FaceTransfer.MARGIN - 0.2, 11.0);
        Vec3 past = crossing(geometry, from, to, FaceTransfer.MARGIN + 0.2, 15.0);
        Set<ChunkPos> forced = load(level, near, past, transformed(geometry, from, to, past));
        ItemEntity stays = floatingItem(level, near);
        ItemEntity crosses = floatingItem(level, past);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(stays.position().distanceTo(near) < 1e-3, "an item short of the margin moved");
            helper.assertTrue(geometry.faceAt(crosses.getX(), crosses.getZ()) == to, "an item past the margin did not cross, or came back");
            stays.discard();
            crosses.discard();
            release(level, forced);
            helper.succeed();
        });
    }

    /** Near a corner, past two diagonals, an item goes to the face that owns its point, and stays there. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void cornersGoToTheOwningFace(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace up = CubeFace.UP;
        double d = geometry.radius + 40.0;
        // UP's storage: local (u, d, v) is cube (x, y, z). Past UP's diagonals toward EAST and SOUTH; EAST dominates.
        Vec3 start = new Vec3(geometry.centerX(up) + d + 1.5, geometry.planeY + 40.0, geometry.centerZ() + d + 0.7);
        CubeFace owner = geometry.ownerAt(up, start.x, start.y, start.z);
        helper.assertTrue(owner == CubeFace.EAST, "expected EAST to own the corner point, got " + owner);
        Vec3 expected = transformed(geometry, up, owner, start);
        Set<ChunkPos> forced = load(level, start, expected);
        ItemEntity item = floatingItem(level, start);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(item.position().distanceTo(expected) < 1e-3, "item at " + item.position() + ", expected " + expected);
            helper.assertTrue(FaceTransfer.destination(geometry, owner, item.getX(), item.getY(), item.getZ()) == null, "item should stay on " + owner);
            item.discard();
            release(level, forced);
            helper.succeed();
        });
    }

    /** A client's claim to have crossed is applied when it fits the server's view, and refused when it does not. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void playerClaimsAreChecked(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace from = CubeFace.UP, to = CubeFace.WEST;
        Vec3 start = crossing(geometry, from, to, FaceTransfer.MARGIN + 0.2, -9.0);
        Vec3 expected = transformed(geometry, from, to, start);
        Set<ChunkPos> forced = load(level, start, expected);
        ServerPlayer player = TestPlayers.mock(helper);
        player.teleportTo(level, start.x, start.y, start.z, 30.0F, 10.0F);
        player.setNoGravity(true);

        FaceTransfers.handleClaim(player, new FaceTransferPayload(CubeFace.EAST.slot(), to.slot(), start.x, start.y, start.z, 30.0F, 10.0F));
        helper.assertTrue(player.position().distanceTo(start) < 1e-6, "a claim from the wrong face moved the player");
        FaceTransfers.handleClaim(player, new FaceTransferPayload(from.slot(), to.slot(), start.x + 50, start.y, start.z, 30.0F, 10.0F));
        helper.assertTrue(player.position().distanceTo(start) < 1e-6, "a claim far from the player moved it");

        FaceTransfers.handleClaim(player, new FaceTransferPayload(from.slot(), to.slot(), start.x, start.y, start.z, 30.0F, 10.0F));
        helper.assertTrue(player.position().distanceTo(expected) < 1e-6, "player at " + player.position() + ", expected " + expected);
        helper.assertTrue(player.getLastSectionPos().equals(SectionPos.of(player)), "the player's chunk tracking did not follow it");
        float[] look = FaceTransfer.rotateLook(FaceTransfer.Mode.UPRIGHT, from, to, 30.0F, 10.0F);
        helper.assertTrue(Math.abs(player.getYRot() - look[0]) < 1e-3 && Math.abs(player.getXRot() - look[1]) < 1e-3, "player look not carried over upright");
        level.getServer().getPlayerList().remove(player);
        release(level, forced);
        helper.succeed();
    }

    /** A player the server finds well past the diagonal (its client never crossed) is moved by the server. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void playersWellPastAreMoved(GameTestHelper helper) {
        CubeGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        CubeFace from = CubeFace.UP, to = CubeFace.NORTH;
        Vec3 shallow = crossing(geometry, from, to, FaceTransfers.FORCE_DEPTH - 1.0, 13.0);
        Vec3 deep = crossing(geometry, from, to, FaceTransfers.FORCE_DEPTH + 0.5, 13.0);
        Set<ChunkPos> forced = load(level, shallow, deep, transformed(geometry, from, to, deep));
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        player.teleportTo(level, shallow.x, shallow.y, shallow.z, 0.0F, 0.0F);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(geometry.faceAt(player.getX(), player.getZ()) == from, "the server moved a player its client may still move");
            player.teleportTo(level, deep.x, deep.y, deep.z, 0.0F, 0.0F);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(geometry.faceAt(player.getX(), player.getZ()) == to, "a player well past the diagonal was not moved");
                level.getServer().getPlayerList().remove(player);
                release(level, forced);
                helper.succeed();
            });
        });
    }
}
