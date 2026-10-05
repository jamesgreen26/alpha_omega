package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import g_mungus.alpha_omega.worldgen.noise.InvariantOctaves;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Terrain is invariant under {@code Γ} (plan phase 9, {@code alpha-omega-best-wrapping-plan.md} §8): at points past each
 * kind of seam, the density and climate equal those at the point's image in the tile, and the gradient is flat at
 * the cone points. Also measures what remains at block level: a band chunk generated directly against the source it
 * is filled from, which shows the residue of aquifers and carvers (not blended; {@code rotated-seams.md} §6.2).
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    private static final int[] HEIGHTS = {-50, -20, 10, 40, 70, 100, 140};
    /** Point-level values agree to rounding. */
    private static final double TOLERANCE = 1e-6;

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    private static RandomState randomState(GameTestHelper helper) {
        return helper.getLevel().getChunkSource().randomState();
    }

    /** A kind of seam, with sample points (block corners) just past it, in the band. */
    private record Seam(String name, List<int[]> points) {
    }

    private static List<Seam> seams(OrbifoldGeometry g) {
        Random random = new Random(1234);
        int depth = 32;
        List<Seam> seams = new ArrayList<>();
        List<int[]> east = new ArrayList<>(), west = new ArrayList<>(), north = new ArrayList<>(), south = new ArrayList<>();
        List<int[]> f = new ArrayList<>(), n = new ArrayList<>(), e = new ArrayList<>(), w = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int d = random.nextInt(depth), d2 = random.nextInt(depth), side = random.nextInt(65) - 32;
            int z = g.northRow + 300 + random.nextInt(g.southRow - g.northRow - 600);
            east.add(new int[] {g.maxX + d, z});
            west.add(new int[] {g.minX - 1 - d, z});
            int x = (random.nextBoolean() ? 1 : -1) * (400 + random.nextInt(g.maxX - 800));
            north.add(new int[] {x, g.northRow - 1 - d});
            int xs = g.minX + random.nextInt(g.a);
            if (Math.abs(Math.abs(xs) - g.a / 4) < 300) xs += 600;
            south.add(new int[] {xs, g.southRow + d});
            f.add(random.nextBoolean() ? new int[] {g.maxX + d, g.northRow - 1 - d2} : new int[] {g.minX - 1 - d, g.northRow - 1 - d2});
            n.add(new int[] {side, g.northRow - 1 - d});
            e.add(new int[] {g.a / 4 + side, g.southRow + d});
            w.add(new int[] {-g.a / 4 + side, g.southRow + d});
        }
        seams.add(new Seam("east-west (east side)", east));
        seams.add(new Seam("east-west (west side)", west));
        seams.add(new Seam("north fold", north));
        seams.add(new Seam("south fold", south));
        seams.add(new Seam("near F", f));
        seams.add(new Seam("near N", n));
        seams.add(new Seam("near E", e));
        seams.add(new Seam("near W", w));
        return seams;
    }

    /** {@code |f(p) − f(g p)|} over a seam's points and heights: {mean, max}, and the mean step to a neighbouring block for scale. */
    private static double[] step(OrbifoldGeometry g, Seam seam, ToDoubleFunction<int[]> f) {
        double sum = 0, max = 0, neighbour = 0;
        int count = 0;
        for (int[] p : seam.points()) {
            Motion frame = g.frame(p[0], p[1]);
            int ix = (int) frame.pointX(p[0]), iz = (int) frame.pointZ(p[1]);
            for (int y : HEIGHTS) {
                double here = f.applyAsDouble(new int[] {p[0], y, p[1]});
                double step = Math.abs(here - f.applyAsDouble(new int[] {ix, y, iz}));
                sum += step;
                max = Math.max(max, step);
                neighbour += Math.abs(here - f.applyAsDouble(new int[] {p[0] + 1, y, p[1]}));
                count++;
            }
        }
        return new double[] {sum / count, max, neighbour / count};
    }

    private static double compute(DensityFunction function, int[] p) {
        return function.compute(new DensityFunction.SinglePointContext(p[0], p[1], p[2]));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void densityMatchesAcrossSeams(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        NoiseRouter router = randomState(helper).router();
        List<String> report = new ArrayList<>(), wrong = new ArrayList<>();
        report.add("Orbifold noise octaves:\n" + InvariantOctaves.summary());
        report.add(String.format(Locale.ROOT, "%-24s %12s %12s %14s", "final density", "mean step", "max step", "(neighbour)"));
        for (Seam seam : seams(g)) {
            double[] s = step(g, seam, p -> compute(router.finalDensity(), p));
            report.add(String.format(Locale.ROOT, "%-24s %12.3e %12.3e %14.3e", seam.name(), s[0], s[1], s[2]));
            if (s[1] > TOLERANCE) wrong.add(seam.name() + " " + s[1]);
        }
        AlphaOmegaMod.LOGGER.info("Terrain density across seams:\n{}", String.join("\n", report));
        helper.assertTrue(wrong.isEmpty(), "density steps across seams: " + wrong);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void climateMatchesAcrossSeams(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        NoiseRouter router = randomState(helper).router();
        DensityFunction[] climate = {router.temperature(), router.vegetation(), router.continents(), router.erosion(), router.depth(), router.ridges()};
        String[] names = {"temperature", "vegetation", "continents", "erosion", "depth", "ridges"};
        List<String> report = new ArrayList<>(), wrong = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-24s %-12s %12s %12s %14s", "climate", "parameter", "mean step", "max step", "(neighbour)"));
        for (Seam seam : seams(g)) {
            for (int i = 0; i < climate.length; i++) {
                DensityFunction function = climate[i];
                double[] s = step(g, seam, p -> compute(function, p));
                report.add(String.format(Locale.ROOT, "%-24s %-12s %12.3e %12.3e %14.3e", seam.name(), names[i], s[0], s[1], s[2]));
                if (s[1] > TOLERANCE) wrong.add(seam.name() + " " + names[i] + " " + s[1]);
            }
        }
        AlphaOmegaMod.LOGGER.info("Climate across seams:\n{}", String.join("\n", report));
        helper.assertTrue(wrong.isEmpty(), "climate steps across seams: " + wrong);
        helper.succeed();
    }

    /** At each cone point the density is even, so its gradient is flat: a step either way, along x or z, gives the same value. */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void gradientIsFlatAtConePoints(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        NoiseRouter router = randomState(helper).router();
        List<String> wrong = new ArrayList<>();
        for (OrbifoldGeometry.ConePoint c : g.conePoints()) {
            double worst = 0;
            for (int y : HEIGHTS) {
                for (int h = 1; h <= 8; h++) {
                    worst = Math.max(worst, Math.abs(compute(router.finalDensity(), new int[] {c.x() + h, y, c.z()})
                        - compute(router.finalDensity(), new int[] {c.x() - h, y, c.z()})));
                    worst = Math.max(worst, Math.abs(compute(router.finalDensity(), new int[] {c.x(), y, c.z() + h})
                        - compute(router.finalDensity(), new int[] {c.x(), y, c.z() - h})));
                    worst = Math.max(worst, Math.abs(compute(router.finalDensity(), new int[] {c.x() + h, y, c.z() + h})
                        - compute(router.finalDensity(), new int[] {c.x() - h, y, c.z() - h})));
                }
            }
            AlphaOmegaMod.LOGGER.info("Density asymmetry at cone point {}: {}", c.name(), worst);
            if (worst > TOLERANCE) wrong.add(c.name() + " " + worst);
        }
        helper.assertTrue(wrong.isEmpty(), "density not even about cone points: " + wrong);
        helper.succeed();
    }

    /** Every noise biome (one per quart, sampled at its centre) past each seam is its source quart's biome. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void biomesMatchAcrossSeams(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        BiomeSource biomes = helper.getLevel().getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = randomState(helper).sampler();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Seam seam : seams(g)) {
            for (int[] p : seam.points()) {
                int qx = p[0] >> 2, qz = p[1] >> 2;
                Motion frame = g.frame(qx << 2, qz << 2);
                int sx = (int) Math.floor(frame.pointX((qx << 2) + 2) / 4.0), sz = (int) Math.floor(frame.pointZ((qz << 2) + 2) / 4.0);
                for (int qy = -12; qy <= 40; qy += 4) {
                    Holder<Biome> here = biomes.getNoiseBiome(qx, qy, qz, sampler), there = biomes.getNoiseBiome(sx, qy, sz, sampler);
                    checked++;
                    if (!here.equals(there) && wrong.size() < 6) wrong.add(seam.name() + " quart " + qx + "," + qy + "," + qz + ": " + here.getRegisteredName()
                        + " vs " + there.getRegisteredName());
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Biomes across seams: {} quarts checked, {} differ", checked, wrong.size());
        helper.assertTrue(wrong.isEmpty(), "biomes differ across seams: " + wrong);
        helper.succeed();
    }

    /**
     * The residue at block level: for a band chunk past each kind of seam, the noise-stage blocks (terrain, water, lava)
     * it would get if generated, against its source's at the same cells. Logged as a table; the density part (solid or
     * not) must be within {@link #MAX_SOLID_RESIDUE} of the cells, the rest is aquifer residue.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 2400)
    public static void blockResidueAcrossSeams(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        OrbifoldChunkGenerator generator = (OrbifoldChunkGenerator) level.getChunkSource().getGenerator();
        int north = (g.northRow >> 4) - 1, south = g.southRow >> 4, east = g.maxX >> 4, west = (g.minX >> 4) - 1, middle = g.spawnZ >> 4;
        Object[][] chunks = {
            {"east-west (east side)", new ChunkPos(east, middle)}, {"east-west (west side)", new ChunkPos(west, middle)},
            {"east-west (east side)", new ChunkPos(east, middle - 150)}, {"east-west (west side)", new ChunkPos(west, middle + 50)},
            {"north fold", new ChunkPos(100, north)}, {"north fold", new ChunkPos(-200, north)}, {"north fold", new ChunkPos(330, north)},
            {"south fold", new ChunkPos(-60, south)}, {"south fold", new ChunkPos(150, south)}, {"south fold", new ChunkPos(-330, south)},
            {"near F", new ChunkPos(east, north)}, {"near N", new ChunkPos(0, north)}, {"near N (west)", new ChunkPos(-1, north)},
            {"near E", new ChunkPos(g.a / 64, south)}, {"near W", new ChunkPos(-g.a / 64 - 1, south)}};
        List<String> report = new ArrayList<>(), wrong = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-24s %-12s %-12s %10s %12s %16s %12s", "blocks", "band chunk", "source", "cells", "solid diff", "(above sea)", "fluid diff"));
        for (Object[] entry : chunks) {
            String name = (String) entry[0];
            ChunkPos band = (ChunkPos) entry[1];
            OrbifoldGeometry.Cell source = g.canonChunk(band.x, band.z);
            Motion frame = source.frame();
            ChunkAccess here = fill(level, generator, band), there = fill(level, generator, new ChunkPos(source.x(), source.z()));
            int cells = 0, solid = 0, fluid = 0, solidHigh = 0;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = band.getMinBlockX() + dx, z = band.getMinBlockZ() + dz;
                    int sx = frame.cellX(x) & 15, sz = frame.cellZ(z) & 15;
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        BlockState a = here.getBlockState(new net.minecraft.core.BlockPos(dx, y, dz)), b = there.getBlockState(new net.minecraft.core.BlockPos(sx, y, sz));
                        cells++;
                        boolean solidA = a.getFluidState().isEmpty() && !a.isAir(), solidB = b.getFluidState().isEmpty() && !b.isAir();
                        if (solidA != solidB) {
                            solid++;
                            if (y > generator.getSeaLevel()) solidHigh++;
                        }
                        else if (!solidA && !a.equals(b)) fluid++;
                    }
                }
            }
            report.add(String.format(Locale.ROOT, "%-24s %-12s %-12s %10d %11.4f%% %15.4f%% %11.4f%%", name, band.x + "," + band.z, source.x() + "," + source.z(),
                cells, 100.0 * solid / cells, 100.0 * solidHigh / cells, 100.0 * fluid / cells));
            if ((double) solid / cells > MAX_SOLID_RESIDUE) wrong.add(name + " " + solid + "/" + cells);
        }
        AlphaOmegaMod.LOGGER.info("Noise-stage block residue across seams:\n{}", String.join("\n", report));
        helper.assertTrue(wrong.isEmpty(), "solid/open blocks differ across seams: " + wrong);
        helper.succeed();
    }

    /** The share of cells whose solidity may differ between a band chunk and its source (aquifer barriers, beards). */
    private static final double MAX_SOLID_RESIDUE = 0.01;

    /** A chunk's noise stage, generated into a scratch chunk outside the level. */
    private static ChunkAccess fill(ServerLevel level, OrbifoldChunkGenerator generator, ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        java.util.concurrent.CompletableFuture<ChunkAccess> filled =
            generator.fillAnyFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), chunk);
        // The fill looks up structure references through the chunk source, which runs on this thread: keep it going.
        while (!filled.isDone()) {
            if (!level.getChunkSource().pollTask()) java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        }
        return filled.join();
    }
}
