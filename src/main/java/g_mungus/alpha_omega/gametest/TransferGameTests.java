package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.transfer.FrameTransfer;
import g_mungus.alpha_omega.transfer.FrameTransfers;
import g_mungus.alpha_omega.transfer.Frames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Changing frame ({@code orbifold-implementation.md} phase 5, RS §4): things past a seam move by their position's frame
 * and keep their world-space velocity, chasing mobs keep their targets, villagers their homes, players near each other
 * share a frame, and nothing flips back and forth. The crossings run high in open air, so only the band is in the way.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TransferGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Height of the open-air tests. */
    private static final double HEIGHT = 230.0;
    /** Height of the platforms the walking tests build. */
    private static final int FLOOR = 200;

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    /** Forces the chunk of each point (generating around it), so entities there tick. */
    private static Set<ChunkPos> load(ServerLevel level, List<Vec3> points) {
        Set<ChunkPos> forced = new HashSet<>();
        for (Vec3 point : points) {
            ChunkPos chunk = new ChunkPos(BlockPos.containing(point));
            if (forced.add(chunk)) TestChunks.force(level, chunk);
        }
        return forced;
    }

    /** Forces every chunk of a rectangle of blocks. */
    private static Set<ChunkPos> loadArea(ServerLevel level, double minX, double minZ, double maxX, double maxZ) {
        List<Vec3> points = new ArrayList<>();
        for (double x = minX; x <= maxX + 15; x += 16) {
            for (double z = minZ; z <= maxZ + 15; z += 16) points.add(new Vec3(Math.min(x, maxX), 0, Math.min(z, maxZ)));
        }
        return load(level, points);
    }

    /** Counts an entity's changes of frame: jumps in storage to near an expression of where it was. */
    private static final class FrameChanges {
        final Entity entity;
        Vec3 last;
        int changes;
        Motion lastMotion;

        FrameChanges(Entity entity) {
            this.entity = entity;
            this.last = entity.position();
        }

        void tick(OrbifoldGeometry geometry) {
            Vec3 now = this.entity.position();
            Motion g = Frames.crossing(geometry, this.last.x, this.last.z, now.x, now.z, 16.0);
            if (g != null) {
                this.changes++;
                this.lastMotion = g;
            }
            this.last = now;
        }
    }

    /** One seam crossing for the open-air test: where things start, which way they fly, and the element expected. */
    private record Crossing(String name, Vec3 start, Vec3 direction, Motion expected) {
    }

    /** Starting just short of {@code H} past each kind of seam, flying outward: both ways near F, and across its corner. */
    private static List<Crossing> crossings(OrbifoldGeometry g) {
        double d = g.band - 0.3;
        double zMid = TestPlaces.at(g, -2000.5, g.northRow + 300.5);
        List<Crossing> list = new ArrayList<>();
        list.add(new Crossing("east", new Vec3(g.maxX + d, HEIGHT, zMid), new Vec3(1, 0, 0), g.west));
        list.add(new Crossing("west", new Vec3(g.minX - d, HEIGHT, zMid + 40), new Vec3(-1, 0, 0), g.east));
        list.add(new Crossing("north fold", new Vec3(TestPlaces.at(g, 600.5, 1300.5), HEIGHT, g.northRow - d), new Vec3(0, 0, -1), g.northFold));
        list.add(new Crossing("south fold", new Vec3(g.a / 4.0 + TestPlaces.at(g, 600.5, 300.5), HEIGHT, g.southRow + d), new Vec3(0, 0, 1), g.southFold));
        list.add(new Crossing("east near F", new Vec3(g.maxX + d, HEIGHT, g.northRow + 20.5), new Vec3(1, 0, 0), g.west));
        list.add(new Crossing("west near F", new Vec3(g.minX - d, HEIGHT, g.northRow + 20.5), new Vec3(-1, 0, 0), g.east));
        list.add(new Crossing("north fold near F", new Vec3(g.maxX - 20.5, HEIGHT, g.northRow - d), new Vec3(0, 0, -1), g.northFold));
        list.add(new Crossing("corner at F", new Vec3(g.maxX + d, HEIGHT, g.northRow - d), new Vec3(1, 0, -1).normalize(),
            g.frame(g.maxX + d + 1, g.northRow - d - 1)));
        return list;
    }

    private static Entity spawn(ServerLevel level, String kind, Vec3 at, Vec3 velocity) {
        Entity entity = switch (kind) {
            case "item" -> {
                ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND), 0, 0, 0);
                item.setNeverPickUp();
                item.setUnlimitedLifetime();
                yield item;
            }
            case "arrow" -> EntityType.ARROW.create(level);
            case "minecart" -> EntityType.MINECART.create(level);
            default -> {
                Zombie zombie = EntityType.ZOMBIE.create(level);
                zombie.setNoAi(true);
                zombie.setPersistenceRequired();
                yield zombie;
            }
        };
        entity.setNoGravity(true);
        float yaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        entity.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        entity.setDeltaMovement(velocity);
        level.addFreshEntity(entity);
        return entity;
    }

    /**
     * Items, arrows, minecarts and mobs flying out past every kind of seam (and both ways near F, and over its corner)
     * cross at {@code H}, by the seam's element, into the tile, keeping their world-space velocity and facing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void everythingCrossesEverySeam(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        List<Crossing> crossings = crossings(g);
        List<Vec3> points = new ArrayList<>();
        for (Crossing c : crossings) {
            for (int i = 0; i < 4; i++) {
                Vec3 p = c.start.add(new Vec3(-c.direction.z, 0, c.direction.x).scale(3.0 * i));
                for (double ahead : new double[] {0.0, 2.0}) {
                    Vec3 q = p.add(c.direction.scale(ahead));
                    points.add(q);
                    points.add(Transform.of(g.frame(q.x + c.direction.x, q.z + c.direction.z)).position(q));
                }
            }
        }
        Set<ChunkPos> forced = load(level, points);
        String[] kinds = {"item", "arrow", "minecart", "zombie"};
        List<Entity> entities = new ArrayList<>();
        List<Crossing> of = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        for (Crossing c : crossings) {
            for (int i = 0; i < kinds.length; i++) {
                // Side by side along the seam, so they do not collide.
                Vec3 side = new Vec3(-c.direction.z, 0, c.direction.x).scale(3.0 * i);
                // A mob without AI does not drift: it starts just past H instead.
                Vec3 at = c.start.add(side).add(c.direction.scale(kinds[i].equals("zombie") ? 0.5 : 0.0));
                Entity entity = spawn(level, kinds[i], at, c.direction.scale(0.4));
                entities.add(entity);
                of.add(c);
                yaws.add(entity.getYRot());
                starts.add(at);
            }
        }
        helper.runAfterDelay(30, () -> {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                Crossing c = of.get(i);
                String what = entity.getType().toShortString() + " at " + c.name;
                // In the tile, a short flight on from where its start lands under the seam's element.
                Vec3 landed = Transform.of(c.expected).position(starts.get(i));
                helper.assertTrue(g.isTile((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ())) && entity.position().distanceTo(landed) < 16.0,
                    what + " should have crossed by " + c.expected + " to near " + landed + ", but is at " + entity.position());
                Vec3 expectedDirection = Transform.of(c.expected).vector(c.direction);
                Vec3 v = entity.getDeltaMovement();
                if (!(entity instanceof Zombie)) {
                    helper.assertTrue(v.horizontalDistance() > 1e-3 && new Vec3(v.x, 0, v.z).normalize().dot(expectedDirection) > 0.99,
                        what + ": velocity " + v + " should point along " + expectedDirection);
                }
                if (entity instanceof Zombie) {
                    float expectedYaw = c.expected.yaw(yaws.get(i));
                    helper.assertTrue(Math.abs(Math.IEEEremainder(entity.getYRot() - expectedYaw, 360.0)) < 1e-3,
                        what + ": yaw " + entity.getYRot() + ", expected " + expectedYaw);
                }
                entity.discard();
            }
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }

    /** Builds a stone floor at {@link #FLOOR} over a rectangle of blocks; band cells write through to their sources. */
    private static void floor(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) level.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE.defaultBlockState(), 2);
        }
    }

    private static void clearFloor(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) level.setBlock(new BlockPos(x, FLOOR, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /**
     * A zombie chases a player across the north fold and back. The player starts on the far side of the fold in
     * storage, 25 blocks off in the world: the zombie joins its frame and walks to it. Then the player walks north,
     * over the fold and on until it crosses by the fold itself; the zombie follows it into its new frame at once and
     * catches up. Its target never changes.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 2400)
    public static void zombieChasesAcrossTheFold(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        int zN = g.northRow;
        // At other sizes the same layout, mirrored onto x = ±1200 (clear of the other tests' sites).
        int fx = TestPlaces.at(g, 600, 1200);
        // One floor across the fold near x = 600, and its other half near x = −600: the band past the fold at each is
        // the tile at the other.
        Set<ChunkPos> forced = loadArea(level, fx - 10, zN - 55, fx + 11, zN + 55);
        forced.addAll(loadArea(level, -fx - 11, zN - 55, -fx + 10, zN + 55));
        floor(level, fx - 4, zN - 45, fx + 5, zN + 44);
        floor(level, -fx - 6, zN - 45, -fx + 3, zN + 44);
        ServerPlayer player = TestPlayers.survival(helper);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100000, 4, false, false));
        // Facing north (yaw 180).
        player.teleportTo(level, -fx - 0.5, FLOOR + 1, zN + 12.5, 180.0F, 0.0F);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPersistenceRequired();
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.moveTo(fx + 0.5, FLOOR + 1, zN + 12.5, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        zombie.setTarget(player);
        FrameChanges changes = new FrameChanges(zombie);
        FrameChanges playerChanges = new FrameChanges(player);
        int[] phase = {0};
        int[] ticks = {0};
        String[] lost = {null};
        helper.onEachTick(() -> {
            if (phase[0] > 2) return;
            ticks[0]++;
            changes.tick(g);
            playerChanges.tick(g);
            if (zombie.getTarget() != player && lost[0] == null) lost[0] = "target became " + zombie.getTarget() + " at tick " + ticks[0];
            double near = zombie.position().distanceTo(player.position());
            if (phase[0] == 0 && near < 3.0 && changes.changes >= 1) {
                phase[0] = 1;
            } else if (phase[0] == 1) {
                // Walk north over the fold until the player crosses by it.
                if (playerChanges.changes == 0) step(level, player, 0.15);
                else if (near < 3.0 && changes.changes >= 2) phase[0] = 2;
            }
            if (phase[0] == 2 || ticks[0] > 2200) {
                boolean reached = phase[0] == 2;
                String state = "phase " + phase[0] + ", zombie changed frame " + changes.changes + " times, player " + playerChanges.changes + ", "
                    + near + " blocks apart in storage, zombie at " + zombie.position() + ", player at " + player.position();
                phase[0] = 3;
                String failure = lost[0];
                zombie.discard();
                level.getServer().getPlayerList().remove(player);
                clearFloor(level, fx - 4, zN - 45, fx + 5, zN + 44);
                clearFloor(level, -fx - 6, zN - 45, -fx + 3, zN + 44);
                TestChunks.release(level, forced);
                if (failure != null) helper.fail("the zombie lost its target: " + failure + " (" + state + ")");
                else if (!reached) helper.fail("the zombie did not catch the player: " + state);
                else helper.succeed();
            }
        });
    }

    /**
     * A villager keeps its home and job site across a transfer: both move with it into the new frame, at the same
     * blocks in the world. A home the move takes past the band stays at its source, and a walk target there is dropped.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void villagerKeepsHomeAndJobSite(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        Vec3 at = new Vec3(g.maxX + 40.5, HEIGHT, TestPlaces.at(g, -1500.5, g.northRow + 400.5));
        Vec3 arrived = Transform.of(g.west).position(at);
        Set<ChunkPos> forced = load(level, List.of(at, arrived));
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setPersistenceRequired();
        villager.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(villager);
        BlockPos home = BlockPos.containing(at.x + 5, at.y, at.z + 2);
        BlockPos job = BlockPos.containing(at.x - 30, at.y, at.z - 4);
        BlockPos farWalk = BlockPos.containing(at.x - 120, at.y, at.z);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(Level.OVERWORLD, home));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(Level.OVERWORLD, job));
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(Level.OVERWORLD, farWalk));
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
            new net.minecraft.world.entity.ai.behavior.BlockPosTracker(farWalk));
        helper.runAfterDelay(2, () -> {
            FrameTransfers.transfer(level, villager, g.west, null);
            Transform t = Transform.of(g.west);
            helper.assertTrue(villager.position().distanceTo(arrived) < 1e-6, "villager at " + villager.position() + ", expected " + arrived);
            Optional<GlobalPos> newHome = villager.getBrain().getMemory(MemoryModuleType.HOME);
            Optional<GlobalPos> newJob = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            helper.assertTrue(newHome.isPresent() && newHome.get().pos().equals(t.block(home)), "home " + newHome + ", expected " + t.block(home));
            helper.assertTrue(newJob.isPresent() && newJob.get().pos().equals(t.block(job)), "job site " + newJob + ", expected " + t.block(job));
            // The meeting point is 120 blocks back, past the band from the new frame: it stays at its source in the tile.
            Optional<GlobalPos> meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
            helper.assertTrue(meeting.isPresent() && meeting.get().pos().equals(farWalk), "meeting point " + meeting + ", expected " + farWalk);
            helper.assertFalse(villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).isPresent(), "a look target past the band should be cleared");
            villager.discard();
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }

    /** Moves a mock player one step along its facing each tick (its yaw turns with any transfer). */
    private static void step(ServerLevel level, ServerPlayer player, double speed) {
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(speed);
        player.moveTo(player.getX() + look.x, player.getY(), player.getZ() + look.z, player.getYRot(), 0.0F);
        level.getChunkSource().move(player);
    }

    /**
     * No ping-pong: a player walking along a seam inside the band, and one circling N at 20 blocks, never transfer;
     * a cow beside each changes frame at most once per follower period.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200, batch = "transfer_near_n")
    public static void noPingPong(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        int zN = g.northRow;
        int lane = TestPlaces.at(g, -1200, g.northRow + 500);
        Set<ChunkPos> forced = loadArea(level, g.maxX - 40, lane, g.maxX + 40, lane + 200);
        forced.addAll(loadArea(level, g.minX - 40, lane, g.minX + 40, lane + 200));
        forced.addAll(loadArea(level, -40, zN - 40, 40, zN + 40));
        ServerPlayer walker = TestPlayers.mock(helper);
        walker.setNoGravity(true);
        // 20 blocks into the band past the east seam, walking south along it.
        walker.teleportTo(level, g.maxX + 20.5, HEIGHT, lane + 10.5, 0.0F, 0.0F);
        ServerPlayer circler = TestPlayers.mock(helper);
        circler.setNoGravity(true);
        circler.teleportTo(level, 20.5, HEIGHT, zN + 0.5, 0.0F, 0.0F);
        Cow walkerCow = EntityType.COW.create(level);
        walkerCow.setNoAi(true);
        walkerCow.setNoGravity(true);
        walkerCow.setPersistenceRequired();
        walkerCow.moveTo(g.minX + 8.5, HEIGHT, lane + 50.5);
        level.addFreshEntity(walkerCow);
        Cow circlerCow = EntityType.COW.create(level);
        circlerCow.setNoAi(true);
        circlerCow.setNoGravity(true);
        circlerCow.setPersistenceRequired();
        circlerCow.moveTo(0.5, HEIGHT, zN + 12.5);
        level.addFreshEntity(circlerCow);
        List<FrameChanges> watched = List.of(new FrameChanges(walker), new FrameChanges(circler), new FrameChanges(walkerCow), new FrameChanges(circlerCow));
        int ticks = 600;
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0] > ticks) return;
            tick[0]++;
            step(level, walker, 0.3);
            double a = tick[0] * 0.3 / 20.0;
            circler.moveTo(20.0 * Math.cos(a), HEIGHT, zN + 20.0 * Math.sin(a), 0.0F, 0.0F);
            level.getChunkSource().move(circler);
            watched.forEach(w -> w.tick(g));
            if (tick[0] == ticks) {
                String counts = "walker " + watched.get(0).changes + ", circler " + watched.get(1).changes + ", walker's cow " + watched.get(2).changes
                    + ", circler's cow " + watched.get(3).changes;
                AlphaOmegaMod.LOGGER.info("No ping-pong over {} ticks: changes of frame {}", ticks, counts);
                walkerCow.discard();
                circlerCow.discard();
                level.getServer().getPlayerList().remove(walker);
                level.getServer().getPlayerList().remove(circler);
                TestChunks.release(level, forced);
                if (watched.get(0).changes != 0 || watched.get(1).changes != 0) helper.fail("a player changed frame: " + counts);
                else if (watched.get(2).changes > 1) helper.fail("the cow beside the seam walker flipped: " + counts);
                else if (watched.get(3).changes > ticks / FrameTransfer.FOLLOW_COOLDOWN_TICKS) helper.fail("the cow near N flipped too often: " + counts);
                else helper.succeed();
            }
        });
    }

    /** Two players walking toward each other across the east seam end side by side in storage: one frame. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void twoPlayersMeetInOneFrame(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        double z = TestPlaces.at(g, -2600.5, g.northRow + 450.5);
        Set<ChunkPos> forced = loadArea(level, g.maxX - 70, z - 20, g.maxX + 70, z + 20);
        forced.addAll(loadArea(level, g.minX - 70, z - 20, g.minX + 70, z + 20));
        ServerPlayer a = TestPlayers.mock(helper);
        ServerPlayer b = TestPlayers.mock(helper);
        a.setNoGravity(true);
        b.setNoGravity(true);
        // Yaw −90 faces east (+x), 90 west.
        a.teleportTo(level, g.maxX - 50.5, HEIGHT, z, -90.0F, 0.0F);
        b.teleportTo(level, g.minX + 50.5, HEIGHT, z, 90.0F, 0.0F);
        int[] tick = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            tick[0]++;
            double apart = Frames.distance(g, b.getX(), b.getZ(), a.getX(), a.getZ());
            if (apart > 4.0) {
                step(level, a, 0.25);
                step(level, b, 0.25);
                if (tick[0] > 1000) {
                    done[0] = true;
                    helper.fail("the players never met: " + a.position() + ", " + b.position());
                }
                return;
            }
            // Met: give the group check a period to settle, then they must be beside each other in storage.
            if (tick[0] % 40 != 0) return;
            done[0] = true;
            double storage = a.position().distanceTo(b.position());
            level.getServer().getPlayerList().remove(a);
            level.getServer().getPlayerList().remove(b);
            TestChunks.release(level, forced);
            if (storage > 5.0) helper.fail("the players met in different frames: " + a.position() + " and " + b.position());
            else helper.succeed();
        });
    }

    /** A player standing still at each cone point never transfers. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "transfer_cone_points")
    public static void standingAtAConePointStays(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> players = new ArrayList<>();
        List<Vec3> at = new ArrayList<>();
        for (OrbifoldGeometry.ConePoint cone : g.conePoints()) at.add(new Vec3(cone.x() + 0.3, HEIGHT, cone.z() - 0.3));
        Set<ChunkPos> forced = load(level, at);
        for (Vec3 p : at) {
            ServerPlayer player = TestPlayers.mock(helper);
            player.setNoGravity(true);
            player.teleportTo(level, p.x, p.y, p.z, 0.0F, 0.0F);
            players.add(player);
        }
        helper.runAfterDelay(200, () -> {
            for (int i = 0; i < players.size(); i++) {
                ServerPlayer player = players.get(i);
                helper.assertTrue(player.position().distanceTo(at.get(i)) < 1e-6, "a player at " + at.get(i) + " moved to " + player.position());
                level.getServer().getPlayerList().remove(player);
            }
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }
}
