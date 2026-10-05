package g_mungus.alpha_omega.gametest;

import com.mojang.datafixers.util.Either;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.sky.LocalSky;
import g_mungus.alpha_omega.sky.PlanetProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Gameplay follows the local sun ({@link LocalSky}), at three places on the default orbifold world:
 * <ul>
 * <li><b>Spawn</b>, at 0° 0°: exactly vanilla's sun and clock.</li>
 * <li><b>The far side</b>, near the equator about 170° of longitude away (about 11½ hours ahead): when it has daylight
 * the global clock, which is spawn's, says night, and the other way round.</li>
 * <li><b>The north pole</b>, cone point {@code N}, 5,204 blocks north of spawn at k = 4: the sun circles the horizon,
 * so it is never day, and sleeping there lasts until local noon.</li>
 * </ul>
 * Each test sets the global time, so each runs in its own batch.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LocalTimeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int PLATFORM_Y = 200;

    private enum Site {
        SPAWN, FAR_SIDE, POLE
    }

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    /** Where a site is: spawn; near the equator on the far side; a few blocks into the tile from {@code N}. */
    private static BlockPos at(GameTestHelper helper, Site site) {
        OrbifoldGeometry geometry = geometry(helper);
        return switch (site) {
            case SPAWN -> new BlockPos(geometry.spawnX, PLATFORM_Y, geometry.spawnZ);
            // (5632, −4736) at k = 4: 3.5° S, 171.7° E.
            case FAR_SIDE -> new BlockPos(geometry.a * 11 / 30, PLATFORM_Y, geometry.northRow + geometry.b / 26);
            // Inside the tile, so the platform stays clear of the band: 3.5 blocks from N, a hair from the pole.
            case POLE -> new BlockPos(0, PLATFORM_Y, geometry.northRow + 3);
        };
    }

    /** A site, its chunk generated and forced so that it ticks, standing on a stone platform, under a clear sky. */
    private static BlockPos platform(GameTestHelper helper, Site site) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = at(helper, site);
        PlanetProjection.Position position = LocalSky.position(level, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (position == null) helper.fail("the local sky should apply in the gametest overworld");
        if (site == Site.FAR_SIDE && Math.abs(position.longitude()) < 0.45) helper.fail("the far side is at " + position);
        if (site == Site.POLE && position.latitude() < Math.toRadians(89.99)) helper.fail("the pole is at " + position);
        TestChunks.force(level, new ChunkPos(pos));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        level.setWeatherParameters(24000, 0, false, false);
        return pos;
    }

    private static void release(GameTestHelper helper, BlockPos pos) {
        TestChunks.release(helper.getLevel(), new ChunkPos(pos));
    }

    /** Sets the global time, some days in, so that the local clock at {@code pos} reads {@code localTicks}. */
    private static void setLocalTime(ServerLevel level, BlockPos pos, long localTicks) {
        long base = 24000L * 10;
        long best = base;
        long bestError = Long.MAX_VALUE;
        for (long t = base; t < base + 24000; t += 5) {
            level.setDayTime(t);
            long error = Math.abs(Math.floorMod(LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5) - localTicks + 12000, 24000L) - 12000);
            if (error < bestError) {
                bestError = error;
                best = t;
            }
        }
        level.setDayTime(best);
        level.updateSkyBrightness();
    }

    private static Zombie zombie(ServerLevel level, BlockPos pos) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.setPersistenceRequired();
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(zombie);
        return zombie;
    }

    // ---- Spawn ----

    /** At spawn the local sky is vanilla's, all day: the clock, daylight and sky darkening. */
    @GameTest(template = TEMPLATE, batch = "localtime_spawn")
    public static void spawnHasVanillasSun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.SPAWN);
        for (long t = 240000; t < 264000; t += 250) {
            level.setDayTime(t);
            level.updateSkyBrightness();
            long local = LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5);
            helper.assertTrue(Math.abs(local - t) <= 1, "local clock at spawn " + local + " for day time " + t);
            // The middle of the spawn block is a fraction of a tick ahead of spawn itself: equal, or a step off at a change.
            int darken = LocalSky.skyDarken(level, pos);
            helper.assertTrue(Math.abs(darken - level.getSkyDarken()) <= 1, "sky darkening at spawn " + darken + ", vanilla " + level.getSkyDarken() + " at " + t);
            if (darken == level.getSkyDarken()) {
                helper.assertTrue(LocalSky.isDay(level, pos) == level.isDay(), "spawn's day differs from vanilla's at " + t);
            } else {
                level.setDayTime(t + 2);
                level.updateSkyBrightness();
                helper.assertTrue(darken == level.getSkyDarken(), "sky darkening at spawn " + darken + " is not vanilla's a tick later, at " + t);
            }
        }
        release(helper, pos);
        helper.succeed();
    }

    // ---- The far side ----

    @GameTest(template = TEMPLATE, batch = "localtime_brightness")
    public static void brightnessFollowsTheLocalSun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        setLocalTime(level, pos, 18000);
        helper.assertTrue(level.isDay(), "it should be day by the global clock (spawn's)");
        helper.assertTrue(!LocalSky.isDay(level, pos), "it should be night on the far side");
        int local = level.getMaxLocalRawBrightness(pos);
        int global = level.getMaxLocalRawBrightness(pos, level.getSkyDarken());
        helper.assertTrue(local <= 4 && global >= 14, "brightness at local midnight " + local + ", by the global sun " + global);
        release(helper, pos);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "localtime_burn", timeoutTicks = 400)
    public static void zombiesBurnAtLocalNoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        setLocalTime(level, pos, 6000);
        helper.assertTrue(!level.isDay(), "it should be night by the global clock");
        Zombie zombie = zombie(level, pos);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isOnFire(), "zombie at local noon is not burning");
            zombie.discard();
            release(helper, pos);
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_noburn", timeoutTicks = 300)
    public static void zombiesDoNotBurnAtLocalMidnight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        setLocalTime(level, pos, 18000);
        helper.assertTrue(level.isDay(), "it should be day by the global clock");
        Zombie zombie = zombie(level, pos);
        helper.runAfterDelay(200, () -> {
            boolean burning = zombie.isOnFire();
            zombie.discard();
            release(helper, pos);
            helper.assertTrue(!burning, "zombie at local midnight is burning");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_detector", timeoutTicks = 200)
    public static void daylightDetectorsSeeTheLocalSun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        setLocalTime(level, pos, 6000);
        level.setBlockAndUpdate(pos, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        helper.succeedWhen(() -> {
            // The noon sun stands 86.5° up there, so nearly vanilla's 15.
            int power = level.getBlockState(pos).getValue(DaylightDetectorBlock.POWER);
            helper.assertTrue(power >= 14, "daylight detector at local noon gives " + power);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            release(helper, pos);
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_villager", timeoutTicks = 200)
    public static void villagersRestAtLocalNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        setLocalTime(level, pos, 18000);
        Villager villager = EntityType.VILLAGER.create(level);
        villager.setPersistenceRequired();
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getBrain().isActive(Activity.REST), "villager at local midnight is not resting: "
                + villager.getBrain().getActiveNonCoreActivity());
            villager.discard();
            release(helper, pos);
        });
    }

    /** Puts a bed at {@code pos} (foot) and east of it (head), and a mock player beside it. */
    private static ServerPlayer bedAndSleeper(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(pos, foot.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(pos.east(), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
        ServerPlayer player = TestPlayers.mock(helper);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() - 0.5, 0.0F, 0.0F);
        return player;
    }

    /** Sleeps deeply enough to skip the night: mock players are not ticked by a connection, so tick this one. */
    private static void sleepThrough(ServerPlayer player) {
        for (int i = 0; i < 101 && player.isSleeping(); i++) {
            player.doTick();
        }
    }

    private static void clearBed(GameTestHelper helper, BlockPos pos, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        level.getServer().getPlayerList().remove(player);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
        release(helper, pos);
    }

    /**
     * A bed works at local night (though it is day by the global clock), and sleeping through it wakes the sleeper at
     * the far side's first light.
     */
    @GameTest(template = TEMPLATE, batch = "localtime_sleep", timeoutTicks = 400)
    public static void sleepingSkipsToLocalMorning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.FAR_SIDE);
        ServerPlayer player = bedAndSleeper(helper, pos);

        setLocalTime(level, pos, 6000);
        Either<Player.BedSleepingProblem, Unit> byDay = player.startSleepInBed(pos.east());
        helper.assertTrue(byDay.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_NOW,
            "a bed at local noon should not work: " + byDay);

        setLocalTime(level, pos, 18000);
        helper.assertTrue(level.isDay(), "it should be day by the global clock");
        Either<Player.BedSleepingProblem, Unit> byNight = player.startSleepInBed(pos.east());
        helper.assertTrue(byNight.right().isPresent(), "a bed at local midnight should work: " + byNight);

        GameRules.IntegerValue percentage = level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int oldPercentage = percentage.get();
        percentage.set(0, level.getServer());
        long before = level.getDayTime();
        sleepThrough(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getDayTime() != before && !player.isSleeping(), "the night was not skipped");
            percentage.set(oldPercentage, level.getServer());
            long local = Math.floorMod(LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5), 24000L);
            helper.assertTrue(LocalSky.isDay(level, pos), "woke before local daylight, at local " + local);
            // Vanilla's sun is already 12° up at day time 0, so first light comes a little before local 0.
            helper.assertTrue(local < 3000L || local > 21000L, "woke at local " + local + ", not local morning");
            clearBed(helper, pos, player);
        });
    }

    // ---- The north pole ----

    /** At the pole the sun circles the horizon all day: never high enough for day, never far below it. */
    @GameTest(template = TEMPLATE, batch = "localtime_pole")
    public static void theSunCirclesTheHorizonAtThePole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.POLE);
        double lowest = 1.0, highest = -1.0;
        for (long t = 240000; t < 264000; t += 250) {
            level.setDayTime(t);
            level.updateSkyBrightness();
            LocalSky.Sample sun = LocalSky.sample(level, pos.getX() + 0.5, pos.getZ() + 0.5);
            lowest = Math.min(lowest, sun.sunY());
            highest = Math.max(highest, sun.sunY());
            helper.assertTrue(!LocalSky.isDay(level, pos), "day at the pole at " + t + ": sun height " + sun.sunY());
            helper.assertTrue(LocalSky.skyDarken(level, pos) == 5, "sky darkening at the pole " + LocalSky.skyDarken(level, pos));
        }
        helper.assertTrue(highest < 1e-3 && lowest > -1e-3, "the sun at the pole went from " + lowest + " to " + highest);
        release(helper, pos);
        helper.succeed();
    }

    /** A zombie at the pole does not burn at spawn's noon. */
    @GameTest(template = TEMPLATE, batch = "localtime_pole_burn", timeoutTicks = 300)
    public static void zombiesDoNotBurnAtThePole(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.POLE);
        level.setDayTime(24000L * 10 + 6000L);
        level.updateSkyBrightness();
        helper.assertTrue(level.isDay(), "it should be day by the global clock");
        Zombie zombie = zombie(level, pos);
        helper.runAfterDelay(200, () -> {
            boolean burning = zombie.isOnFire();
            zombie.discard();
            release(helper, pos);
            helper.assertTrue(!burning, "zombie at the pole is burning");
            helper.succeed();
        });
    }

    /**
     * A bed at the pole works at any hour (it is never day there), and sleeping skips to local noon, the sun's highest
     * at the pole (main's polar rule).
     */
    @GameTest(template = TEMPLATE, batch = "localtime_pole_sleep", timeoutTicks = 400)
    public static void sleepingAtThePoleSkipsToNoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, Site.POLE);
        ServerPlayer player = bedAndSleeper(helper, pos);
        level.setDayTime(24000L * 10 + 6000L);
        level.updateSkyBrightness();
        helper.assertTrue(level.isDay(), "it should be day by the global clock");
        Either<Player.BedSleepingProblem, Unit> atNoon = player.startSleepInBed(pos.east());
        helper.assertTrue(atNoon.right().isPresent(), "a bed at the pole should work at spawn's noon: " + atNoon);
        // Longitude turns fast this close to the pole: the night skip aims at where the sleeper lies.
        PlanetProjection.Position sleeper = LocalSky.position(level, player.getX(), player.getZ());

        GameRules.IntegerValue percentage = level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        int oldPercentage = percentage.get();
        percentage.set(0, level.getServer());
        long before = level.getDayTime();
        sleepThrough(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getDayTime() != before && !player.isSleeping(), "the night was not skipped");
            percentage.set(oldPercentage, level.getServer());
            // The only sleeper is the mean of the sleepers, so its clock lands on noon.
            long local = Math.floorMod(level.getDayTime() + Math.round(24000.0 * sleeper.longitude()), 24000L);
            helper.assertTrue(Math.abs(local - 6000L) <= 10L, "woke at the pole at local " + local + ", not noon");
            clearBed(helper, pos, player);
        });
    }
}
