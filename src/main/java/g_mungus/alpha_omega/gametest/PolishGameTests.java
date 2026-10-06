package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.api.FrameTransferEvent;
import g_mungus.alpha_omega.command.OrbifoldTeleport;
import g_mungus.alpha_omega.item.Compasses;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.NearestImages;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.HexOrbifoldProjection;
import g_mungus.alpha_omega.sky.PlanetProjection;
import g_mungus.alpha_omega.sky.ProjectionInverse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 12 (polish): {@code /orbifold tp}, compasses through images, the public API against the geometry, and the
 * transfer event. Sites: the cone points (teleports only, nothing built); the north fold at {@code x = 160..260}, both
 * sides (a lodestone and a marker block at {@code y = 200} in the tile, their copies past the fold at
 * {@code x = −261..−161}; a flight at {@code y = 230} at {@code x = 300}), clear of the pole platform at {@code x = 0}
 * and the bridge sites from {@code x = 400}.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class PolishGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int HEIGHT = 200;

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    private static void run(ServerPlayer player, String command, int permission) {
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(permission).withSuppressedOutput(), command);
    }

    /** That a player stands on the surface at its position: at the motion-blocking height there. */
    private static void assertOnSurface(GameTestHelper helper, ServerPlayer player, String what) {
        ServerLevel level = helper.getLevel();
        int x = player.getBlockX(), z = player.getBlockZ();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        helper.assertTrue(Math.abs(player.getY() - surface) < 1e-6, what + ": at y " + player.getY() + ", the surface is at " + surface);
        helper.assertTrue(!level.getBlockState(new BlockPos(x, surface - 1, z)).isAir(), what + ": standing on air");
    }

    /** {@code /orbifold tp N|F|E|W}: in the tile, a few blocks from the cone point, on the surface. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void tpToEachConePoint(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerPlayer player = TestPlayers.mock(helper);
        for (OrbifoldGeometry.ConePoint cone : g.conePoints()) {
            run(player, "orbifold tp " + cone.name(), 2);
            String what = "tp " + cone.name();
            helper.assertTrue(g.isTile(player.getBlockX(), player.getBlockZ()), what + ": not in the tile at " + player.position());
            double from = NearestImages.distance(g, cone.x(), cone.z(), player.getX(), player.getZ());
            double expected = Math.hypot(OrbifoldTeleport.CONE_OFFSET + 0.5, cone.name().equals("F") ? OrbifoldTeleport.CONE_OFFSET + 0.5 : 0.5);
            helper.assertTrue(Math.abs(from - expected) < 1e-6, what + ": " + from + " blocks from the cone point, expected " + expected);
            assertOnSurface(helper, player, what);
        }
        // Without permission level 2 nothing happens.
        Vec3 before = player.position();
        run(player, "orbifold tp N", 0);
        helper.assertTrue(player.position().equals(before), "tp ran without permission");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** {@code /orbifold tp <lat> <lon>}: in the tile, where the projection puts that place, on the surface. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void tpToLatitudeLongitude(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        HexOrbifoldProjection projection = HexOrbifoldProjection.of(g);
        ServerPlayer player = TestPlayers.mock(helper);
        double[][] places = {{30.0, 45.0}, {-60.0, -120.0}, {0.0, 0.0}, {89.99, 0.0}, {-89.5, 100.0}};
        for (double[] place : places) {
            run(player, String.format(java.util.Locale.ROOT, "orbifold tp %s %s", place[0], place[1]), 2);
            String what = "tp " + place[0] + " " + place[1];
            helper.assertTrue(g.isTile(player.getBlockX(), player.getBlockZ()), what + ": not in the tile at " + player.position());
            PlanetProjection.Position at = projection.project(player.getX(), player.getZ());
            double[] u = ProjectionInverse.unit(at.latitude(), at.longitude());
            double[] v = ProjectionInverse.unit(Math.toRadians(place[0]), place[1] / 360.0);
            double chord = Math.sqrt(Math.pow(u[0] - v[0], 2) + Math.pow(u[1] - v[1], 2) + Math.pow(u[2] - v[2], 2));
            double blocks = chord / Math.max(projection.skySpeed(player.getX(), player.getZ()), 1e-6);
            helper.assertTrue(blocks < 2.0, what + ": landed " + blocks + " blocks of arc away, at " + player.position());
            assertOnSurface(helper, player, what);
        }
        // Spawn is 0° 0°.
        run(player, "orbifold tp 0 0", 2);
        helper.assertTrue(Math.hypot(player.getX() - g.spawnX, player.getZ() - g.spawnZ) < 2.0, "0 0 is not spawn: " + player.position());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** The north-fold site: a tile cell at {@code (x, zN + dz)} and its copy past the fold. */
    private static BlockPos site(OrbifoldGeometry g, int x, int dz) {
        return new BlockPos(x, HEIGHT, g.northRow + dz);
    }

    private static Set<ChunkPos> forceBoth(ServerLevel level, OrbifoldGeometry g, BlockPos source) {
        Set<ChunkPos> forced = new HashSet<>();
        BlockPos copy = g_mungus.alpha_omega.orbifold.Transform.of(g.northFold).block(source);
        for (BlockPos pos : List.of(source, copy)) {
            ChunkPos chunk = new ChunkPos(pos);
            if (forced.add(chunk)) TestChunks.force(level, chunk);
        }
        return forced;
    }

    /**
     * A lodestone compass held past the north fold aims at the lodestone's copy beside the holder (turned, a few blocks
     * off), not at the stored lodestone a fold away; a compass bound to that copy keeps tracking (the lodestone's POI
     * is found through the copy) and, held in the tile by the source, aims at the source.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void lodestoneCompassInTheBandAimsThroughImages(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        BlockPos lodestone = site(g, 200, 8);
        BlockPos copy = g_mungus.alpha_omega.orbifold.Transform.of(g.northFold).block(lodestone);
        Set<ChunkPos> forced = forceBoth(level, g, lodestone);
        level.setBlock(lodestone, Blocks.LODESTONE.defaultBlockState(), 3);
        ServerPlayer player = TestPlayers.mock(helper);
        // Past the fold, 20 blocks north of the copy: in the band, in the fold's frame.
        player.teleportTo(level, copy.getX() + 0.5, HEIGHT + 1, copy.getZ() - 19.5, 0.0F, 0.0F);
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, lodestone)), true));
        helper.succeedWhen(() -> {
            helper.assertTrue(g.isBand(player.getBlockX(), player.getBlockZ()), "the holder should be in the band, at " + player.position());
            helper.assertTrue(level.getBlockState(copy).is(Blocks.LODESTONE), "the band copy of the lodestone is not there yet");
            GlobalPos target = compass.get(DataComponents.LODESTONE_TRACKER).target().orElseThrow();
            GlobalPos aimed = Compasses.aim(player, target);
            helper.assertTrue(aimed.pos().equals(copy), "aims at " + aimed.pos() + ", expected the copy " + copy);
            // South, toward the copy: vanilla's angle in turns from east toward south is a quarter.
            double angle = Compasses.angle(player, target.pos());
            helper.assertTrue(Math.abs(angle - 0.25) < 0.01, "needle angle " + angle + ", expected 0.25 (south)");
            // Bound to the copy: the server keeps it tracked, and from the tile beside the source it aims at the source.
            LodestoneTracker bandBound = new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, copy)), true);
            helper.assertTrue(bandBound.tick(level).target().isPresent(), "a compass bound to the band copy lost its target");
            Vec3 byTheSource = Vec3.atCenterOf(lodestone).add(0, 0, 10);
            helper.assertTrue(Compasses.aim(level, copy, byTheSource).equals(lodestone), "from the tile, the band-bound compass should aim at the source");
            level.setBlock(lodestone, Blocks.AIR.defaultBlockState(), 3);
            level.getServer().getPlayerList().remove(player);
            TestChunks.release(level, forced);
        });
    }

    /** The public API answers as the geometry does, its copies hold the same block, and a map made past the fold is the source's map. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void apiAgreesWithTheGeometry(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        g_mungus.alpha_omega.api.Orbifold api = g_mungus.alpha_omega.api.Orbifold.of(level);
        helper.assertTrue(api != null && api.geometry() == g, "the API should see the overworld's geometry");
        for (ServerLevel other : level.getServer().getAllLevels()) {
            helper.assertTrue(g_mungus.alpha_omega.api.Orbifold.isOrbifold(other) == (Orbifold.of(other) != null), other.dimension() + ": isOrbifold disagrees");
        }
        BlockPos marker = site(g, 230, 5);
        BlockPos copy = g_mungus.alpha_omega.orbifold.Transform.of(g.northFold).block(marker);
        Set<ChunkPos> forced = forceBoth(level, g, marker);
        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        // Canon, frame, copies, toFrame and nearest.
        helper.assertTrue(api.canon(copy).equals(marker) && api.frame(copy).equals(g.northFold), "canon of the copy");
        helper.assertTrue(api.isTile(marker) && api.isBand(copy) && !api.isBand(marker), "regions");
        List<BlockPos> copies = api.copies(copy);
        helper.assertTrue(copies.get(0).equals(marker) && copies.contains(copy), "copies of the copy: " + copies);
        Vec3 inBand = Vec3.atCenterOf(copy).add(0, 0, -10);
        helper.assertTrue(g_mungus.alpha_omega.api.Orbifold.toFrame(level, marker, inBand).equals(copy), "toFrame into the band");
        helper.assertTrue(api.nearest(marker, inBand).equals(copy), "nearest from the band");
        // A map made at the copy is centred as one made at the source.
        ItemStack fromBand = MapItem.create(level, copy.getX(), copy.getZ(), (byte) 0, true, false);
        ItemStack fromSource = MapItem.create(level, marker.getX(), marker.getZ(), (byte) 0, true, false);
        MapItemSavedData a = MapItem.getSavedData(fromBand, level), b = MapItem.getSavedData(fromSource, level);
        helper.assertTrue(a.centerX == b.centerX && a.centerZ == b.centerZ, "map centres differ: " + a.centerX + "," + a.centerZ + " vs " + b.centerX + "," + b.centerZ);
        helper.succeedWhen(() -> {
            for (BlockPos pos : copies) {
                if (!g.inFootprint(pos.getX(), pos.getZ())) continue;
                helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "copy " + pos + " does not hold the block yet");
            }
            level.setBlock(marker, Blocks.AIR.defaultBlockState(), 3);
            TestChunks.release(level, forced);
        });
    }

    /** Transfer events seen, for the test running now (main thread only). */
    private static final List<FrameTransferEvent> EVENTS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean LISTENING = new AtomicBoolean();

    private static void listen() {
        if (LISTENING.compareAndSet(false, true)) NeoForge.EVENT_BUS.addListener((FrameTransferEvent event) -> EVENTS.add(event));
    }

    private static List<FrameTransferEvent> eventsFor(Entity entity) {
        List<FrameTransferEvent> list = new ArrayList<>();
        for (FrameTransferEvent event : EVENTS) if (event.entity() == entity) list.add(event);
        return list;
    }

    /**
     * An item flying north over the fold crosses once at {@code H}: one event, reason SEAM, by the fold, from where it
     * was. A minecart carrying a mob crosses together: one event each, both naming the minecart as root.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void transferEventFiresOncePerTransfer(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        listen();
        double startZ = g.northRow - g.band + 0.3;
        Vec3 itemStart = new Vec3(300.5, 230.0, startZ);
        Vec3 cartStart = new Vec3(310.5, 230.0, startZ - 0.5);
        Set<ChunkPos> forced = new HashSet<>();
        for (Vec3 p : List.of(itemStart, cartStart)) {
            for (Vec3 q : List.of(p, p.add(0, 0, -8), g_mungus.alpha_omega.orbifold.Transform.of(g.northFold).position(p))) {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(q));
                if (forced.add(chunk)) TestChunks.force(level, chunk);
            }
        }
        ItemEntity item = new ItemEntity(level, itemStart.x, itemStart.y, itemStart.z, new ItemStack(Items.DIAMOND), 0, 0, -0.4);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        item.setNoGravity(true);
        level.addFreshEntity(item);
        Minecart cart = EntityType.MINECART.create(level);
        cart.setNoGravity(true);
        cart.moveTo(cartStart.x, cartStart.y, cartStart.z, 0.0F, 0.0F);
        level.addFreshEntity(cart);
        Zombie rider = EntityType.ZOMBIE.create(level);
        rider.setNoAi(true);
        rider.setPersistenceRequired();
        rider.moveTo(cartStart.x, cartStart.y, cartStart.z, 0.0F, 0.0F);
        level.addFreshEntity(rider);
        rider.startRiding(cart, true);
        // The cart has no velocity of its own off rails: it starts just past H, like a mob in TransferGameTests.
        helper.runAfterDelay(40, () -> {
            List<FrameTransferEvent> forItem = eventsFor(item);
            helper.assertTrue(forItem.size() == 1, "item: " + forItem.size() + " transfer events, expected 1");
            FrameTransferEvent e = forItem.get(0);
            helper.assertTrue(e.reason() == FrameTransferEvent.Reason.SEAM && e.motion().equals(g.northFold) && e.root() == item,
                "item event: " + e.reason() + " by " + e.motion());
            helper.assertTrue(g.isTile(item.getBlockX(), item.getBlockZ()), "the item should be in the tile, at " + item.position());
            helper.assertTrue(e.from().distanceTo(itemStart) < 40, "event says it came from " + e.from());
            List<FrameTransferEvent> forCart = eventsFor(cart), forRider = eventsFor(rider);
            helper.assertTrue(forCart.size() == 1 && forRider.size() == 1, "cart " + forCart.size() + ", rider " + forRider.size() + " events, expected 1 each");
            helper.assertTrue(forRider.get(0).root() == cart && forCart.get(0).motion().equals(forRider.get(0).motion()), "the rider moved with its cart");
            Motion m = forCart.get(0).motion();
            helper.assertTrue(m.equals(g.northFold), "cart moved by " + m);
            item.discard();
            rider.discard();
            cart.discard();
            EVENTS.removeIf(event -> event.entity() == item || event.entity() == cart || event.entity() == rider);
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }
}
