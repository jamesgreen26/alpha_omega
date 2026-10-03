package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.wrap.Wrap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Milestone 6: each dimension wraps at its own period; the Nether at an eighth of the Overworld's. */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class DimensionGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    @GameTest(template = TEMPLATE)
    public static void dimensionsHaveTheirOwnPeriods(GameTestHelper helper) {
        Wrap overworld = Wrap.of(Level.OVERWORLD);
        Wrap nether = Wrap.of(Level.NETHER);
        helper.assertTrue(overworld.enabled() && nether.period * 8 == overworld.period, "Nether is not an eighth of the Overworld: " + nether + " vs " + overworld);
        helper.assertTrue(!Wrap.of(Level.END).enabled(), "the End should not wrap by default");
        helper.succeed();
    }

    /** Blocks in the Nether repeat every Nether period, not every Overworld period. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void netherStorageIsPeriodic(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        int period = Wrap.of(nether).period;
        BlockPos pos = new BlockPos(period / 3, 125, period / 5);
        nether.setBlockAndUpdate(pos.offset(period, 0, -2 * period), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(nether.getBlockState(pos).is(Blocks.GOLD_BLOCK), "Nether block not visible one Nether period away");
        nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** The Nether's terrain noise tiles at the Nether's period. */
    @GameTest(template = TEMPLATE)
    public static void netherNoiseIsPeriodic(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        int period = Wrap.of(nether).period;
        NoiseRouter router = nether.getChunkSource().randomState().router();
        RandomSource random = RandomSource.create(5L);
        for (int sample = 0; sample < 200; sample++) {
            int x = random.nextInt(period);
            int y = random.nextIntBetweenInclusive(0, 127);
            int z = random.nextInt(period);
            for (DensityFunction function : new DensityFunction[] {router.finalDensity(), router.temperature(), router.vegetation()}) {
                double base = function.compute(new DensityFunction.SinglePointContext(x, y, z));
                double image = function.compute(new DensityFunction.SinglePointContext(x + period, y, z - 3 * period));
                if (Math.abs(base - image) > 1e-6 * Math.max(1.0, Math.abs(base))) {
                    helper.fail("Nether noise differs between images at " + x + ", " + y + ", " + z + ": " + base + " vs " + image);
                }
            }
        }
        helper.succeed();
    }
}
