package g_mungus.alpha_omega.gametest;

import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.orbifold.Orbifold;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.worldgen.OrbifoldChunkGenerator;
import g_mungus.alpha_omega.worldgen.OrbifoldSpawn;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The generator skeleton (plan phase 3): the tile is vanilla terrain, the band and skirt generate empty, the rest is
 * void, no structure reaches a seam, and the world spawn is near the geometry's spawn point.
 */
@GameTestHolder(AlphaOmegaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GeneratorGameTests {

    private static final String TEMPLATE = "gametest/flat_7x4x7";

    private static OrbifoldGeometry geometry(GameTestHelper helper) {
        OrbifoldGeometry geometry = Orbifold.of(helper.getLevel());
        if (geometry == null) helper.fail("the gametest overworld should be an orbifold world");
        return geometry;
    }

    private static OrbifoldChunkGenerator generator(GameTestHelper helper) {
        return (OrbifoldChunkGenerator) helper.getLevel().getChunkSource().getGenerator();
    }

    private static boolean allAir(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) return false;
        }
        return true;
    }

    /** Up to eight non-air blocks of a chunk, for messages. */
    private static String blocks(ChunkAccess chunk) {
        List<String> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight() && found.size() < 8; y++) {
            for (int dx = 0; dx < 16 && found.size() < 8; dx++) {
                for (int dz = 0; dz < 16 && found.size() < 8; dz++) {
                    pos.set(chunk.getPos().getMinBlockX() + dx, y, chunk.getPos().getMinBlockZ() + dz);
                    if (!chunk.getBlockState(pos).isAir()) found.add(pos.toShortString() + " " + chunk.getBlockState(pos));
                }
            }
        }
        return found.toString();
    }

    /** The first band chunk past each seam, beside a tile chunk: {name, band chunk, tile chunk}. */
    private static List<Object[]> bandChunks(OrbifoldGeometry g) {
        int spawnChunkZ = g.spawnZ >> 4;
        List<Object[]> chunks = new ArrayList<>();
        chunks.add(new Object[] {"east", new ChunkPos(g.maxX >> 4, spawnChunkZ), new ChunkPos((g.maxX >> 4) - 1, spawnChunkZ)});
        chunks.add(new Object[] {"west", new ChunkPos((g.minX >> 4) - 1, spawnChunkZ), new ChunkPos(g.minX >> 4, spawnChunkZ)});
        chunks.add(new Object[] {"north", new ChunkPos(20, (g.northRow >> 4) - 1), new ChunkPos(20, g.northRow >> 4)});
        chunks.add(new Object[] {"south", new ChunkPos(20, g.southRow >> 4), new ChunkPos(20, (g.southRow >> 4) - 1)});
        // Past N: the band there holds turned copies of the tile just south of it.
        chunks.add(new Object[] {"north at N", new ChunkPos(0, (g.northRow >> 4) - 1), new ChunkPos(0, g.northRow >> 4)});
        // The skirt, past the band's corner beyond F.
        int skirt = (g.band >> 4) + 1;
        chunks.add(new Object[] {"skirt at F", new ChunkPos((g.maxX >> 4) - 1 + skirt, (g.northRow >> 4) - skirt), new ChunkPos((g.maxX >> 4) - 1, g.northRow >> 4)});
        return chunks;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void voidOutsideFootprint(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        int[] bounds = g.footprintChunks();
        List<ChunkPos> outside = List.of(new ChunkPos(bounds[2] + 1, g.spawnZ >> 4), new ChunkPos(bounds[0] - 1, g.spawnZ >> 4),
            new ChunkPos(40, bounds[1] - 1), new ChunkPos(0, bounds[3] + 1), new ChunkPos(bounds[2] + 3, bounds[3] + 3));
        for (ChunkPos pos : outside) {
            helper.assertTrue(!g.inFootprintChunk(pos.x, pos.z) && generator(helper).region(pos) == OrbifoldChunkGenerator.Region.VOID,
                pos + " should be outside the footprint");
            ChunkAccess chunk = level.getChunk(pos.x, pos.z);
            helper.assertTrue(allAir(chunk), "void chunk " + pos + " has blocks");
            helper.assertTrue(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 7, 7) < level.getMinBuildHeight(), "void chunk " + pos + " has a surface");
            BlockPos middle = pos.getMiddleBlockPosition(0);
            helper.assertTrue(level.getBrightness(LightLayer.SKY, middle) == 15, "void chunk " + pos + " is not sky lit: "
                + level.getBrightness(LightLayer.SKY, middle));
        }
        helper.succeed();
    }

    /**
     * A void chunk lit, saved and unloaded, then its skirt neighbour filled while it is away, is still sky lit when it
     * loads again ({@code ChunkLightMixin}; before, the fill left it dark below the skirt's terrain).
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 800)
    public static void voidStaysLitBesideAFilledSkirt(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        ServerLevel level = helper.getLevel();
        int[] bounds = g.footprintChunks();
        // Past the north skirt at N, where the fill first left the void dark.
        ChunkPos skirt = new ChunkPos(0, bounds[1]), voidChunk = new ChunkPos(skirt.x, bounds[1] - 1);
        helper.assertTrue(generator(helper).region(skirt) == OrbifoldChunkGenerator.Region.SKIRT, skirt + " should be skirt");
        BlockPos middle = voidChunk.getMiddleBlockPosition(0);
        level.getChunk(voidChunk.x, voidChunk.z);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(level.getChunkSource().getChunkNow(voidChunk.x, voidChunk.z) == null, "the void chunk should have unloaded");
            helper.assertTrue(!allAir(level.getChunk(skirt.x, skirt.z)), "the skirt chunk should be filled from its source");
            helper.runAfterDelay(200, () -> {
                level.getChunk(voidChunk.x, voidChunk.z);
                List<String> dark = new ArrayList<>();
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 16) {
                    int light = level.getBrightness(LightLayer.SKY, middle.atY(y));
                    if (light != 15) dark.add(y + ": " + light);
                }
                helper.assertTrue(dark.isEmpty(), "void chunk " + voidChunk + " reloaded dark: " + dark);
                helper.succeed();
            });
        });
    }

    /**
     * Band and skirt chunks are generated empty, waiting for phase 4 to fill them; the tile chunk beside each has
     * terrain. Checked on a scratch chunk through the generator's noise fill, and on the level's chunk at the features
     * step, before it is promoted (and so before any fill), unless another test already has it in play.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 1200)
    public static void bandChunksGenerateEmpty(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        OrbifoldChunkGenerator generator = generator(helper);
        ServerLevel level = helper.getLevel();
        for (Object[] entry : bandChunks(g)) {
            String name = (String) entry[0];
            ChunkPos band = (ChunkPos) entry[1], tile = (ChunkPos) entry[2];
            helper.assertTrue(generator.awaitsFill(band), name + ": " + band + " should await a fill, is " + generator.region(band));
            helper.assertTrue(generator.region(tile) == OrbifoldChunkGenerator.Region.TILE && !generator.awaitsFill(tile), name + ": " + tile + " should be tile");
            // The generator itself, on a scratch chunk: whatever the level has done to the real one since.
            ChunkAccess scratch = new ProtoChunk(band, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
            generator.fillFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), scratch).join();
            helper.assertTrue(allAir(scratch), name + ": the noise fill of band chunk " + band + " has blocks: " + blocks(scratch));
            // The level's chunk through every generation step, unless another test has already loaded it into play:
            // a full band chunk is gameplay's (fluids from the tile flow into it until phase 4 fills it).
            ChunkAccess chunk = level.getChunkSource().getChunk(band.x, band.z, ChunkStatus.FEATURES, true);
            if (chunk.getPersistedStatus() == ChunkStatus.FULL) {
                AlphaOmegaMod.LOGGER.info("Band chunk {} has already been in play; checked its generation on a scratch chunk only", band);
            } else {
                helper.assertTrue(allAir(chunk), name + ": band chunk " + band + " (" + chunk.getPersistedStatus() + ") has blocks after generation: "
                    + blocks(chunk));
                for (Heightmap.Types type : new Heightmap.Types[] {Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
                    for (int dx = 0; dx < 16; dx += 5) {
                        for (int dz = 0; dz < 16; dz += 5) {
                            int height = chunk.getHeight(type, dx, dz);
                            helper.assertTrue(height < level.getMinBuildHeight(), name + ": band chunk " + band + " " + type + " at " + dx + "," + dz + " is " + height);
                        }
                    }
                }
            }
            ChunkAccess beside = level.getChunkSource().getChunk(tile.x, tile.z, ChunkStatus.FEATURES, true);
            helper.assertTrue(!allAir(beside), name + ": tile chunk " + tile + " has no terrain");
        }
        helper.succeed();
    }

    /** Every structure start near every seam, in the tile or past it, lies inside the tile with the margin. */
    @GameTest(template = TEMPLATE, timeoutTicks = 2400)
    public static void noStructureCrossesASeam(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        OrbifoldChunkGenerator generator = generator(helper);
        ServerLevel level = helper.getLevel();
        int rejectedBefore = OrbifoldChunkGenerator.REJECTED_STRUCTURES.get();
        int along = 24, inside = 8, outside = 2;
        List<ChunkPos> chunks = new ArrayList<>();
        int east = (g.maxX >> 4) - 1, west = g.minX >> 4, north = g.northRow >> 4, south = (g.southRow >> 4) - 1;
        for (int i = 0; i < along; i++) {
            for (int d = -outside; d < inside; d++) {
                chunks.add(new ChunkPos(east - d, (g.spawnZ >> 4) - along / 2 + i));
                chunks.add(new ChunkPos(west + d, (g.spawnZ >> 4) - along / 2 + i));
                chunks.add(new ChunkPos(-along / 2 + i, north + d));
                chunks.add(new ChunkPos(-along / 2 + i, south - d));
                chunks.add(new ChunkPos((g.maxX >> 4) - along + i, north + d));
            }
        }
        int starts = 0;
        List<String> crossing = new ArrayList<>();
        for (ChunkPos pos : chunks) {
            ChunkAccess chunk = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true);
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (!start.isValid()) continue;
                starts++;
                BoundingBox box = start.getBoundingBox();
                if (!generator.fitsInTile(box) && crossing.size() < 5) crossing.add(pos + ": " + box);
            }
        }
        int rejected = OrbifoldChunkGenerator.REJECTED_STRUCTURES.get() - rejectedBefore;
        AlphaOmegaMod.LOGGER.info("Structure filter: {} starts kept near seams in {} chunks, {} turned away", starts, chunks.size(), rejected);
        helper.assertTrue(crossing.isEmpty(), "structure boxes reach past the tile: " + crossing);
        helper.assertTrue(starts > 0, "no structure starts near the seams to check");
        helper.succeed();
    }

    /** The filter's bounds: a box is kept up to the margin from each seam and turned away one block past it. */
    @GameTest(template = TEMPLATE)
    public static void structureFilterKeepsTheMargin(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        OrbifoldChunkGenerator generator = generator(helper);
        int m = OrbifoldChunkGenerator.STRUCTURE_MARGIN, y0 = 0, y1 = 80;
        helper.assertTrue(m >= 12 + 4, "the margin " + m + " should cover the beardifier's 12 blocks of terrain adaptation, plus 4");
        int x0 = g.minX + m, x1 = g.maxX - m - 1, z0 = g.northRow + m, z1 = g.southRow - m - 1;
        helper.assertTrue(generator.fitsInTile(new BoundingBox(x0, y0, z0, x1, y1, z1)), "the tile less the margin should fit");
        helper.assertTrue(!generator.fitsInTile(new BoundingBox(x0 - 1, y0, 0, x0 + 20, y1, 20)), "a box within the margin of the west seam fits");
        helper.assertTrue(!generator.fitsInTile(new BoundingBox(x1 - 20, y0, 0, x1 + 1, y1, 20)), "a box within the margin of the east seam fits");
        helper.assertTrue(!generator.fitsInTile(new BoundingBox(0, y0, z0 - 1, 20, y1, z0 + 20)), "a box within the margin of the north fold fits");
        helper.assertTrue(!generator.fitsInTile(new BoundingBox(0, y0, z1 - 20, 20, y1, z1 + 1)), "a box within the margin of the south fold fits");
        helper.assertTrue(!generator.fitsInTile(new BoundingBox(g.maxX + 10, y0, 0, g.maxX + 40, y1, 20)), "a box in the band fits");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void spawnIsNearTheSpawnPoint(GameTestHelper helper) {
        OrbifoldGeometry g = geometry(helper);
        BlockPos spawn = helper.getLevel().getSharedSpawnPos();
        double distance = Math.hypot(spawn.getX() - g.spawnX, spawn.getZ() - g.spawnZ);
        helper.assertTrue(distance <= OrbifoldSpawn.RADIUS, "world spawn " + spawn + " is " + (int) distance + " blocks from the spawn point ("
            + g.spawnX + ", " + g.spawnZ + ")");
        helper.succeed();
    }
}
