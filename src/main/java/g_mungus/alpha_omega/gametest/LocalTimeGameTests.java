package g_mungus.alpha_omega.gametest;

import com.mojang.datafixers.util.Either;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.sky.LocalSky;
import g_mungus.alpha_omega.wrap.Wrap;
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
 * Gameplay follows the local sun. Each test works on a platform high above the date line (half a lap east of spawn),
 * where the local clock runs 12 hours apart from the prime meridian: global noon is local midnight. Each test sets
 * the global time, so each runs in its own batch.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class LocalTimeGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int PLATFORM_Y = 200;

    /** A spot near the date line on the equator, its chunk forced so that it ticks, standing on a stone platform. */
    private static BlockPos dateLine(GameTestHelper helper, int dx) {
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wrap.of(level);
        if (!wrap.enabled()) helper.fail("the overworld must wrap for local time tests");
        BlockPos pos = new BlockPos(wrap.canonBlock(wrap.period / 2 + 8 + dx), PLATFORM_Y, 8);
        ChunkPos chunk = new ChunkPos(pos);
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

    /** Set the global time so that the local clock at {@code pos} reads {@code localTicks}. */
    private static void setLocalTime(ServerLevel level, BlockPos pos, long localTicks) {
        long offset = LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5) - level.getDayTime();
        level.setDayTime(Math.floorMod(localTicks - offset, 24000L) + 24000L * 10);
    }

    @GameTest(template = TEMPLATE, batch = "localtime_brightness")
    public static void brightnessFollowsTheLocalSun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = dateLine(helper, 0);
        setLocalTime(level, pos, 18000);
        level.updateSkyBrightness();
        helper.assertTrue(level.isDay(), "it should be day at the prime meridian");
        helper.assertTrue(!LocalSky.isDay(level, pos), "it should be night at the date line");
        int local = level.getMaxLocalRawBrightness(pos);
        int global = level.getMaxLocalRawBrightness(pos, level.getSkyDarken());
        helper.assertTrue(local <= 4 && global >= 14, "brightness at local midnight " + local + ", by the global sun " + global);
        release(helper, pos);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "localtime_burn", timeoutTicks = 400)
    public static void zombiesBurnAtLocalNoon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = dateLine(helper, 0);
        setLocalTime(level, pos, 6000);
        level.updateSkyBrightness();
        helper.assertTrue(!level.isDay(), "it should be night at the prime meridian");
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        zombie.setNoAi(false);
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
        BlockPos pos = dateLine(helper, 0);
        setLocalTime(level, pos, 18000);
        level.updateSkyBrightness();
        helper.assertTrue(level.isDay(), "it should be day at the prime meridian");
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
        BlockPos pos = dateLine(helper, 0);
        setLocalTime(level, pos, 6000);
        level.updateSkyBrightness();
        level.setBlockAndUpdate(pos, Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        helper.succeedWhen(() -> {
            int power = level.getBlockState(pos).getValue(DaylightDetectorBlock.POWER);
            helper.assertTrue(power >= 13, "daylight detector at local noon gives " + power);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            release(helper, pos);
        });
    }

    @GameTest(template = TEMPLATE, batch = "localtime_villager", timeoutTicks = 200)
    public static void villagersRestAtLocalNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = dateLine(helper, 0);
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
     * A bed works at local night (though it is day at the prime meridian), and sleeping through it wakes the sleeper
     * at local morning: the global time lands half a day away from vanilla's.
     */
    @GameTest(template = TEMPLATE, batch = "localtime_sleep", timeoutTicks = 400)
    public static void sleepingSkipsToLocalMorning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = dateLine(helper, 0);
        BlockState foot = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(pos, foot.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(pos.east(), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
        ServerPlayer player = TestPlayers.mock(helper);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() - 0.5, 0.0F, 0.0F);

        setLocalTime(level, pos, 6000);
        level.updateSkyBrightness();
        Either<Player.BedSleepingProblem, Unit> byDay = player.startSleepInBed(pos.east());
        helper.assertTrue(byDay.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_NOW,
            "a bed at local noon should not work: " + byDay);

        setLocalTime(level, pos, 18000);
        level.updateSkyBrightness();
        helper.assertTrue(level.isDay(), "it should be day at the prime meridian");
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
            long local = LocalSky.localDayTime(level, pos.getX() + 0.5, pos.getZ() + 0.5);
            helper.assertTrue(Math.floorMod(local, 24000L) == 0L, "woke at local " + Math.floorMod(local, 24000L) + ", not local morning");
            helper.assertTrue(LocalSky.isDay(level, pos), "woke before local daylight");
            level.getServer().getPlayerList().remove(player);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
            release(helper, pos);
        });
    }
}
