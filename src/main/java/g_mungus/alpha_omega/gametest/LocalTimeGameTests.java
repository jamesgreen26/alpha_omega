package g_mungus.alpha_omega.gametest;

import com.mojang.datafixers.util.Either;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.cube.Cube;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.sky.LocalSky;
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
 * Gameplay follows the sun of the face below (design §7). The tests work on a platform high over DOWN, whose noon
 * comes 12 hours after UP's with the default (diagonal) sun axis: when DOWN has daylight the global clock, which is
 * UP's, says night, and the other way round. Each test sets the global time, so each runs in its own batch.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LocalTimeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int PLATFORM_Y = 200;

    /** A spot over DOWN, its chunk forced so that it ticks, standing on a stone platform. */
    private static BlockPos platform(GameTestHelper helper, int dz) {
        ServerLevel level = helper.getLevel();
        CubeGeometry geometry = Cube.of(level);
        if (geometry == null) helper.fail("the gametest overworld should be a cube world");
        if (geometry.settings.sunAxis() != g_mungus.alpha_omega.cube.CubeSettings.SunAxis.DIAGONAL) helper.fail("tests expect the diagonal sun axis");
        BlockPos pos = new BlockPos(geometry.centerX(CubeFace.DOWN) + 8, PLATFORM_Y, geometry.centerZ() + 8 + dz);
        ChunkPos chunk = new ChunkPos(pos);
        // Generate the neighbourhood now: gametest ticks run back to back, faster than chunks generate around a ticket.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz2 = -2; dz2 <= 2; dz2++) level.getChunk(chunk.x + dx, chunk.z + dz2);
        }
        level.setChunkForced(chunk.x, chunk.z, true);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
        }
        level.setWeatherParameters(24000, 0, false, false);
        return pos;
    }

    private static void release(GameTestHelper helper, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
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

    @GameTest(template = TEMPLATE, batch = "localtime_brightness")
    public static void brightnessFollowsTheLocalSun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, 0);
        setLocalTime(level, pos, 18000);
        helper.assertTrue(level.isDay(), "it should be day by the global clock (UP's)");
        helper.assertTrue(!LocalSky.isDay(level, pos), "it should be night over DOWN");
        int local = level.getMaxLocalRawBrightness(pos);
        int global = level.getMaxLocalRawBrightness(pos, level.getSkyDarken());
        helper.assertTrue(local <= 4 && global >= 14, "brightness at local midnight " + local + ", by the global sun " + global);
        release(helper, pos);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "localtime_burn", timeoutTicks = 400)
    public static void zombiesBurnAtLocalNoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, 0);
        setLocalTime(level, pos, 6000);
        helper.assertTrue(!level.isDay(), "it should be night by the global clock");
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isOnFire(), "zombie at local noon is not burning");
            zombie.discard();
            release(helper, pos);
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_noburn", timeoutTicks = 300)
    public static void zombiesDoNotBurnAtLocalMidnight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, 0);
        setLocalTime(level, pos, 18000);
        helper.assertTrue(level.isDay(), "it should be day by the global clock");
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(zombie);
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
        BlockPos pos = platform(helper, 0);
        setLocalTime(level, pos, 6000);
        level.setBlockAndUpdate(pos, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        helper.succeedWhen(() -> {
            // DOWN's noon sun stands at 54.7°, so a little under vanilla's 15.
            int power = level.getBlockState(pos).getValue(DaylightDetectorBlock.POWER);
            helper.assertTrue(power >= 12, "daylight detector at local noon gives " + power);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            release(helper, pos);
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_villager", timeoutTicks = 200)
    public static void villagersRestAtLocalNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, 0);
        setLocalTime(level, pos, 18000);
        Villager villager = EntityType.VILLAGER.create(level);
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(villager);
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getBrain().isActive(Activity.REST), "villager at local midnight is not resting: "
                + villager.getBrain().getActiveNonCoreActivity());
            villager.discard();
            release(helper, pos);
        });
    }

    /**
     * A bed works at local night (though it is day by the global clock), and sleeping through it wakes the sleeper at
     * DOWN's first light.
     */
    @GameTest(template = TEMPLATE, batch = "localtime_sleep", timeoutTicks = 400)
    public static void sleepingSkipsToLocalMorning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = platform(helper, 0);
        BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(pos, foot.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(pos.east(), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
        ServerPlayer player = TestPlayers.mock(helper);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() - 0.5, 0.0F, 0.0F);

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
        // Mock players are not ticked by a connection; tick this one until it sleeps deeply enough to skip the night.
        for (int i = 0; i < 101 && player.isSleeping(); i++) {
            player.doTick();
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getDayTime() != before && !player.isSleeping(), "the night was not skipped");
            percentage.set(oldPercentage, level.getServer());
            long local = Math.floorMod(LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5), 24000L);
            helper.assertTrue(LocalSky.isDay(level, pos), "woke before local daylight, at local " + local);
            // Vanilla's sun is already 12° up at day time 0, so first light comes a little before local 0.
            helper.assertTrue(local < 3000L || local > 21000L, "woke at local " + local + ", not local morning");
            level.getServer().getPlayerList().remove(player);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
            release(helper, pos);
        });
    }
}
