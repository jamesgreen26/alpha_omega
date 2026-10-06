package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.mixin.server.ChunkMapAccessor;
import g_mungus.alpha_omega.mixin.server.TrackedEntityAccessor;
import g_mungus.alpha_omega.neighbour.ImageGeometry;
import g_mungus.alpha_omega.neighbour.ImageTrackingView;
import g_mungus.alpha_omega.neighbour.ImageViews;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Image views on the server ({@code orbifold-implementation.md} phase 6): a player near a seam tracks, loads and ticks
 * the chunks around its image position for each image its view needs, is sent the entities there, and walking along a
 * seam (or to and fro where an image starts) never makes it forget a chunk it then needs again.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class ImageGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** The view distance the mock players ask for (the server's own caps it). */
    private static final int VIEW = 6;
    private static final double HEIGHT = 120.0;

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    private static ChunkPos chunk(double x, double z) {
        return new ChunkPos(BlockPos.containing(x, 0, z));
    }

    /** A mock player flying at {@code (x, z)}, asking for {@link #VIEW}, acknowledging chunks every tick. */
    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestPlayers.mock(helper);
        ClientInformation defaults = ClientInformation.createDefault();
        player.updateOptions(new ClientInformation(defaults.language(), VIEW, defaults.chatVisibility(), defaults.chatColors(),
            defaults.modelCustomisation(), defaults.mainHand(), defaults.textFilteringEnabled(), defaults.allowsListing()));
        TestPlayers.receiveChunks(helper, player);
        player.setNoGravity(true);
        player.teleportTo(level, x, HEIGHT, z, 0.0F, 0.0F);
        return player;
    }

    private static int viewDistance(ServerLevel level, ServerPlayer player) {
        return ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$getPlayerViewDistance(player);
    }

    /**
     * Stands a player at {@code (x, z)} and waits until it has exactly the images {@code expected}, each tracked
     * around its image position, with that chunk loaded and ticking, and the player's view square there in its view.
     */
    private static void tracksImages(GameTestHelper helper, double x, double z, List<Motion> expected) {
        OrbifoldGeometry geometry = geometry(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, x, z);
        helper.succeedWhen(() -> {
            int view = viewDistance(level, player);
            ChunkPos home = chunk(x, z);
            List<Motion> images = ImageViews.current(player).stream().map(ImageTrackingView.Virtual::image).toList();
            helper.assertTrue(player.getChunkTrackingView() instanceof ImageTrackingView, "plain tracking view: " + player.getChunkTrackingView());
            helper.assertTrue(images.equals(expected.stream().sorted(ImageGeometry.ORDER).toList()),
                "images " + images + ", expected " + expected + " (view " + view + ")");
            ImageTrackingView tracking = (ImageTrackingView) player.getChunkTrackingView();
            for (ImageTrackingView.Virtual virtual : ImageViews.current(player)) {
                Motion back = virtual.image().inverse();
                ChunkPos center = new ChunkPos(back.chunkX(home.x), back.chunkZ(home.z));
                helper.assertTrue(virtual.center().equals(center), virtual + " should be centred on " + center);
                helper.assertTrue(tracking.virtuals().contains(virtual), "tracking view lacks " + virtual);
                // Its whole square of live chunks is in view; the rest of it is not.
                for (int dx = -view; dx <= view; dx++) {
                    for (int dz = -view; dz <= view; dz++) {
                        int cx = center.x + dx, cz = center.z + dz;
                        if (!ImageGeometry.withinView(center.x, center.z, view, cx, cz, false)) continue;
                        boolean live = ImageGeometry.live(geometry, cx, cz);
                        boolean inHome = ImageGeometry.withinView(home.x, home.z, view, cx, cz, true);
                        if (live) helper.assertTrue(tracking.contains(cx, cz, false), virtual.image() + " does not track " + cx + "," + cz);
                        else if (!inHome) helper.assertFalse(tracking.contains(cx, cz, true), virtual.image() + " tracks dead chunk " + cx + "," + cz);
                    }
                }
                if (!ImageGeometry.live(geometry, center.x, center.z)) continue;
                helper.assertTrue(level.getChunkSource().getChunkNow(center.x, center.z) != null, "image centre " + center + " of " + virtual.image() + " is not loaded");
                helper.assertTrue(level.shouldTickBlocksAt(center.toLong()), "image centre " + center + " of " + virtual.image() + " does not tick");
            }
            level.getServer().getPlayerList().remove(player);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void eastSeamImageIsTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        tracksImages(helper, g.maxX - 8.5, 200.5, List.of(g.east));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void westBandImageIsTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        // Standing in the band past the west seam: its frame is T+, and its image beyond is T−.
        tracksImages(helper, g.minX - 20.5, -300.5, List.of(g.west));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void northFoldImageIsTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        tracksImages(helper, 400.5, g.northRow + 8.5, List.of(g.northFold));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void nearNTheImageOverlapsHome(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        // Twenty blocks from N: the north fold's image position is just across N, and the squares overlap.
        tracksImages(helper, 14.5, g.northRow + 14.5, List.of(g.northFold));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void southFoldImagesAreTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        tracksImages(helper, g.a / 4 + 300.5, g.southRow - 8.5, List.of(g.southFold));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void southFoldAtWImageIsTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        tracksImages(helper, -g.a / 4 - 300.5, g.southRow - 8.5, List.of(g.southFold.then(g.west)));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void imagesAtFAreTracked(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        tracksImages(helper, g.maxX - 8.5, g.northRow + 8.5, List.of(g.east, g.northFold, g.northFold.then(g.east)));
    }

    /** Whether a player is sent an entity. */
    private static boolean sees(ServerLevel level, ServerPlayer player, Zombie zombie) {
        Object tracker = ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$entityMap().get(zombie.getId());
        return tracker != null && ((TrackedEntityAccessor) tracker).alpha_omega$seenBy().contains(player.connection);
    }

    /** A zombie stored at {@code there} is sent to a player standing at {@code standing}. */
    private static void sentAcross(GameTestHelper helper, Vec3 standing, Vec3 there) {
        ServerLevel level = helper.getLevel();
        // Keep the zombie's chunk loaded until the player's image view reaches it.
        ChunkPos zombieChunk = chunk(there.x, there.z);
        TestChunks.force(level, zombieChunk);
        ServerPlayer player = player(helper, standing.x, standing.z);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPersistenceRequired();
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.moveTo(there.x, HEIGHT, there.z);
        level.addFreshEntity(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(sees(level, player, zombie), "the player at " + standing + " is not sent the zombie at " + there + "; images "
                + ImageViews.current(player));
            zombie.discard();
            TestChunks.release(level, zombieChunk);
            level.getServer().getPlayerList().remove(player);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void mobPastTheSeamIsSent(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        // The zombie stands at the tile's west edge: 14 blocks east of a player just inside the east seam.
        sentAcross(helper, new Vec3(g.maxX - 8.5, 0, TestPlaces.at(g, 600.5, g.northRow + 1216.5)), new Vec3(g.minX + 6.5, 0, TestPlaces.at(g, 603.5, g.northRow + 1219.5)));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void mobInTheFarBandIsSent(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        // The zombie is stored in the east band, so it is physically just inside the west seam, next to the player.
        sentAcross(helper, new Vec3(g.minX + 8.5, 0, TestPlaces.at(g, 900.5, g.northRow + 1376.5)), new Vec3(g.maxX + 12.5, 0, TestPlaces.at(g, 902.5, g.northRow + 1378.5)));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 3000)
    public static void mobAcrossTheNorthFoldIsSent(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        // Across the fold from (−300, zN + 6) is the turned tile: the zombie 10 blocks north of the player is stored at
        // R_N of that point, 300 blocks east.
        Vec3 across = new Vec3(-300.5, 0, g.northRow - 4.5);
        Vec3 stored = new Vec3(g.northFold.pointX(across.x), 0, g.northFold.pointZ(across.z));
        sentAcross(helper, new Vec3(-300.5, 0, g.northRow + 6.5), stored);
    }

    /**
     * Moves a player one block per tick from {@code from} by {@code (dx, dz)} per tick for {@code ticks} ticks, then
     * back if asked, and fails if a chunk outside its own square left its view and came back on the way, within
     * {@code within} ticks of leaving.
     */
    private static void walk(GameTestHelper helper, Vec3 from, double dx, double dz, int ticks, boolean andBack, int within) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, from.x, from.z);
        LongSet[] view = {null};
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap left = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        LongSet churned = new LongOpenHashSet();
        int[] tick = {-40};
        int[] most = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] > 0) {
                int step = andBack && tick[0] > ticks ? 2 * ticks - tick[0] : Math.min(tick[0], ticks);
                player.teleportTo(level, from.x + dx * step, HEIGHT, from.z + dz * step, 0.0F, 0.0F);
            }
            LongSet now = new LongOpenHashSet();
            player.getChunkTrackingView().forEach(pos -> now.add(pos.toLong()));
            most[0] = Math.max(most[0], ImageViews.current(player).size());
            if (view[0] != null && tick[0] > 0) {
                for (long chunk : view[0]) if (!now.contains(chunk)) left.put(chunk, tick[0]);
                // Chunks of the player's own square come and go as vanilla's do (walking back brings them back);
                // an image's must not.
                net.minecraft.server.level.ChunkTrackingView.Positioned home = player.getChunkTrackingView() instanceof ImageTrackingView tracking ? tracking.home() : null;
                for (long chunk : now) {
                    boolean own = home != null && home.contains(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true);
                    if (!view[0].contains(chunk) && left.containsKey(chunk) && tick[0] - left.get(chunk) <= within && !own) churned.add(chunk);
                }
            }
            view[0] = now;
            if (tick[0] == (andBack ? 2 * ticks : ticks) + 5) {
                level.getServer().getPlayerList().remove(player);
                AlphaOmegaMod.LOGGER.info("Image walk from {} by ({}, {}) x {}{}: {} chunks left the view, {} came back, at most {} images, {} lingering",
                    from, dx, dz, ticks, andBack ? " and back" : "", left.size(), churned.size(), most[0], ImageViews.lingering(player).size());
                if (most[0] == 0) helper.fail("the walk never had an image");
                else if (!churned.isEmpty()) helper.fail(churned.size() + " chunks left the view and came back, e.g. " + new ChunkPos(churned.iterator().nextLong()));
                else helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void walkingAlongTheEastSeamKeepsView(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        walk(helper, new Vec3(g.maxX - 10.5, 0, TestPlaces.at(g, -1500.5, g.northRow + 692.5)), 0.0, 1.0, 320, false, Integer.MAX_VALUE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void walkingAlongTheNorthFoldKeepsView(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        walk(helper, new Vec3(400.5, 0, g.northRow + 10.5), 1.0, 0.0, 320, false, Integer.MAX_VALUE);
    }

    /**
     * Through N, the image's square runs the other way along the fold, over chunks the player's own square has just
     * left: those come back, as the geometry says. None may flicker: leave and come back within a few ticks.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void walkingThroughNKeepsView(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        walk(helper, new Vec3(-160.5, 0, g.northRow + 10.5), 1.0, 0.0, 320, false, 4);
    }

    /**
     * Out from the east seam past where its image is no longer needed (view + band) and back: the image's square
     * lingers, so none of its chunks leave the view on the way, and the image comes back.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void anImageNoLongerNeededLingers(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        int view = Math.min(VIEW, ((ChunkMapAccessor) level.getChunkSource().chunkMap).alpha_omega$serverViewDistance());
        double start = g.maxX - (view + 1) * 16 - g.band + 24.5;
        double z = TestPlaces.at(g, -2100.5, g.northRow + 592.5);
        ServerPlayer player = player(helper, start, z);
        int steps = 56;
        int[] tick = {-40};
        LongSet[] square = {null};
        String[] problem = {null};
        boolean[] lingered = {false};
        helper.onEachTick(() -> {
            tick[0]++;
            if (tick[0] > 0) {
                int step = tick[0] > steps ? Math.max(0, 2 * steps - tick[0]) : tick[0];
                player.teleportTo(level, start - step, HEIGHT, z, 0.0F, 0.0F);
            }
            List<ImageTrackingView.Virtual> current = ImageViews.current(player);
            if (tick[0] > 0 && current.isEmpty() && square[0] == null) {
                // The image just went: everything its square held must stay in view while it lingers.
                square[0] = new LongOpenHashSet();
                ImageTrackingView tracking = (ImageTrackingView) player.getChunkTrackingView();
                for (ImageTrackingView.Virtual virtual : ImageViews.lingering(player)) {
                    tracking.forEach(pos -> {
                        if (ImageGeometry.withinView(virtual.center().x, virtual.center().z, tracking.viewDistance(), pos.x, pos.z, true)
                            && ImageGeometry.live(g, pos.x, pos.z)) square[0].add(pos.toLong());
                    });
                }
                lingered[0] = !ImageViews.lingering(player).isEmpty();
            }
            if (square[0] != null && current.isEmpty() && problem[0] == null) {
                for (long chunk : square[0]) {
                    if (!player.getChunkTrackingView().contains(new ChunkPos(chunk))) problem[0] = "chunk " + new ChunkPos(chunk) + " left the view at tick " + tick[0];
                }
            }
            if (tick[0] == 2 * steps + 5) {
                level.getServer().getPlayerList().remove(player);
                AlphaOmegaMod.LOGGER.info("Image lingering: square of {} chunks, lingered {}, problem {}", square[0] == null ? -1 : square[0].size(), lingered[0], problem[0]);
                if (square[0] == null) helper.fail("the image was never dropped");
                else if (!lingered[0]) helper.fail("the dropped image did not linger");
                else if (problem[0] != null) helper.fail(problem[0]);
                else if (ImageViews.current(player).isEmpty()) helper.fail("the image did not come back");
                else helper.succeed();
            }
        });
    }
}
