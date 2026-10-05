package g_mungus.alpha_omega.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.block.EdgeAirBlock;
import g_mungus.alpha_omega.block.EdgeBedrockBlock;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import g_mungus.alpha_omega.mixin.worldgen.NoiseBasedChunkGeneratorAccessor;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The overworld generator of a cube world (design §4.1). Its settings are the cube's shape, so a world is a cube
 * world exactly when its overworld uses this generator.
 *
 * <p>Chunks outside every face's footprint stay empty. Inside, vanilla terrain is generated, then each column gets
 * its barrier cell (bedrock where the terrain is solid, edge air where it is open) and filler below it, where the
 * column belongs to another face (§4.4). Later steps cannot write there (the write guard).
 */
public class CubeChunkGenerator extends NoiseBasedChunkGenerator {

    public static final ResourceKey<WorldPreset> PRESET =
        ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "cube"));

    public static final MapCodec<CubeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
        CubeSettings.fieldsCodec(AlphaOmegaConfig::defaults).forGetter(CubeChunkGenerator::cube)
    ).apply(instance, CubeChunkGenerator::new));

    private final CubeSettings cube;
    private volatile CubeGeometry geometry;

    public CubeChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, CubeSettings cube) {
        super(biomeSource, settings);
        this.cube = cube;
    }

    public CubeSettings cube() {
        return this.cube;
    }

    /** The geometry for a level using this generator; the face plane is at sea level. */
    public CubeGeometry geometry(LevelHeightAccessor level) {
        CubeGeometry geometry = this.geometry;
        if (geometry == null) {
            geometry = new CubeGeometry(this.cube, this.getSeaLevel(), level.getMinBuildHeight(), level.getMaxBuildHeight());
            this.geometry = geometry;
        }
        return geometry;
    }

    /** Structures keep this far from the barrier (§4.5). */
    private static final int STRUCTURE_MARGIN = 8;

    /** Structure starts turned away for crossing the barrier, for tests and debugging. */
    public static final AtomicInteger REJECTED_STRUCTURES = new AtomicInteger();

    /** {@link #fitsOnFace}, counting rejections: called as each structure start is generated. */
    public boolean keepStructure(BoundingBox box, LevelHeightAccessor level) {
        boolean fits = this.fitsOnFace(box, level);
        if (!fits) {
            REJECTED_STRUCTURES.incrementAndGet();
            AlphaOmegaMod.LOGGER.debug("Structure at {} crosses the barrier", box);
        }
        return fits;
    }

    /** Whether a structure with this bounding box may generate: wholly inside one face, clear of the barrier. */
    public boolean fitsOnFace(BoundingBox box, LevelHeightAccessor level) {
        CubeGeometry geometry = this.geometry(level);
        CubeFace face = geometry.faceAt(box.minX(), box.minZ());
        return face != null && geometry.boxOwned(face, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), STRUCTURE_MARGIN);
    }

    private boolean inFootprint(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        return this.geometry(chunk).inFootprint(pos.x, pos.z);
    }

    /**
     * Vanilla's noise settings for a chunk, with the bottom raised to its noise floor: the section holding its lowest
     * barrier cell. Every cell below that is another face's, so it becomes filler whatever the noise says; in the
     * overhang that is most of the column. The floor is a multiple of the cell height, so the noise above it is
     * vanilla's to the block.
     */
    public NoiseGeneratorSettings noiseSettings(ChunkAccess chunk) {
        NoiseGeneratorSettings settings = this.generatorSettings().value();
        if (!this.inFootprint(chunk)) return settings;
        NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(chunk);
        int top = noise.minY() + noise.height();
        int floor = Math.min(Math.floorDiv(this.lowestBarrier(chunk), 16) * 16, top - 16);
        if (floor <= noise.minY()) return settings;
        return new NoiseGeneratorSettings(new NoiseSettings(floor, top - floor, noise.noiseSizeHorizontal(), noise.noiseSizeVertical()),
            settings.defaultBlock(), settings.defaultFluid(), settings.noiseRouter(), settings.surfaceRule(), settings.spawnTarget(),
            settings.seaLevel(), settings.disableMobGeneration(), settings.aquifersEnabled(), settings.oreVeinsEnabled(),
            settings.useLegacyRandomSource());
    }

    /** The y of the lowest barrier cell among a chunk's columns. */
    private int lowestBarrier(ChunkAccess chunk) {
        CubeGeometry geometry = this.geometry(chunk);
        ChunkPos pos = chunk.getPos();
        CubeFace face = geometry.faceAtChunk(pos.x, pos.z);
        int lowest = Integer.MAX_VALUE;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) lowest = Math.min(lowest, geometry.barrierY(face, pos.getMinBlockX() + dx, pos.getMinBlockZ() + dz));
        }
        return lowest;
    }

    /** Vanilla's fill, from the chunk's noise floor up ({@link #noiseSettings}); then the barrier pass. */
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        if (!this.inFootprint(chunk)) return CompletableFuture.completedFuture(chunk);
        NoiseSettings noise = this.noiseSettings(chunk).noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int minCellY = Math.floorDiv(noise.minY(), noise.getCellHeight());
        int cellCountY = Math.floorDiv(noise.height(), noise.getCellHeight());
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> {
            List<LevelChunkSection> sections = new ArrayList<>();
            for (int i = chunk.getSectionIndex(cellCountY * noise.getCellHeight() - 1 + noise.minY()); i >= chunk.getSectionIndex(noise.minY()); i--) {
                LevelChunkSection section = chunk.getSection(i);
                section.acquire();
                sections.add(section);
            }
            try {
                return ((NoiseBasedChunkGeneratorAccessor) this).alpha_omega$doFill(blender, structures, random, chunk, minCellY, cellCountY);
            } finally {
                sections.forEach(LevelChunkSection::release);
            }
        }), Util.backgroundExecutor()).thenApply(filled -> {
            try {
                return this.carveFaces(filled, random);
            } catch (RuntimeException e) {
                // Failures inside a generation future are otherwise silent: the chunk just never finishes.
                AlphaOmegaMod.LOGGER.error("Placing the barrier in chunk {} failed", filled.getPos(), e);
                throw e;
            }
        });
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        if (this.inFootprint(chunk)) super.buildSurface(region, structures, random, chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        if (!this.inFootprint(chunk)) return;
        // Caves are cut by each face on its own: keep them out of the shared band, so the terrain there stays one shape.
        CARVING.set(this.geometry(chunk));
        try {
            super.applyCarvers(region, seed, random, biomes, structures, chunk, step);
        } finally {
            CARVING.remove();
        }
    }

    /** While carvers run on this thread, the geometry of the level they carve. */
    private static final ThreadLocal<CubeGeometry> CARVING = new ThreadLocal<>();

    /** Whether a carver may write a cell: not inside the shared band next to the barrier. */
    public static boolean carverMayWrite(BlockPos pos) {
        CubeGeometry geometry = CARVING.get();
        if (geometry == null) return true;
        CubeFace face = geometry.faceAt(pos.getX(), pos.getZ());
        if (face == null) return true;
        int barrierY = geometry.barrierY(face, pos.getX(), pos.getZ());
        if (barrierY < geometry.planeY - DEEP || geometry.barrierPartner(face, pos.getX(), barrierY, pos.getZ()) == null) return true;
        int k = pos.getY() - barrierY;
        return k > SHARED + FADE;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (this.inFootprint(chunk)) super.applyBiomeDecoration(level, chunk, structures);
    }

    /** Barrier cells this far below the face plane are always bedrock: deep in the rock, nothing to match. */
    private static final int DEEP = 24;
    /** Barrier cells above this height are always edge air: no terrain reaches them. */
    private static final int SKY = 300;

    /** Terrain densities for the barrier pass of one chunk ({@link Densities}). */
    public Densities densities(RandomState random) {
        return new Densities(random, this.generatorSettings().value().noiseSettings());
    }

    /**
     * Puts each column's barrier cell in place and fills the rest of the column below it: the band just under the
     * barrier with edge filler, which collides like the neighbour's blocks there, and the rest with plain filler.
     */
    private ChunkAccess carveFaces(ChunkAccess chunk, RandomState random) {
        CubeGeometry geometry = this.geometry(chunk);
        ChunkPos pos = chunk.getPos();
        CubeFace face = geometry.faceAtChunk(pos.x, pos.z);
        if (face == null) return chunk;
        BlockState filler = CubeBlocks.FILLER.get().defaultBlockState();
        BlockState edgeFiller = CubeBlocks.EDGE_FILLER.get().defaultBlockState();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Densities densities = this.densities(random);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx, z = pos.getMinBlockZ() + dz;
                int barrierY = geometry.barrierY(face, x, z);
                this.blendTowardNeighbour(geometry, densities, chunk, face, x, barrierY, z, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz));
                int fillTop = Math.min(barrierY, geometry.maxY);
                int bandBottom = barrierY - CubeGeometry.BAND;
                for (int y = geometry.minY; y < fillTop; y++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(dx, y & 15, dz, y >= bandBottom ? edgeFiller : filler, false);
                }
                if (fillTop > geometry.minY) {
                    BlockState top = fillTop - 1 >= bandBottom ? edgeFiller : filler;
                    oceanFloor.update(dx, fillTop - 1, dz, top);
                    surface.update(dx, fillTop - 1, dz, top);
                }
                if (barrierY >= geometry.minY && barrierY < geometry.maxY) {
                    cursor.set(x, barrierY, z);
                    BlockState barrier = this.barrier(geometry, densities, face, x, barrierY, z, chunk.getBlockState(cursor));
                    chunk.getSection(chunk.getSectionIndex(barrierY)).setBlockState(dx, barrierY & 15, dz, barrier, false);
                    oceanFloor.update(dx, barrierY, dz, barrier);
                    surface.update(dx, barrierY, dz, barrier);
                }
            }
        }
        return chunk;
    }

    /** Cells of a column within this many of its barrier cell share one terrain with the neighbouring face. */
    private static final int SHARED = 8;
    /** Over this many cells beyond that, the terrain fades back to the face's own. */
    private static final int FADE = 8;
    /** The band is skipped this far above the column's own terrain: open sky on both sides. */
    private static final int BAND_SLACK = 32;

    /**
     * Near the barrier, both faces build the same terrain (design §4.3): within {@link #SHARED} cells of it the
     * density is the mean of the two faces' densities at the same physical point (the cell's corner with the smallest
     * cube coordinates), which is one function of position whichever face evaluates it, and it fades back to the
     * face's own over the next {@link #FADE}. The barrier itself is decided by the same mean. Deep in the rock (where
     * the barrier is always bedrock) and high in the sky it is skipped.
     */
    private void blendTowardNeighbour(CubeGeometry geometry, Densities densities, ChunkAccess chunk, CubeFace face, int x, int barrierY, int z, int ownTop) {
        if (barrierY < geometry.planeY - DEEP || barrierY >= geometry.maxY) return;
        CubeFace partner = geometry.barrierPartner(face, x, barrierY, z);
        if (partner == null) return;
        BlockState stone = this.generatorSettings().value().defaultBlock();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int k = 1; k <= SHARED + FADE; k++) {
            int y = barrierY + k;
            if (y >= geometry.maxY || y > SKY || y > ownTop + BAND_SLACK) break;
            double weight;
            if (k <= SHARED) {
                weight = 0.5;
            } else {
                double t = 1.0 - (k - SHARED) / (FADE + 1.0);
                weight = 0.5 * t * t * (3.0 - 2.0 * t);
            }
            int[] corner = geometry.cubeMinCorner(face, x, y, z);
            int[] there = geometry.transformCorner(face, partner, corner[0], corner[1], corner[2]);
            double density = (1.0 - weight) * densities.at(corner[0], corner[1], corner[2]) + weight * densities.at(there[0], there[1], there[2]);
            cursor.set(x, y, z);
            BlockState state = chunk.getBlockState(cursor);
            // Fluids too follow one rule on both faces: water below sea level, air above (vanilla's aquifers do not).
            BlockState wanted = density > 0.0 ? (state.isAir() || !state.getFluidState().isEmpty() ? stone : state)
                : y < this.getSeaLevel() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            if (wanted != state) chunk.setBlockState(cursor, wanted, false);
        }
    }

    /**
     * What a barrier cell becomes. Every face storing it (two, or three where faces meet at a corner) must decide
     * alike: it is solid (edge bedrock) where the mean of their terrain densities at the cell's corner with the
     * smallest cube coordinates is, and open (edge air) elsewhere. Each copy sums the same densities in the same
     * order, so they always agree. Deep in the rock it is always solid, high in the sky always open. Edge air is
     * waterlogged where this face has water there.
     */
    private BlockState barrier(CubeGeometry geometry, Densities densities, CubeFace face, int x, int y, int z, BlockState terrain) {
        List<CubeFace> faces = geometry.barrierFaces(face, x, y, z);
        BlockState solid = CubeBlocks.EDGE_BEDROCK.get().defaultBlockState()
            .setValue(EdgeBedrockBlock.PRIMARY, faces.isEmpty() || faces.get(0) == face);
        if (y < geometry.planeY - DEEP) return solid;
        BlockState open = CubeBlocks.EDGE_AIR.get().defaultBlockState()
            .setValue(EdgeAirBlock.WATERLOGGED, terrain.getFluidState().is(FluidTags.WATER));
        if (y > SKY || faces.isEmpty()) return open;
        int[] corner = geometry.cubeMinCorner(face, x, y, z);
        double sum = 0.0;
        for (CubeFace copy : faces) {
            int[] there = geometry.transformCorner(face, copy, corner[0], corner[1], corner[2]);
            sum += densities.at(there[0], there[1], there[2]);
        }
        return sum / faces.size() > 0.0 ? solid : open;
    }

    /**
     * Terrain density at block corners, for the barrier pass. The density function without vanilla's caches costs
     * tens of microseconds a point, too much for every cell of the band, so like vanilla's own fill it is sampled at
     * the corners of the noise cells (on the fill's grid) and interpolated between them, each corner once per chunk.
     * A value depends only on the storage point, so every face asking for the same point gets the same answer.
     */
    public static final class Densities {

        private final DensityFunction density;
        private final int cellWidth;
        private final int cellHeight;
        private final Long2DoubleOpenHashMap corners = new Long2DoubleOpenHashMap();

        public Densities(RandomState random, NoiseSettings noise) {
            this.density = random.router().finalDensity();
            this.cellWidth = noise.getCellWidth();
            this.cellHeight = noise.getCellHeight();
            this.corners.defaultReturnValue(Double.NaN);
        }

        public double at(int x, int y, int z) {
            int x0 = Math.floorDiv(x, this.cellWidth) * this.cellWidth;
            int y0 = Math.floorDiv(y, this.cellHeight) * this.cellHeight;
            int z0 = Math.floorDiv(z, this.cellWidth) * this.cellWidth;
            int x1 = x0 + this.cellWidth, y1 = y0 + this.cellHeight, z1 = z0 + this.cellWidth;
            return Mth.lerp3((double) (x - x0) / this.cellWidth, (double) (y - y0) / this.cellHeight, (double) (z - z0) / this.cellWidth,
                this.corner(x0, y0, z0), this.corner(x1, y0, z0), this.corner(x0, y1, z0), this.corner(x1, y1, z0),
                this.corner(x0, y0, z1), this.corner(x1, y0, z1), this.corner(x0, y1, z1), this.corner(x1, y1, z1));
        }

        private double corner(int x, int y, int z) {
            long key = BlockPos.asLong(x, y, z);
            double value = this.corners.get(key);
            if (Double.isNaN(value)) {
                value = this.density.compute(new DensityFunction.SinglePointContext(x, y, z));
                this.corners.put(key, value);
            }
            return value;
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
