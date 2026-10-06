package g_mungus.alpha_omega.gametest;

import com.mojang.authlib.GameProfile;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.bridge.BridgeCounters;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges into the other frame ({@code orbifold-implementation.md} phase 7, RS §5), across the east–west seam, the
 * north fold and the south fold.
 *
 * <p>Each scenario uses a {@link Site}: a tile cell just inside a seam (the <b>owner</b>: its block, block entity and
 * POI record live there) and its copy just past the other side of that seam. Things placed on the tile side of the copy
 * are in the world right beside the owner, but in storage they are the tile's width (or a fold's span) away from it:
 * in the other frame. Only a bridge can connect the two.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class BridgeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int Y = 200;

    enum Seam {
        EAST, NORTH, SOUTH
    }

    /**
     * A tile cell {@code owner} two or three cells inside a seam, its {@code copy} past the seam on the far side of it,
     * and {@code inward}, the way from the copy back into the tile. {@code outward} points from the owner to its seam.
     */
    record Site(String name, BlockPos owner, BlockPos copy, Direction inward, Direction outward) {

        /** The tile cell {@code k} cells in from the copy: beside the owner in the world, in the other frame. */
        BlockPos across(int k) {
            return this.copy.relative(this.inward, k);
        }

        Direction lateral() {
            return this.inward.getClockWise();
        }
    }

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) throw new GameTestAssertException("the gametest overworld should be an orbifold world");
        return geometry;
    }

    /**
     * Sites, 32 apart per lane: along the east seam (owner by the west edge, its copy in the east band), along the north
     * fold either side of N, and along the south fold west of E. Clear of the other classes' sites ({@link TestPlaces}).
     */
    static Site site(GameTestHelper helper, Seam seam, int lane) {
        OrbifoldGeometry g = geometry(helper);
        BlockPos owner;
        Motion frame;
        Direction inward, outward;
        switch (seam) {
            case EAST -> {
                owner = new BlockPos(g.minX + 2, Y, TestPlaces.at(g, -3600, g.northRow + 1040) + 32 * lane);
                frame = g.west;
                inward = Direction.WEST;
                outward = Direction.WEST;
            }
            case NORTH -> {
                owner = new BlockPos(400 + 32 * lane, Y, g.northRow + 2);
                frame = g.northFold;
                inward = Direction.SOUTH;
                outward = Direction.NORTH;
            }
            default -> {
                owner = new BlockPos(TestPlaces.at(g, 2500, 300) + 32 * lane, Y, g.southRow - 3);
                frame = g.southFold;
                inward = Direction.NORTH;
                outward = Direction.SOUTH;
            }
        }
        OrbifoldGeometry.Cell copy = g.copies(owner.getX(), owner.getZ()).stream().filter(c -> c.frame().equals(frame)).findFirst()
            .orElseThrow(() -> new GameTestAssertException("no copy of " + owner.toShortString() + " across the " + seam + " seam"));
        return new Site(seam.name().toLowerCase(java.util.Locale.ROOT), owner, new BlockPos(copy.x(), Y, copy.z()), inward, outward);
    }

    /** Forces the chunks round the owner and round the copy (both sides of it), with every copy of each. */
    private static Set<ChunkPos> load(ServerLevel level, Site site, int radius) {
        OrbifoldGeometry g = Orbifold.of(level);
        Set<ChunkPos> chunks = new HashSet<>();
        for (BlockPos center : List.of(site.owner, site.copy, site.across(radius))) {
            for (int dx = -radius; dx <= radius + 15; dx += 16) {
                for (int dz = -radius; dz <= radius + 15; dz += 16) {
                    int x = center.getX() + Math.min(dx, radius), z = center.getZ() + Math.min(dz, radius);
                    chunks.add(new ChunkPos(new BlockPos(x, 0, z)));
                    if (!g.inFootprint(x, z)) continue;
                    OrbifoldGeometry.Cell source = g.canon(x, z);
                    chunks.add(new ChunkPos(new BlockPos(source.x(), 0, source.z())));
                    for (OrbifoldGeometry.Cell copy : g.copies(source.x(), source.z())) chunks.add(new ChunkPos(new BlockPos(copy.x(), 0, copy.z())));
                }
            }
        }
        for (ChunkPos chunk : chunks) TestChunks.force(level, chunk);
        return chunks;
    }

    /** Fails with {@code message} after cleaning up, unless {@code condition}. */
    private static void check(boolean condition, String message, Runnable cleanup) {
        if (condition) return;
        cleanup.run();
        throw new GameTestAssertException(message);
    }

    // ---- Pressure plates: entity box queries ----

    /**
     * A wooden pressure plate is owned in the tile; an item lies on its copy past the seam. The plate was pressed in its
     * owner's frame (powered, with its tick scheduled there), so every re-check runs at the owner, whose box holds no
     * entity in its own frame: the plate stays down only if the box query finds the item through its image. Once the
     * item is gone, the plate comes up.
     */
    private static void pressurePlate(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 0);
        Set<ChunkPos> forced = load(level, site, 8);
        BlockPos plate = site.owner;
        level.setBlock(plate.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(plate, Blocks.OAK_PRESSURE_PLATE.defaultBlockState().setValue(PressurePlateBlock.POWERED, true), 3);
        level.scheduleTick(plate, Blocks.OAK_PRESSURE_PLATE, 2);
        ItemEntity item = new ItemEntity(level, site.copy.getX() + 0.5, Y + 0.05, site.copy.getZ() + 0.5, new ItemStack(Items.STICK), 0, 0, 0);
        item.setNoGravity(true);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
        Runnable cleanup = () -> {
            item.discard();
            level.setBlock(plate, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(plate.below(), Blocks.AIR.defaultBlockState(), 2 | 16);
            TestChunks.release(level, forced);
        };
        int[] tick = {0};
        List<String> up = new ArrayList<>();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            tick[0]++;
            boolean owner = level.getBlockState(plate).getValue(PressurePlateBlock.POWERED);
            boolean copy = level.getBlockState(site.copy).is(Blocks.OAK_PRESSURE_PLATE) && level.getBlockState(site.copy).getValue(PressurePlateBlock.POWERED);
            if (tick[0] <= 60) {
                if ((!owner || !copy) && up.size() < 4) up.add("tick " + tick[0] + ": owner " + owner + ", copy " + copy);
                if (tick[0] == 60) {
                    boolean ticking = level.getBlockTicks().hasScheduledTick(plate, Blocks.OAK_PRESSURE_PLATE);
                    if (!up.isEmpty() || !ticking) {
                        done[0] = true;
                        cleanup.run();
                        helper.fail("across " + site.name + ": the plate should stay down, re-checked at its owner: " + up + ", owner's tick pending " + ticking);
                        return;
                    }
                    item.discard();
                }
            } else if (tick[0] > 60 + 30 || !owner && !copy) {
                done[0] = true;
                cleanup.run();
                if (owner || copy) helper.fail("across " + site.name + ": the plate stayed down after the item left (owner " + owner + ", copy " + copy + ")");
                else helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_plate", timeoutTicks = 300)
    public static void pressurePlateEast(GameTestHelper helper) {
        pressurePlate(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_plate", timeoutTicks = 300)
    public static void pressurePlateNorth(GameTestHelper helper) {
        pressurePlate(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_plate", timeoutTicks = 300)
    public static void pressurePlateSouth(GameTestHelper helper) {
        pressurePlate(helper, Seam.SOUTH);
    }

    // ---- Spawners: player proximity ----

    /** A spawner owned in the tile spawns while the only player stands six blocks from its copy, in the other frame. */
    private static void spawner(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 1);
        Set<ChunkPos> forced = load(level, site, 12);
        BlockPos spawner = site.owner;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) level.setBlock(spawner.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2 | 16);
        }
        level.setBlock(spawner, Blocks.SPAWNER.defaultBlockState(), 3);
        if (level.getBlockEntity(spawner) instanceof SpawnerBlockEntity entity) entity.setEntityId(EntityType.ARMOR_STAND, level.getRandom());
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        Vec3 stand = site.across(6).getBottomCenter();
        player.teleportTo(level, stand.x, stand.y, stand.z, 0.0F, 0.0F);
        AABB around = new AABB(spawner).inflate(6.0);
        Runnable cleanup = () -> {
            level.getEntitiesOfClass(ArmorStand.class, around.inflate(4.0)).forEach(Entity::discard);
            level.getServer().getPlayerList().remove(player);
            level.setBlock(spawner, Blocks.AIR.defaultBlockState(), 3);
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) level.setBlock(spawner.offset(dx, -1, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            TestChunks.release(level, forced);
        };
        check(player.distanceToSqr(spawner.getCenter()) > 100 * 100, "the player should be far from the spawner in storage", cleanup);
        check(level.hasNearbyAlivePlayer(spawner.getX() + 0.5, spawner.getY() + 0.5, spawner.getZ() + 0.5, 16.0),
            "across " + site.name + ": the spawner should see the player through its copy", cleanup);
        helper.runAfterDelay(120, () -> {
            int spawned = level.getEntitiesOfClass(ArmorStand.class, around).size();
            cleanup.run();
            if (spawned == 0) helper.fail("across " + site.name + ": the spawner spawned nothing with a player beside its copy");
            else helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_spawner", timeoutTicks = 300)
    public static void spawnerEast(GameTestHelper helper) {
        spawner(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_spawner", timeoutTicks = 300)
    public static void spawnerNorth(GameTestHelper helper) {
        spawner(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_spawner", timeoutTicks = 300)
    public static void spawnerSouth(GameTestHelper helper) {
        spawner(helper, Seam.SOUTH);
    }

    // ---- Villagers: POIs ----

    /** A box of floor (stone below {@code Y}, air above) round the copy, from four cells past it to {@code depth} cells in. */
    private static void floor(ServerLevel level, Site site, int depth, boolean clear) {
        for (int k = -4; k <= depth; k++) {
            for (int l = -5; l <= 5; l++) {
                BlockPos at = site.copy.relative(site.inward, k).relative(site.lateral(), l);
                level.setBlock(at.below(), clear ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 2; dy++) level.setBlock(at.above(dy), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    /**
     * A bed is owned in the tile; a villager stands in the tile across the seam from its copy. The villager must find
     * the bed through the POI bridge, claim it (the owner's ticket), hold it as its home at the copy beside it, and keep
     * it while vanilla's validation ({@code ValidateNearbyPoi} for homes) runs every tick for a while.
     */
    private static void villager(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 2);
        Set<ChunkPos> forced = load(level, site, 24);
        floor(level, site, 14, false);
        BlockPos head = site.owner, foot = head.relative(site.outward.getOpposite());
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, site.outward);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPersistenceRequired();
        Vec3 start = site.across(8).getBottomCenter();
        villager.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
        level.addFreshEntity(villager);
        PoiManager pois = level.getPoiManager();
        Runnable cleanup = () -> {
            villager.discard();
            level.setBlock(head, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(foot, Blocks.AIR.defaultBlockState(), 2 | 16);
            floor(level, site, 14, true);
            TestChunks.release(level, forced);
        };
        check(pois.existsAtPosition(PoiTypes.HOME, head), "the bed's POI should be recorded at its owner " + head.toShortString(), cleanup);
        BehaviorControl<net.minecraft.world.entity.LivingEntity> validate = ValidateNearbyPoi.create(h -> h.is(PoiTypes.HOME), MemoryModuleType.HOME);
        long[] claimedAt = {-1};
        boolean[] done = {false};
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (done[0]) return;
            ticks[0]++;
            Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (claimedAt[0] < 0) {
                if (home.isPresent()) {
                    claimedAt[0] = ticks[0];
                    AlphaOmegaMod.LOGGER.info("Bridge villager {}: claimed {} after {} ticks (owner {}, copy {})", site.name, home.get().pos().toShortString(),
                        ticks[0], head.toShortString(), site.copy.toShortString());
                } else if (ticks[0] > 900) {
                    done[0] = true;
                    String where = villager.position().toString();
                    cleanup.run();
                    helper.fail("across " + site.name + ": the villager at " + where + " never claimed the bed (owner " + head.toShortString() + ", copy "
                        + site.copy.toShortString() + ")");
                }
                return;
            }
            // Vanilla's home validation, every tick (villagers run it only at night).
            validate.tryStart(level, villager, level.getGameTime());
            if (ticks[0] - claimedAt[0] < 200) {
                if (home.isEmpty()) {
                    done[0] = true;
                    cleanup.run();
                    helper.fail("across " + site.name + ": the villager lost its home " + (ticks[0] - claimedAt[0]) + " ticks after claiming it");
                }
                return;
            }
            done[0] = true;
            List<String> wrong = new ArrayList<>();
            BlockPos held = home.map(GlobalPos::pos).orElse(null);
            if (held == null) wrong.add("no home");
            else if (!held.equals(site.copy)) wrong.add("home " + held.toShortString() + " should be the bed's copy " + site.copy.toShortString() + " beside it");
            if (pois.getFreeTickets(head) != 0) wrong.add("the owner's record should have its ticket taken, has " + pois.getFreeTickets(head) + " free");
            if (pois.getFreeTickets(site.copy) != 0) wrong.add("the copy should report the owner's tickets");
            if (!pois.existsAtPosition(PoiTypes.HOME, site.copy)) wrong.add("the copy should report the owner's POI");
            cleanup.run();
            if (wrong.isEmpty()) helper.succeed();
            else helper.fail("across " + site.name + ": " + wrong);
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_villager", timeoutTicks = 1400)
    public static void villagerClaimsBedEast(GameTestHelper helper) {
        villager(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_villager", timeoutTicks = 1400)
    public static void villagerClaimsBedNorth(GameTestHelper helper) {
        villager(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_villager", timeoutTicks = 1400)
    public static void villagerClaimsBedSouth(GameTestHelper helper) {
        villager(helper, Seam.SOUTH);
    }

    // ---- Sculk sensors: game events ----

    /**
     * A sculk sensor owned in the tile hears an eating sound five blocks from its copy, in the other frame: it activates
     * with that event's frequency.
     */
    private static void sculk(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 3);
        Set<ChunkPos> forced = load(level, site, 12);
        BlockPos sensor = site.owner;
        level.setBlock(sensor.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(sensor, Blocks.SCULK_SENSOR.defaultBlockState(), 3);
        Runnable cleanup = () -> {
            level.setBlock(sensor, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(sensor.below(), Blocks.AIR.defaultBlockState(), 2 | 16);
            TestChunks.release(level, forced);
        };
        Vec3 event = site.across(5).getCenter();
        int expected = VibrationSystem.getGameEventFrequency(GameEvent.EAT);
        // Let anything the setup made it hear die down first, then make the sound and wait for the sensor.
        int[] tick = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            tick[0]++;
            if (tick[0] < 60) return;
            BlockState state = level.getBlockState(sensor);
            if (tick[0] == 60) {
                if (SculkSensorBlock.getPhase(state) != SculkSensorPhase.INACTIVE) {
                    done[0] = true;
                    cleanup.run();
                    helper.fail("across " + site.name + ": the sensor should be quiet before the test, is " + state);
                    return;
                }
                level.gameEvent(GameEvent.EAT, event, GameEvent.Context.of((Entity) null, null));
                return;
            }
            boolean active = SculkSensorBlock.getPhase(state) == SculkSensorPhase.ACTIVE;
            if (!active && tick[0] < 100) return;
            done[0] = true;
            int heard = level.getBlockEntity(sensor) instanceof SculkSensorBlockEntity entity ? entity.getLastVibrationFrequency() : -1;
            cleanup.run();
            if (!active) helper.fail("across " + site.name + ": the sensor did not hear an event five blocks from its copy");
            else if (heard != expected) helper.fail("across " + site.name + ": the sensor heard frequency " + heard + ", expected " + expected);
            else helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_sculk", timeoutTicks = 300)
    public static void sculkHearsEast(GameTestHelper helper) {
        sculk(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_sculk", timeoutTicks = 300)
    public static void sculkHearsNorth(GameTestHelper helper) {
        sculk(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_sculk", timeoutTicks = 300)
    public static void sculkHearsSouth(GameTestHelper helper) {
        sculk(helper, Seam.SOUTH);
    }

    // ---- Chests: interaction range and the opener count ----

    /**
     * A chest is owned in the tile; a player three blocks back from its copy, in the other frame, opens the copy (as
     * using it does, without the interaction pull). The menu's block entity is the owner's, at the owner's position, far
     * away in storage: the menu stays open only if the range check measures to the copy, and the lid stays open only if
     * the opener recount finds the player through the box bridge.
     */
    private static void chest(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 4);
        Set<ChunkPos> forced = load(level, site, 8);
        BlockPos chest = site.owner;
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        Vec3 stand = site.across(3).getBottomCenter();
        player.teleportTo(level, stand.x, stand.y, stand.z, 0.0F, 0.0F);
        Runnable cleanup = () -> {
            player.closeContainer();
            level.getServer().getPlayerList().remove(player);
            level.setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
            TestChunks.release(level, forced);
        };
        BlockHitResult hit = new BlockHitResult(site.copy.getCenter(), Direction.UP, site.copy, false);
        level.getBlockState(site.copy).useWithoutItem(level, player, hit);
        check(player.containerMenu instanceof ChestMenu menu && menu.getContainer() == level.getBlockEntity(chest),
            "across " + site.name + ": using the chest's copy should open the owner's chest, opened " + player.containerMenu, cleanup);
        int[] tick = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) return;
            tick[0]++;
            boolean open = player.containerMenu instanceof ChestMenu;
            int openers = ChestBlockEntity.getOpenCount(level, chest);
            if (open && openers > 0 && tick[0] < 80) return;
            done[0] = true;
            cleanup.run();
            if (!open) helper.fail("across " + site.name + ": the chest's menu closed after " + tick[0] + " ticks, with the player " + 3 + " blocks from its copy");
            else if (openers == 0) helper.fail("across " + site.name + ": the chest lost its opener after " + tick[0] + " ticks");
            else helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_chest", timeoutTicks = 200)
    public static void chestStaysOpenEast(GameTestHelper helper) {
        chest(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_chest", timeoutTicks = 200)
    public static void chestStaysOpenNorth(GameTestHelper helper) {
        chest(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_chest", timeoutTicks = 200)
    public static void chestStaysOpenSouth(GameTestHelper helper) {
        chest(helper, Seam.SOUTH);
    }

    // ---- Sounds and particles: broadcast ----

    /** A mock player whose every outgoing packet is recorded in {@code sink}. */
    private static ServerPlayer capturing(GameTestHelper helper, List<Packet<?>> sink) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                sink.add(packet);
                super.send(packet, listener, flush);
            }
        };
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /**
     * A sound and a particle at a cell owned in the tile reach a player four blocks from the cell's copy, in the other
     * frame, and not a player in the tile's interior.
     */
    private static void broadcast(GameTestHelper helper, Seam seam) {
        ServerLevel level = helper.getLevel();
        Site site = site(helper, seam, 5);
        Set<ChunkPos> forced = load(level, site, 8);
        List<Packet<?>> near = new CopyOnWriteArrayList<>(), far = new CopyOnWriteArrayList<>();
        ServerPlayer nearPlayer = capturing(helper, near), farPlayer = capturing(helper, far);
        nearPlayer.setNoGravity(true);
        farPlayer.setNoGravity(true);
        Vec3 stand = site.across(4).getBottomCenter(), interior = site.across(300).getBottomCenter();
        nearPlayer.teleportTo(level, stand.x, stand.y, stand.z, 0.0F, 0.0F);
        farPlayer.teleportTo(level, interior.x, interior.y, interior.z, 0.0F, 0.0F);
        helper.runAfterDelay(5, () -> {
            near.clear();
            far.clear();
            Vec3 at = site.owner.getCenter();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HEART, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            Predicate<Packet<?>> sound = p -> p instanceof ClientboundSoundPacket s && Math.abs(s.getX() - at.x) < 0.2 && Math.abs(s.getZ() - at.z) < 0.2;
            Predicate<Packet<?>> particle = p -> p instanceof ClientboundLevelParticlesPacket s && Math.abs(s.getX() - at.x) < 0.2 && Math.abs(s.getZ() - at.z) < 0.2;
            boolean nearSound = near.stream().anyMatch(sound), nearParticle = near.stream().anyMatch(particle);
            boolean farSound = far.stream().anyMatch(sound), farParticle = far.stream().anyMatch(particle);
            level.getServer().getPlayerList().remove(nearPlayer);
            level.getServer().getPlayerList().remove(farPlayer);
            TestChunks.release(level, forced);
            List<String> wrong = new ArrayList<>();
            if (!nearSound) wrong.add("the sound did not reach the player beside the copy");
            if (!nearParticle) wrong.add("the particle did not reach the player beside the copy");
            if (farSound || farParticle) wrong.add("the interior player got sound " + farSound + ", particle " + farParticle);
            if (wrong.isEmpty()) helper.succeed();
            else helper.fail("across " + site.name + ": " + wrong);
        });
    }

    @GameTest(template = TEMPLATE, batch = "bridge_broadcast", timeoutTicks = 100)
    public static void broadcastReachesEast(GameTestHelper helper) {
        broadcast(helper, Seam.EAST);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_broadcast", timeoutTicks = 100)
    public static void broadcastReachesNorth(GameTestHelper helper) {
        broadcast(helper, Seam.NORTH);
    }

    @GameTest(template = TEMPLATE, batch = "bridge_broadcast", timeoutTicks = 100)
    public static void broadcastReachesSouth(GameTestHelper helper) {
        broadcast(helper, Seam.SOUTH);
    }

    // ---- Away from the edges ----

    /**
     * One query of every bridged kind round {@code at}: a box query, nearest and nearby player, a sound and a particle,
     * a POI area query and point lookups, a game event, a reach check and a structure lookup. Returns what it set up, to
     * clean up after.
     */
    private static Runnable probe(ServerLevel level, ServerPlayer player, BlockPos at) {
        ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, new ItemStack(Items.STICK), 0, 0, 0);
        item.setNoGravity(true);
        item.setNeverPickUp();
        level.addFreshEntity(item);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        BlockPos head = at.above(3), foot = head.south();
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        player.teleportTo(level, at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2.0));
        level.getNearestPlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 16.0, false);
        level.hasNearbyAlivePlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 16.0);
        level.playSound(null, at, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), at, 8, PoiManager.Occupancy.ANY).count();
        level.getPoiManager().exists(head, h -> h.is(PoiTypes.HOME));
        level.getPoiManager().getType(at);
        level.gameEvent(GameEvent.EAT, at.getCenter(), GameEvent.Context.of((Entity) null, null));
        player.canInteractWithBlock(at, 1.0);
        level.structureManager().getStructureWithPieceAt(at, StructureTags.VILLAGE);
        return () -> {
            item.discard();
            level.setBlock(head, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(foot, Blocks.AIR.defaultBlockState(), 2 | 16);
        };
    }

    /**
     * Away from the edges the bridges do nothing: a probe of every bridged query in the tile's interior adds no bridge
     * work there. The same probe at a copy past the north fold does (the check that the watch works).
     */
    @GameTest(template = TEMPLATE, batch = "bridge_away", timeoutTicks = 200)
    public static void awayFromEdgesNothingChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbifoldGeometry g = geometry(helper);
        BlockPos interior = new BlockPos(-300, Y, (g.northRow + g.southRow) / 2);
        Site edge = site(helper, Seam.NORTH, 6);
        Set<ChunkPos> forced = new HashSet<>();
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) forced.add(new ChunkPos(interior.offset(dx, 0, dz)));
        }
        forced.forEach(chunk -> TestChunks.force(level, chunk));
        forced.addAll(load(level, edge, 8));
        ServerPlayer player = TestPlayers.mock(helper);
        player.setNoGravity(true);
        helper.runAfterDelay(2, () -> {
            BridgeCounters.watch(interior.getX() - 200, interior.getZ() - 200, interior.getX() + 200, interior.getZ() + 200);
            Runnable undo = probe(level, player, interior);
            Map<BridgeCounters.Kind, Long> inside = BridgeCounters.watched();
            undo.run();
            BridgeCounters.watch(edge.copy.getX() - 20, edge.copy.getZ() - 20, edge.copy.getX() + 20, edge.copy.getZ() + 20);
            undo = probe(level, player, edge.copy);
            Map<BridgeCounters.Kind, Long> atEdge = BridgeCounters.watched();
            undo.run();
            BridgeCounters.stopWatching();
            level.getServer().getPlayerList().remove(player);
            TestChunks.release(level, forced);
            AlphaOmegaMod.LOGGER.info("Bridges away from the edges: {}; at a copy past the north fold: {}; {}", inside, atEdge, BridgeCounters.lines());
            Set<BridgeCounters.Kind> expected = EnumSet.of(BridgeCounters.Kind.ENTITIES, BridgeCounters.Kind.PLAYERS, BridgeCounters.Kind.BROADCAST,
                BridgeCounters.Kind.POI, BridgeCounters.Kind.POI_OWNER, BridgeCounters.Kind.GAME_EVENTS, BridgeCounters.Kind.INTERACTION,
                BridgeCounters.Kind.STRUCTURES);
            Set<BridgeCounters.Kind> missing = EnumSet.copyOf(expected);
            missing.removeIf(kind -> atEdge.getOrDefault(kind, 0L) > 0);
            if (!inside.isEmpty()) helper.fail("bridges did work in the tile's interior: " + inside);
            else if (!missing.isEmpty()) helper.fail("the probe at the edge should run every bridge; these did not: " + missing + " (ran " + atEdge + ")");
            else helper.succeed();
        });
    }
}
