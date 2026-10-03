package g_mungus.alpha_omega.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.api.WorldWrap;
import g_mungus.alpha_omega.frame.Frames;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 6: debug commands, raids, and the public API. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class PolishGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void wrapCommandsRun(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        var dispatcher = level.getServer().getCommands().getDispatcher();
        helper.assertTrue(dispatcher.execute("wrap info", source) == Wrap.of(level).period, "/wrap info did not report the period");
        helper.assertTrue(dispatcher.execute("wrap islands", source) > 0, "/wrap islands found no islands");
        helper.assertTrue(dispatcher.execute("wrap check", source) == 1, "/wrap check found invariant violations");
        helper.succeed();
    }

    /** Raid centers are saved canonically and read in the frame of the raid's island. */
    @GameTest(template = TEMPLATE)
    public static void raidCentersAreCanonical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Wrap wrap = Wrap.of(level);
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        Raid raid = new Raid(9999, level, pos.offset(wrap.period, 0, -2 * wrap.period));
        helper.assertTrue(raid.getCenter().equals(Frames.lift(level, pos)), "raid center not in its island's frame: " + raid.getCenter());
        CompoundTag saved = raid.save(new CompoundTag());
        helper.assertTrue(saved.getInt("CX") == wrap.canonBlock(pos.getX()) && saved.getInt("CZ") == wrap.canonBlock(pos.getZ()), "raid center not saved canonically");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void apiMeasuresAcrossTheSeam(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int period = WorldWrap.period(level);
        helper.assertTrue(WorldWrap.isWrapped(level) && period > 0, "Overworld should wrap");
        Vec3 east = new Vec3(period - 1.0, 64, 10);
        Vec3 west = new Vec3(1.0, 64, 10);
        helper.assertTrue(Math.abs(WorldWrap.distanceSqr(level, east, west) - 4.0) < 1e-9, "distance across the seam should be 2");
        helper.assertTrue(WorldWrap.nearestImage(level, west, east).x == period + 1.0, "nearest image across the seam");
        helper.assertTrue(WorldWrap.canonical(level, new BlockPos(-1, 64, period)).equals(new BlockPos(period - 1, 64, 0)), "canonical position");
        helper.assertTrue(!WorldWrap.isWrapped(level.getServer().getLevel(Level.END)), "the End should not wrap");
        helper.succeed();
    }
}
