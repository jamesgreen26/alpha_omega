package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.wrap.Wrap;
import g_mungus.alpha_omega.wrap.Wraps;
import java.util.List;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Periodic worldgen (design doc §12): generation is a function of position that repeats every {@code period()}, so the
 * column at {@code x = 0} continues the column at {@code x = period() - 1} with no seam.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldgenGameTests {

    private static Wrap wrap() {
        return Wraps.overworld();
    }

    private static int period() {
        return wrap().period;
    }

    private static int chunks() {
        return wrap().chunkPeriod;
    }

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final double TOLERANCE = 1e-6;

    @GameTest(template = TEMPLATE)
    public static void noiseRouterIsPeriodic(GameTestHelper helper) {
        for (ResourceKey<NoiseGeneratorSettings> settings : SETTINGS) {
            assertRouterPeriodic(helper, settings.location().getPath(), randomState(helper.getLevel(), settings).router());
        }
        helper.succeed();
    }

    private static void assertRouterPeriodic(GameTestHelper helper, String settings, NoiseRouter router) {
        Map<String, DensityFunction> functions = Map.ofEntries(
            Map.entry("barrier", router.barrierNoise()),
            Map.entry("fluidLevelFloodedness", router.fluidLevelFloodednessNoise()),
            Map.entry("fluidLevelSpread", router.fluidLevelSpreadNoise()),
            Map.entry("lava", router.lavaNoise()),
            Map.entry("temperature", router.temperature()),
            Map.entry("vegetation", router.vegetation()),
            Map.entry("continents", router.continents()),
            Map.entry("erosion", router.erosion()),
            Map.entry("depth", router.depth()),
            Map.entry("ridges", router.ridges()),
            Map.entry("initialDensityWithoutJaggedness", router.initialDensityWithoutJaggedness()),
            Map.entry("finalDensity", router.finalDensity()),
            Map.entry("veinToggle", router.veinToggle()),
            Map.entry("veinRidged", router.veinRidged()),
            Map.entry("veinGap", router.veinGap()));

        RandomSource random = RandomSource.create(42L);
        for (int sample = 0; sample < 400; sample++) {
            int x = sample < 50 ? period() - 25 + sample : random.nextInt(period());
            int y = random.nextIntBetweenInclusive(-64, 256);
            int z = random.nextInt(period());
            for (Map.Entry<String, DensityFunction> entry : functions.entrySet()) {
                double base = entry.getValue().compute(new DensityFunction.SinglePointContext(x, y, z));
                assertImage(helper, settings + "/" + entry.getKey(), x, z, base, entry.getValue().compute(new DensityFunction.SinglePointContext(x + period(), y, z)));
                assertImage(helper, settings + "/" + entry.getKey(), x, z, base, entry.getValue().compute(new DensityFunction.SinglePointContext(x, y, z - period())));
                assertImage(helper, settings + "/" + entry.getKey(), x, z, base, entry.getValue().compute(new DensityFunction.SinglePointContext(x - 2 * period(), y, z + 3 * period())));
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void climateIsPeriodic(GameTestHelper helper) {
        Climate.Sampler sampler = randomState(helper.getLevel(), NoiseGeneratorSettings.OVERWORLD).sampler();
        int quarts = QuartPos.fromBlock(period());
        RandomSource random = RandomSource.create(7L);
        for (int sample = 0; sample < 200; sample++) {
            int x = random.nextInt(quarts);
            int y = random.nextIntBetweenInclusive(-16, 64);
            int z = random.nextInt(quarts);
            Climate.TargetPoint base = sampler.sample(x, y, z);
            helper.assertTrue(base.equals(sampler.sample(x + quarts, y, z - quarts)), "climate differs between images at quart " + x + ", " + z);
        }
        helper.succeed();
    }

    /** Whole generated columns, including aquifers, ore veins and the 3D base noise. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void generatedColumnsArePeriodic(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NoiseBasedChunkGenerator generator = overworldGenerator(level);
        RandomState state = randomState(level, NoiseGeneratorSettings.OVERWORLD);
        RandomSource random = RandomSource.create(11L);
        for (int sample = 0; sample < 24; sample++) {
            int x = sample < 6 ? period() - 3 + sample : random.nextInt(period());
            int z = random.nextInt(period());
            int height = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state);
            int imageHeight = generator.getBaseHeight(x + period(), z - period(), Heightmap.Types.OCEAN_FLOOR_WG, level, state);
            helper.assertTrue(height == imageHeight, "height differs between images at " + x + ", " + z + ": " + height + " vs " + imageHeight);

            NoiseColumn column = generator.getBaseColumn(x, z, level, state);
            NoiseColumn image = generator.getBaseColumn(x - period(), z + 2 * period(), level, state);
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                if (column.getBlock(y) != image.getBlock(y)) {
                    helper.fail("block differs between images at " + x + ", " + y + ", " + z + ": " + column.getBlock(y) + " vs " + image.getBlock(y));
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void structureGridsTileTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = level.getSeed();
        // Spacing must divide every wrapped dimension's period (the Nether's is the smallest); placements repeat
        // with the Overworld's.
        int grid = Wraps.structureGridPeriod();
        int n = chunks();
        for (StructureSet set : level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            if (set.placement() instanceof RandomSpreadStructurePlacement placement) {
                helper.assertTrue(grid % placement.spacing() == 0, "spacing " + placement.spacing() + " does not divide " + grid);
                helper.assertTrue(placement.separation() < placement.spacing(), "separation not below spacing");
                for (int x = -n; x < 2 * n; x += 7) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(seed, x, 5);
                    ChunkPos image = placement.getPotentialStructureChunk(seed, x + n, 5 - n);
                    helper.assertTrue(image.x == chunk.x + n && image.z == chunk.z - n, "structure grid is not periodic at chunk " + x);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void strongholdsAreCanonical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structureState = level.getChunkSource().getGeneratorState();
        for (StructureSet set : level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            if (set.placement() instanceof ConcentricRingsStructurePlacement placement) {
                helper.succeedWhen(() -> {
                    List<ChunkPos> positions = structureState.getRingPositionsFor(placement);
                    helper.assertTrue(positions != null, "ring positions not ready");
                    helper.assertTrue(!positions.isEmpty() && positions.size() <= placement.spread(), "unexpected ring count " + positions.size());
                    for (ChunkPos pos : positions) {
                        helper.assertTrue(wrap().canon(pos) == pos, "ring position not canonical: " + pos);
                    }
                });
                return;
            }
        }
        helper.fail("no concentric ring structure set");
    }

    /** Noise settings with periodic generation; the End's island noise is simplex and not yet periodic. */
    private static final List<ResourceKey<NoiseGeneratorSettings>> SETTINGS = List.of(
        NoiseGeneratorSettings.OVERWORLD, NoiseGeneratorSettings.LARGE_BIOMES, NoiseGeneratorSettings.AMPLIFIED,
        NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.CAVES, NoiseGeneratorSettings.FLOATING_ISLANDS);

    /**
     * The gametest world is superflat, so its own random state has a dummy router. Build real ones from the
     * registered noise settings instead.
     */
    private static RandomState randomState(ServerLevel level, ResourceKey<NoiseGeneratorSettings> settings) {
        RegistryAccess registries = level.registryAccess();
        return RandomState.create(registries.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settings),
            registries.lookupOrThrow(Registries.NOISE), level.getSeed());
    }

    private static NoiseBasedChunkGenerator overworldGenerator(ServerLevel level) {
        RegistryAccess registries = level.registryAccess();
        return new NoiseBasedChunkGenerator(
            MultiNoiseBiomeSource.createFromPreset(registries.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
            registries.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
    }

    private static void assertImage(GameTestHelper helper, String name, int x, int z, double expected, double actual) {
        if (Math.abs(expected - actual) > TOLERANCE * Math.max(1.0, Math.abs(expected))) {
            helper.fail(name + " differs between images at " + x + ", " + z + ": " + expected + " vs " + actual);
        }
    }
}
