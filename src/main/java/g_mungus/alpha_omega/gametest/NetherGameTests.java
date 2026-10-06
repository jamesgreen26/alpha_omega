package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.band.Band;
import g_mungus.alpha_omega.band.BandCheck;
import g_mungus.alpha_omega.orbifold.Motion;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.Transform;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Nether of an orbifold world ({@code orbifold-implementation.md} phase 10): the overworld's layout at 1:8 with its
 * own band; void outside, an empty band before fill, invariant terrain and biomes across every kind of seam, band copies,
 * transfers, and portals that link from a portal's canonical copy, so every copy of a portal links the same way.
 *
 * <p>Sites are placed from the Nether's own fold rows and edges, so they hold at every size (the small size's Nether tile
 * is 448 × 192). In the Nether: lava on the east seam at {@code zN + 24}, y 140 (above the roof); transfers on the east
 * seam at the tile's middle row and on the north fold at {@code x = −a/8}, y 200; the Nether portal on the north fold at
 * {@code x = −a/4}, y 150. In the overworld, portals at y 150: on the east seam at {@code zN + 1300} and on the north
 * fold at {@code x = −600}, each well over 128 blocks (16 in the Nether) from every other portal the suite builds.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class NetherGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";
    /** Heights sampled for noise: the Nether is 128 blocks of terrain under a bedrock roof. */
    private static final int[] HEIGHTS = {2, 20, 40, 64, 90, 110, 126};
    private static final double TOLERANCE = 1e-6;
    /** Height of the open-air tests, above the Nether's roof. */
    private static final double HEIGHT = 200.0;

    private static ServerLevel nether(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) helper.fail("no Nether");
        return nether;
    }

    private static OrbifoldGeometry geometry(GameTestHelper helper, ServerLevel level) {
        OrbifoldGeometry geometry = Orbifold.of(level);
        if (geometry == null) helper.fail(level.dimension().location() + " should be an orbifold");
        return geometry;
    }

    private static OrbifoldChunkGenerator generator(ServerLevel level) {
        return (OrbifoldChunkGenerator) level.getChunkSource().getGenerator();
    }

    // ---- The geometry ----

    /** The Nether is the overworld's size at 1:8, with the same band in chunks; the End is no orbifold. */
    @GameTest(template = TEMPLATE)
    public static void netherIsTheOverworldAtOneEighth(GameTestHelper helper) {
        OrbifoldGeometry overworld = geometry(helper, helper.getLevel()), nether = geometry(helper, nether(helper));
        helper.assertTrue(nether.scale == OrbifoldGeometry.NETHER_SCALE && nether.size.equals(overworld.size), "Nether geometry " + nether);
        helper.assertTrue(8 * nether.a == overworld.a && 8 * nether.b == overworld.b && 8 * nether.northRow == overworld.northRow
            && 8 * nether.southRow == overworld.southRow, "Nether " + nether + " is not " + overworld + " at 1:8");
        helper.assertTrue(nether.bandChunks == overworld.bandChunks, "Nether band " + nether.bandChunks + " chunks, overworld " + overworld.bandChunks);
        for (OrbifoldGeometry.ConePoint cone : nether.conePoints()) {
            helper.assertTrue(cone.x() % 16 == 0 && cone.z() % 16 == 0, "cone point " + cone + " is not chunk-aligned");
        }
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end == null || Orbifold.of(end) == null, "the End should stay vanilla");
        helper.assertTrue(end == null || !(end.getChunkSource().getGenerator() instanceof OrbifoldChunkGenerator), "the End's generator should be vanilla");
        helper.succeed();
    }

    // ---- Generation ----

    private static boolean allAir(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) return false;
        }
        return true;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherVoidOutsideFootprint(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        int[] bounds = g.footprintChunks();
        int middle = (g.northRow + g.southRow) / 2 >> 4;
        List<ChunkPos> outside = List.of(new ChunkPos(bounds[2] + 1, middle), new ChunkPos(bounds[0] - 1, middle),
            new ChunkPos(0, bounds[1] - 1), new ChunkPos(0, bounds[3] + 1), new ChunkPos(bounds[2] + 3, bounds[3] + 3));
        for (ChunkPos pos : outside) {
            helper.assertTrue(generator(level).region(pos) == OrbifoldChunkGenerator.Region.VOID, pos + " should be outside the footprint");
            ChunkAccess chunk = level.getChunk(pos.x, pos.z);
            helper.assertTrue(allAir(chunk), "void chunk " + pos + " has blocks (the Nether's roof and floor stop at the footprint)");
            helper.assertTrue(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 7, 7) < level.getMinBuildHeight(), "void chunk " + pos + " has a surface");
        }
        helper.succeed();
    }

    /** The first band chunk past each seam generates empty (no roof, no floor) before its fill; the tile chunk beside it has terrain. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherBandGeneratesEmpty(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        OrbifoldChunkGenerator generator = generator(level);
        int middle = (g.northRow + g.southRow) / 2 >> 4, x = g.a / 16 >> 4;
        Object[][] chunks = {
            {"east", new ChunkPos(g.maxX >> 4, middle), new ChunkPos((g.maxX >> 4) - 1, middle)},
            {"west", new ChunkPos((g.minX >> 4) - 1, middle), new ChunkPos(g.minX >> 4, middle)},
            {"north", new ChunkPos(x, (g.northRow >> 4) - 1), new ChunkPos(x, g.northRow >> 4)},
            {"south", new ChunkPos(x, g.southRow >> 4), new ChunkPos(x, (g.southRow >> 4) - 1)},
            {"north at N", new ChunkPos(0, (g.northRow >> 4) - 1), new ChunkPos(0, g.northRow >> 4)}};
        for (Object[] entry : chunks) {
            String name = (String) entry[0];
            ChunkPos band = (ChunkPos) entry[1], tile = (ChunkPos) entry[2];
            helper.assertTrue(generator.awaitsFill(band), name + ": " + band + " should await a fill, is " + generator.region(band));
            ChunkAccess scratch = new ProtoChunk(band, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
            generator.fillFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), scratch).join();
            helper.assertTrue(allAir(scratch), name + ": the noise fill of band chunk " + band + " has blocks");
            ChunkAccess chunk = level.getChunkSource().getChunk(band.x, band.z, ChunkStatus.FEATURES, true);
            if (chunk.getPersistedStatus() != ChunkStatus.FULL) {
                helper.assertTrue(allAir(chunk), name + ": band chunk " + band + " (" + chunk.getPersistedStatus() + ") has blocks after generation");
            }
            ChunkAccess beside = level.getChunkSource().getChunk(tile.x, tile.z, ChunkStatus.FEATURES, true);
            helper.assertTrue(!allAir(beside), name + ": tile chunk " + tile + " has no terrain");
            helper.assertTrue(beside.getBlockState(new BlockPos(tile.getMinBlockX() + 3, 127, tile.getMinBlockZ() + 3)).is(Blocks.BEDROCK),
                name + ": tile chunk " + tile + " has no bedrock roof");
        }
        helper.succeed();
    }

    /** Fortresses and bastions near every Nether seam lie inside the tile with the margin. */
    @GameTest(template = TEMPLATE, timeoutTicks = 2400)
    public static void netherStructuresStayInTheTile(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        OrbifoldChunkGenerator generator = generator(level);
        int[] f = g.footprintChunks();
        int starts = 0;
        List<String> crossing = new ArrayList<>();
        // Every footprint chunk at the smaller sizes; a ring along the seams at the larger.
        for (int cx = f[0]; cx <= f[2]; cx++) {
            for (int cz = f[1]; cz <= f[3]; cz++) {
                if (g.chunkDepth(cx, cz) == 0 && cx > (g.minX >> 4) + 4 && cx < (g.maxX >> 4) - 4 && cz > (g.northRow >> 4) + 4 && cz < (g.southRow >> 4) - 4) continue;
                ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, true);
                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (!start.isValid()) continue;
                    starts++;
                    BoundingBox box = start.getBoundingBox();
                    if (!generator.fitsInTile(box) && crossing.size() < 5) crossing.add(cx + "," + cz + ": " + box);
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Nether structure filter: {} starts kept near the seams", starts);
        helper.assertTrue(crossing.isEmpty(), "Nether structures reach past the tile: " + crossing);
        helper.succeed();
    }

    // ---- Seam invariance ----

    /** A kind of seam, with sample points (block corners) just past it, in the band. */
    private record Seam(String name, List<int[]> points) {
    }

    private static List<Seam> seams(OrbifoldGeometry g) {
        Random random = new Random(8);
        int depth = 32, tall = g.southRow - g.northRow;
        List<int[]> east = new ArrayList<>(), west = new ArrayList<>(), north = new ArrayList<>(), south = new ArrayList<>();
        List<int[]> f = new ArrayList<>(), n = new ArrayList<>(), e = new ArrayList<>(), w = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int d = random.nextInt(depth), d2 = random.nextInt(depth), side = random.nextInt(65) - 32;
            int z = g.northRow + 8 + random.nextInt(tall - 16);
            east.add(new int[] {g.maxX + d, z});
            west.add(new int[] {g.minX - 1 - d, z});
            north.add(new int[] {g.minX + random.nextInt(g.a), g.northRow - 1 - d});
            south.add(new int[] {g.minX + random.nextInt(g.a), g.southRow + d});
            f.add(random.nextBoolean() ? new int[] {g.maxX + d, g.northRow - 1 - d2} : new int[] {g.minX - 1 - d, g.northRow - 1 - d2});
            n.add(new int[] {side, g.northRow - 1 - d});
            e.add(new int[] {g.a / 4 + side, g.southRow + d});
            w.add(new int[] {-g.a / 4 + side, g.southRow + d});
        }
        return List.of(new Seam("east-west (east side)", east), new Seam("east-west (west side)", west), new Seam("north fold", north),
            new Seam("south fold", south), new Seam("near F", f), new Seam("near N", n), new Seam("near E", e), new Seam("near W", w));
    }

    /** {@code |f(p) − f(g p)|} over a seam's points and heights: {mean, max, mean step to a neighbouring block}. */
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

    /** The Nether's final density (its 3D noise included) and its climate are equal at every point past a seam and at its source. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherNoiseMatchesAcrossSeams(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        RandomState random = level.getChunkSource().randomState();
        NoiseRouter router = random.router();
        DensityFunction[] functions = {router.finalDensity(), router.temperature(), router.vegetation()};
        String[] names = {"final density", "temperature", "vegetation"};
        List<String> report = new ArrayList<>(), wrong = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-24s %-14s %12s %12s %14s", "Nether " + g.size.id(), "function", "mean step", "max step", "(neighbour)"));
        for (Seam seam : seams(g)) {
            for (int i = 0; i < functions.length; i++) {
                DensityFunction function = functions[i];
                double[] s = step(g, seam, p -> compute(function, p));
                report.add(String.format(Locale.ROOT, "%-24s %-14s %12.3e %12.3e %14.3e", seam.name(), names[i], s[0], s[1], s[2]));
                if (s[1] > TOLERANCE) wrong.add(seam.name() + " " + names[i] + " " + s[1]);
            }
        }
        AlphaOmegaMod.LOGGER.info("Nether noise across seams:\n{}", String.join("\n", report));
        helper.assertTrue(wrong.isEmpty(), "Nether noise steps across seams: " + wrong);
        helper.succeed();
    }

    /** At each cone point the Nether's density is even about it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void netherGradientIsFlatAtConePoints(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        DensityFunction density = level.getChunkSource().randomState().router().finalDensity();
        List<String> wrong = new ArrayList<>();
        for (OrbifoldGeometry.ConePoint c : g.conePoints()) {
            double worst = 0;
            for (int y : HEIGHTS) {
                for (int h = 1; h <= 8; h++) {
                    worst = Math.max(worst, Math.abs(compute(density, new int[] {c.x() + h, y, c.z()}) - compute(density, new int[] {c.x() - h, y, c.z()})));
                    worst = Math.max(worst, Math.abs(compute(density, new int[] {c.x(), y, c.z() + h}) - compute(density, new int[] {c.x(), y, c.z() - h})));
                }
            }
            if (worst > TOLERANCE) wrong.add(c.name() + " " + worst);
        }
        helper.assertTrue(wrong.isEmpty(), "Nether density not even about cone points: " + wrong);
        helper.succeed();
    }

    /** Every Nether noise biome past each seam is its source quart's. */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherBiomesMatchAcrossSeams(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        List<String> wrong = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int checked = 0, differ = 0;
        for (Seam seam : seams(g)) {
            for (int[] p : seam.points()) {
                int qx = p[0] >> 2, qz = p[1] >> 2;
                Motion frame = g.frame(qx << 2, qz << 2);
                int sx = (int) Math.floor(frame.pointX((qx << 2) + 2) / 4.0), sz = (int) Math.floor(frame.pointZ((qz << 2) + 2) / 4.0);
                for (int qy = 0; qy < 32; qy += 4) {
                    Holder<Biome> here = biomes.getNoiseBiome(qx, qy, qz, sampler), there = biomes.getNoiseBiome(sx, qy, sz, sampler);
                    checked++;
                    seen.add(here.getRegisteredName());
                    if (!here.equals(there)) {
                        differ++;
                        if (wrong.size() < 6) wrong.add(seam.name() + " quart " + qx + "," + qy + "," + qz + ": " + here.getRegisteredName() + " vs " + there.getRegisteredName());
                    }
                }
            }
        }
        AlphaOmegaMod.LOGGER.info("Nether biomes across seams: {} quarts checked, {} differ; biomes seen {}", checked, differ, seen);
        helper.assertTrue(wrong.isEmpty(), "Nether biomes differ across seams: " + wrong);
        helper.succeed();
    }

    /**
     * The residue at block level: a Nether band chunk past each kind of seam, generated as if it were tile, against its
     * source at the same cells. The Nether has no aquifers (its lava sea is a plain fluid level), so solid and open
     * cells must agree within {@link #MAX_SOLID_RESIDUE}.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 2400)
    public static void netherBlockResidueAcrossSeams(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        OrbifoldChunkGenerator generator = generator(level);
        int north = (g.northRow >> 4) - 1, south = g.southRow >> 4, east = g.maxX >> 4, west = (g.minX >> 4) - 1;
        int middle = (g.northRow + g.southRow) / 2 >> 4, x = g.a / 16 >> 4;
        Object[][] chunks = {
            {"east-west (east side)", new ChunkPos(east, middle)}, {"east-west (west side)", new ChunkPos(west, middle)},
            {"north fold", new ChunkPos(x, north)}, {"north fold", new ChunkPos(-x - 3, north)},
            {"south fold", new ChunkPos(x + 1, south)}, {"south fold", new ChunkPos(-x - 2, south)},
            {"near F", new ChunkPos(east, north)}, {"near N", new ChunkPos(0, north)}, {"near N (west)", new ChunkPos(-1, north)},
            {"near E", new ChunkPos(g.a / 64, south)}, {"near W", new ChunkPos(-g.a / 64 - 1, south)}};
        List<String> report = new ArrayList<>(), wrong = new ArrayList<>();
        report.add(String.format(Locale.ROOT, "%-24s %-12s %-12s %10s %12s %12s", "Nether " + g.size.id() + " blocks", "band chunk", "source", "cells",
            "solid diff", "fluid diff"));
        for (Object[] entry : chunks) {
            String name = (String) entry[0];
            ChunkPos band = (ChunkPos) entry[1];
            OrbifoldGeometry.Cell source = g.canonChunk(band.x, band.z);
            Motion frame = source.frame();
            ChunkAccess here = fill(level, generator, band), there = fill(level, generator, new ChunkPos(source.x(), source.z()));
            int cells = 0, solid = 0, fluid = 0;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int bx = band.getMinBlockX() + dx, bz = band.getMinBlockZ() + dz;
                    int sx = frame.cellX(bx) & 15, sz = frame.cellZ(bz) & 15;
                    for (int y = level.getMinBuildHeight(); y < 128; y++) {
                        BlockState a = here.getBlockState(new BlockPos(dx, y, dz)), b = there.getBlockState(new BlockPos(sx, y, sz));
                        cells++;
                        boolean solidA = a.getFluidState().isEmpty() && !a.isAir(), solidB = b.getFluidState().isEmpty() && !b.isAir();
                        if (solidA != solidB) solid++;
                        else if (!solidA && !a.equals(b)) fluid++;
                    }
                }
            }
            report.add(String.format(Locale.ROOT, "%-24s %-12s %-12s %10d %11.4f%% %11.4f%%", name, band.x + "," + band.z, source.x() + "," + source.z(),
                cells, 100.0 * solid / cells, 100.0 * fluid / cells));
            if ((double) solid / cells > MAX_SOLID_RESIDUE) wrong.add(name + " " + solid + "/" + cells);
        }
        AlphaOmegaMod.LOGGER.info("Nether noise-stage block residue across seams:\n{}", String.join("\n", report));
        helper.assertTrue(wrong.isEmpty(), "solid/open blocks differ across Nether seams: " + wrong);
        helper.succeed();
    }

    private static final double MAX_SOLID_RESIDUE = 0.01;

    /** A chunk's noise stage, generated into a scratch chunk outside the level. */
    private static ChunkAccess fill(ServerLevel level, OrbifoldChunkGenerator generator, ChunkPos pos) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        java.util.concurrent.CompletableFuture<ChunkAccess> filled =
            generator.fillAnyFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), chunk);
        while (!filled.isDone()) {
            if (!level.getChunkSource().pollTask()) java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        }
        return filled.join();
    }

    // ---- Band copies ----

    private static Set<ChunkPos> force(ServerLevel level, BlockPos... positions) {
        Set<ChunkPos> forced = new HashSet<>();
        for (BlockPos pos : positions) {
            ChunkPos chunk = new ChunkPos(pos);
            if (forced.add(chunk)) TestChunks.force(level, chunk);
        }
        return forced;
    }

    /**
     * Lava poured on a floor just inside the east seam (above the roof) flows across it into the band; the band copy
     * holds what its source holds, cell for cell ({@code /orbifold check}), and the lava reached past the seam.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void netherLavaFlowsAcrossASeam(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        int y = 140, z0 = g.northRow + 24;
        // The floor: 12 each side of the seam, 17 wide, so no edge is within lava's 4-block search for a drop (which would draw
        // all of the flow toward it); the lava source 2 inside.
        BlockPos source = new BlockPos(g.maxX - 2, y + 1, z0);
        Set<ChunkPos> forced = force(level, new BlockPos(g.maxX - 12, y, z0), new BlockPos(g.maxX + 12, y, z0),
            new BlockPos(g.minX + 12, y, z0), new BlockPos(g.minX - 12, y, z0));
        for (int x = g.maxX - 12; x <= g.maxX + 12; x++) {
            for (int z = z0 - 8; z <= z0 + 8; z++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int dy = 1; dy <= 2; dy++) level.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(source, Blocks.LAVA.defaultBlockState(), 3);
        helper.runAfterDelay(160, () -> {
            BlockPos past = new BlockPos(g.maxX + 2, y + 1, z0);
            BlockState there = level.getBlockState(past);
            BlockPos atSource = Transform.of(g.frame(past.getX(), past.getZ())).block(past);
            if (!there.is(Blocks.LAVA)) {
                StringBuilder row = new StringBuilder();
                for (int x = g.maxX - 4; x <= g.maxX + 4; x++) {
                    BlockPos p = new BlockPos(x, y + 1, z0);
                    row.append(x).append('=').append(level.getBlockState(p)).append('/').append(level.getBlockState(p.below()).getBlock()).append(' ');
                }
                helper.fail("lava should have flowed past the seam to " + past.toShortString() + ", found " + there + "; row " + row);
            }
            helper.assertTrue(level.getBlockState(atSource).is(Blocks.LAVA), "its source cell " + atSource.toShortString() + " holds " + level.getBlockState(atSource));
            BandCheck.Result check = BandCheck.check(level, forced);
            BandCheck.Result around = BandCheck.check(level, new ChunkPos(past), 1);
            helper.assertTrue(check.chunks() + around.chunks() > 0, "no band chunk was checked");
            helper.assertTrue(check.clean() && around.clean(), "Nether band copies differ: " + check + "; " + around);
            for (int x = g.maxX - 12; x <= g.maxX + 12; x++) {
                for (int z = z0 - 8; z <= z0 + 8; z++) {
                    for (int dy = 0; dy <= 2; dy++) level.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }

    // ---- Transfers ----

    /** Frame transfer events seen, for {@link #netherThingsCrossSeams}. */
    private static final List<g_mungus.alpha_omega.api.FrameTransferEvent> EVENTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.concurrent.atomic.AtomicBoolean LISTENING = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * An item and a mob flying out past {@code H} in the Nether, across the east seam and the north fold, cross into the
     * tile, and each crossing posts its {@code FrameTransferEvent} in the Nether.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void netherThingsCrossSeams(GameTestHelper helper) {
        if (LISTENING.compareAndSet(false, true)) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((g_mungus.alpha_omega.api.FrameTransferEvent event) -> EVENTS.add(event));
        }
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        double d = g.band - 0.3, zMid = (g.northRow + g.southRow) / 2.0 + 0.5, xNorth = -g.a / 8.0 + 0.5;
        record Crossing(String name, Vec3 start, Vec3 direction, Motion expected) {
        }
        List<Crossing> crossings = List.of(new Crossing("east", new Vec3(g.maxX + d, HEIGHT, zMid), new Vec3(1, 0, 0), g.west),
            new Crossing("north fold", new Vec3(xNorth, HEIGHT, g.northRow - d), new Vec3(0, 0, -1), g.northFold));
        List<BlockPos> points = new ArrayList<>();
        for (Crossing c : crossings) {
            for (int i = 0; i < 2; i++) {
                for (double ahead : new double[] {0.0, 2.0}) {
                    Vec3 p = c.start.add(new Vec3(-c.direction.z, 0, c.direction.x).scale(3.0 * i)).add(c.direction.scale(ahead));
                    points.add(BlockPos.containing(p));
                    points.add(BlockPos.containing(Transform.of(c.expected).position(p)));
                }
            }
        }
        Set<ChunkPos> forced = force(level, points.toArray(BlockPos[]::new));
        List<Entity> entities = new ArrayList<>();
        List<Crossing> of = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        for (Crossing c : crossings) {
            for (int i = 0; i < 2; i++) {
                Vec3 at = c.start.add(new Vec3(-c.direction.z, 0, c.direction.x).scale(3.0 * i)).add(c.direction.scale(i == 1 ? 0.5 : 0.0));
                Entity entity;
                if (i == 0) {
                    ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.QUARTZ), 0, 0, 0);
                    item.setNeverPickUp();
                    item.setUnlimitedLifetime();
                    entity = item;
                } else {
                    ZombifiedPiglin piglin = EntityType.ZOMBIFIED_PIGLIN.create(level);
                    piglin.setNoAi(true);
                    piglin.setPersistenceRequired();
                    entity = piglin;
                }
                entity.setNoGravity(true);
                float yaw = (float) Math.toDegrees(Math.atan2(-c.direction.x, c.direction.z));
                entity.moveTo(at.x, at.y, at.z, yaw, 0.0F);
                entity.setDeltaMovement(c.direction.scale(0.4));
                level.addFreshEntity(entity);
                entities.add(entity);
                of.add(c);
                starts.add(at);
                yaws.add(yaw);
            }
        }
        helper.runAfterDelay(30, () -> {
            for (int i = 0; i < entities.size(); i++) {
                Entity entity = entities.get(i);
                Crossing c = of.get(i);
                String what = entity.getType().toShortString() + " at the Nether's " + c.name;
                Vec3 landed = Transform.of(c.expected).position(starts.get(i));
                helper.assertTrue(g.isTile((int) Math.floor(entity.getX()), (int) Math.floor(entity.getZ())) && entity.position().distanceTo(landed) < 16.0,
                    what + " should have crossed by " + c.expected + " to near " + landed + ", but is at " + entity.position());
                helper.assertTrue(EVENTS.stream().anyMatch(e -> e.entity() == entity && e.level() == level && e.motion().equals(c.expected)),
                    what + ": no frame transfer event in the Nether");
                if (entity instanceof ZombifiedPiglin) {
                    float expectedYaw = c.expected.yaw(yaws.get(i));
                    helper.assertTrue(Math.abs(Math.IEEEremainder(entity.getYRot() - expectedYaw, 360.0)) < 1e-3,
                        what + ": yaw " + entity.getYRot() + ", expected " + expectedYaw);
                } else {
                    Vec3 v = entity.getDeltaMovement(), expected = Transform.of(c.expected).vector(c.direction);
                    helper.assertTrue(new Vec3(v.x, 0, v.z).normalize().dot(expected) > 0.99, what + ": velocity " + v + " should point along " + expected);
                }
                entity.discard();
            }
            TestChunks.release(level, forced);
            helper.succeed();
        });
    }

    // ---- Portals ----

    /**
     * Builds a lit Nether portal with its interior's bottom-left cell at {@code corner} (2 wide along {@code axis}, 3
     * tall), clearing the space around it. Returns the interior cells.
     */
    private static List<BlockPos> portal(ServerLevel level, BlockPos corner, Direction.Axis axis) {
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis), across = along.getClockWise();
        for (int u = -2; u <= 3; u++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int w = -1; w <= 1; w++) level.setBlock(corner.relative(along, u).relative(across, w).above(dy), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (int u = -1; u <= 2; u++) {
            level.setBlock(corner.relative(along, u).below(), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(corner.relative(along, u).above(3), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
        for (int dy = 0; dy <= 2; dy++) {
            level.setBlock(corner.relative(along, -1).above(dy), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(corner.relative(along, 2).above(dy), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
        PortalShape.findEmptyPortalShape(level, corner, axis).ifPresent(PortalShape::createPortalBlocks);
        List<BlockPos> interior = new ArrayList<>();
        for (int u = 0; u <= 1; u++) {
            for (int dy = 0; dy <= 2; dy++) interior.add(corner.relative(along, u).above(dy));
        }
        return interior;
    }

    /** A pig standing at {@code at} facing {@code yaw}, not added to the level: only asked where a portal takes it. */
    private static Entity pig(ServerLevel level, Vec3 at, float yaw) {
        Entity pig = EntityType.PIG.create(level);
        pig.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        pig.setDeltaMovement(Vec3.ZERO);
        return pig;
    }

    @org.jetbrains.annotations.Nullable
    private static DimensionTransition through(ServerLevel level, Entity entity, BlockPos portal) {
        return ((NetherPortalBlock) Blocks.NETHER_PORTAL).getPortalDestination(level, entity, portal);
    }

    /** A point's canonical copy, as a vector. */
    private static Vec3 canonical(OrbifoldGeometry g, Vec3 p) {
        return Transform.of(g.frame(p.x, p.z)).position(p);
    }

    /**
     * Portals at overworld seams: a portal just inside the east seam and one just inside the north fold, each with a band
     * copy. A pig in the band copy and one at the same place in the tile copy go to the same place in the Nether: the
     * source's canonical position at 1:8, in the Nether's tile. From there the Nether's portal leads back to the same
     * portal: the arrival's canonical position is in the original portal.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherPortalsAtOverworldSeamsLinkBack(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel(), nether = nether(helper);
        OrbifoldGeometry g = geometry(helper, overworld), n = geometry(helper, nether);
        int y = 150;
        record Site(String name, BlockPos corner, Direction.Axis axis) {
        }
        List<Site> sites = List.of(new Site("east seam", new BlockPos(g.maxX - 4, y, g.northRow + 1300), Direction.Axis.Z),
            new Site("north fold", new BlockPos(-600, y, g.northRow + 3), Direction.Axis.X));
        List<String> report = new ArrayList<>();
        for (Site site : sites) {
            BlockPos corner = site.corner;
            Motion toBand = g.copies(corner.getX(), corner.getZ()).stream().findFirst().map(c -> c.frame().inverse()).orElse(null);
            helper.assertTrue(toBand != null, site.name + ": the portal at " + corner.toShortString() + " should have a band copy");
            BlockPos bandCorner = Transform.of(toBand).block(corner);
            Set<ChunkPos> forced = force(overworld, corner, bandCorner, corner.offset(3, 0, 3), bandCorner.offset(-3, 0, -3));
            List<BlockPos> interior = portal(overworld, corner, site.axis);
            for (BlockPos cell : interior) {
                BlockPos copy = Transform.of(toBand).block(cell);
                helper.assertTrue(overworld.getBlockState(cell).is(Blocks.NETHER_PORTAL), site.name + ": portal not lit at " + cell.toShortString());
                helper.assertTrue(overworld.getBlockState(copy).is(Blocks.NETHER_PORTAL), site.name + ": the band copy " + copy.toShortString()
                    + " holds " + overworld.getBlockState(copy));
            }
            // The tile pig a little off the portal's middle, facing along it; the band pig at its copy.
            BlockPos cell = interior.get(1);
            Vec3 at = new Vec3(cell.getX() + 0.3, cell.getY(), cell.getZ() + 0.7);
            float yaw = 30.0F;
            Entity tilePig = pig(overworld, at, yaw), bandPig = pig(overworld, Transform.of(toBand).position(at), toBand.yaw(yaw));
            // The Nether's portal, built where the scaled source lands, as a player would build it: vanilla's own
            // search for a place to make one may land 16 blocks off, 128 back in the overworld, past the search there.
            Vec3 scaled = canonical(n, new Vec3(at.x / 8.0, 0, at.z / 8.0));
            BlockPos netherCorner = new BlockPos((int) Math.floor(scaled.x), y, (int) Math.floor(scaled.z));
            Set<ChunkPos> netherForced = force(nether, netherCorner, netherCorner.offset(3, 0, 3), netherCorner.offset(-3, 0, -3));
            List<BlockPos> netherInterior = portal(nether, netherCorner, site.axis);
            DimensionTransition fromTile = through(overworld, tilePig, cell), fromBand = through(overworld, bandPig, Transform.of(toBand).block(cell));
            helper.assertTrue(fromTile != null && fromBand != null, site.name + ": no transition");
            helper.assertTrue(fromTile.newLevel() == nether && fromBand.newLevel() == nether, site.name + ": should go to the Nether");
            helper.assertTrue(fromTile.pos().distanceTo(fromBand.pos()) < 1e-6, site.name + ": the band copy goes to " + fromBand.pos()
                + ", the tile copy to " + fromTile.pos());
            helper.assertTrue(Math.abs(Math.IEEEremainder(fromTile.yRot() - fromBand.yRot(), 360.0)) < 1e-3, site.name + ": yaw " + fromBand.yRot()
                + " from the band copy, " + fromTile.yRot() + " from the tile copy");
            Vec3 arrival = fromTile.pos();
            helper.assertTrue(n.isTile((int) Math.floor(arrival.x), (int) Math.floor(arrival.z)), site.name + ": the arrival " + arrival + " is not in the Nether's tile");
            boolean inNetherPortal = netherInterior.stream().anyMatch(c -> Math.abs(c.getX() + 0.5 - arrival.x) < 1.6 && Math.abs(c.getZ() + 0.5 - arrival.z) < 1.6);
            helper.assertTrue(inNetherPortal, site.name + ": the Nether arrival " + arrival + " is not in the portal built at the scaled source " + scaled);
            // And back: from the Nether's portal, to the same portal's copy set.
            BlockPos exitCell = BlockPos.containing(arrival);
            if (!nether.getBlockState(exitCell).is(Blocks.NETHER_PORTAL)) exitCell = exitCell.above();
            helper.assertTrue(nether.getBlockState(exitCell).is(Blocks.NETHER_PORTAL), site.name + ": no portal at the Nether arrival " + arrival);
            netherForced.addAll(force(nether, exitCell));
            DimensionTransition back = through(nether, pig(nether, arrival, fromTile.yRot()), exitCell);
            helper.assertTrue(back != null && back.newLevel() == overworld, site.name + ": no way back");
            Vec3 home = canonical(g, back.pos());
            boolean inPortal = interior.stream().anyMatch(c -> Math.abs(c.getX() + 0.5 - home.x) < 1.6 && Math.abs(c.getZ() + 0.5 - home.z) < 1.6
                && Math.abs(c.getY() - home.y) < 3.0);
            helper.assertTrue(inPortal, site.name + ": back at " + back.pos() + " (canonical " + home + "), not in the portal at " + corner.toShortString());
            report.add(String.format(Locale.ROOT, "%s: overworld %s (band copy %s) -> Nether %s -> overworld %s", site.name, at, bandPig.position(), arrival, back.pos()));
            // Both levels' band copies around the portals hold what their sources hold.
            BandCheck.Result atNether = BandCheck.check(nether, new ChunkPos(netherCorner), 3), atHome = BandCheck.check(overworld, new ChunkPos(corner), 3);
            helper.assertTrue(atNether.clean() && atHome.clean(), site.name + ": copies differ: Nether " + atNether + "; overworld " + atHome);
            TestChunks.release(overworld, forced);
            TestChunks.release(nether, netherForced);
        }
        AlphaOmegaMod.LOGGER.info("Portals at overworld seams:\n{}", String.join("\n", report));
        helper.succeed();
    }

    /**
     * A Nether portal just inside the Nether's north fold, with a band copy past the fold: a pig at the band copy and one at
     * the same place in the tile copy go to the same place in the overworld (8 times the canonical position), and the
     * overworld's portal there leads back into the same Nether portal.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void netherPortalInTheBandLinksBack(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel(), nether = nether(helper);
        OrbifoldGeometry g = geometry(helper, overworld), n = geometry(helper, nether);
        BlockPos corner = new BlockPos(-n.a / 4, 150, n.northRow + 3);
        Motion toBand = n.copies(corner.getX(), corner.getZ()).stream().filter(c -> c.frame().turned()).findFirst().map(c -> c.frame().inverse()).orElse(null);
        helper.assertTrue(toBand != null, "the Nether portal at " + corner.toShortString() + " should have a copy past the north fold");
        BlockPos bandCorner = Transform.of(toBand).block(corner);
        Set<ChunkPos> forced = force(nether, corner, bandCorner, corner.offset(3, 0, 3), bandCorner.offset(-3, 0, -3));
        List<BlockPos> interior = portal(nether, corner, Direction.Axis.X);
        BlockPos cell = interior.get(4);
        for (BlockPos c : interior) {
            helper.assertTrue(nether.getBlockState(Transform.of(toBand).block(c)).is(Blocks.NETHER_PORTAL), "the band copy of " + c.toShortString() + " is not portal");
        }
        Vec3 at = new Vec3(cell.getX() + 0.6, cell.getY(), cell.getZ() + 0.4);
        float yaw = -60.0F;
        Entity tilePig = pig(nether, at, yaw), bandPig = pig(nether, Transform.of(toBand).position(at), toBand.yaw(yaw));
        DimensionTransition fromTile = through(nether, tilePig, cell), fromBand = through(nether, bandPig, Transform.of(toBand).block(cell));
        helper.assertTrue(fromTile != null && fromBand != null && fromTile.newLevel() == overworld && fromBand.newLevel() == overworld, "no way to the overworld");
        helper.assertTrue(fromTile.pos().distanceTo(fromBand.pos()) < 1e-6, "the band copy goes to " + fromBand.pos() + ", the tile copy to " + fromTile.pos());
        helper.assertTrue(Math.abs(Math.IEEEremainder(fromTile.yRot() - fromBand.yRot(), 360.0)) < 1e-3, "yaw " + fromBand.yRot() + " vs " + fromTile.yRot());
        Vec3 arrival = fromTile.pos();
        helper.assertTrue(g.isTile((int) Math.floor(arrival.x), (int) Math.floor(arrival.z)), "the overworld arrival " + arrival + " is not in the tile");
        double off = Math.hypot(arrival.x - at.x * 8.0, arrival.z - at.z * 8.0);
        helper.assertTrue(off < 24.0, "the overworld arrival " + arrival + " is " + off + " from 8 times the source");
        BlockPos exitCell = BlockPos.containing(arrival);
        if (!overworld.getBlockState(exitCell).is(Blocks.NETHER_PORTAL)) exitCell = exitCell.above();
        helper.assertTrue(overworld.getBlockState(exitCell).is(Blocks.NETHER_PORTAL), "no portal at the overworld arrival " + arrival);
        Set<ChunkPos> overworldForced = force(overworld, exitCell);
        DimensionTransition back = through(overworld, pig(overworld, arrival, fromTile.yRot()), exitCell);
        helper.assertTrue(back != null && back.newLevel() == nether, "no way back to the Nether");
        Vec3 home = canonical(n, back.pos());
        boolean inPortal = interior.stream().anyMatch(c -> Math.abs(c.getX() + 0.5 - home.x) < 1.6 && Math.abs(c.getZ() + 0.5 - home.z) < 1.6
            && Math.abs(c.getY() - home.y) < 3.0);
        helper.assertTrue(inPortal, "back at " + back.pos() + " (canonical " + home + "), not in the Nether portal at " + corner.toShortString());
        AlphaOmegaMod.LOGGER.info("Nether portal in the band: Nether {} (band copy {}) -> overworld {} -> Nether {}", at, bandPig.position(), arrival, back.pos());
        BandCheck.Result atNether = BandCheck.check(nether, new ChunkPos(corner), 3), atBand = BandCheck.check(nether, new ChunkPos(bandCorner), 3);
        helper.assertTrue(atNether.clean() && atBand.clean(), "Nether copies differ: " + atNether + "; " + atBand);
        TestChunks.release(nether, forced);
        TestChunks.release(overworld, overworldForced);
        helper.succeed();
    }

    /** Band rules apply in the Nether: its band chunks are linked to their sources, its tile chunks are not band. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void netherBandIsLinked(GameTestHelper helper) {
        ServerLevel level = nether(helper);
        OrbifoldGeometry g = geometry(helper, level);
        helper.assertTrue(Band.geometry(level) == g, "the Nether's band rules should use its own geometry");
        helper.assertTrue(Band.geometry(helper.getLevel()) != g, "the overworld's band rules should use the overworld's geometry");
        helper.succeed();
    }
}
