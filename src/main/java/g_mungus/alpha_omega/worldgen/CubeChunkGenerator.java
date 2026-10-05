package g_mungus.alpha_omega.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.block.CubeBlocks;
import g_mungus.alpha_omega.block.EdgeAirBlock;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.cube.CubeFace;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.FluidTags;
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

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        if (!this.inFootprint(chunk)) return CompletableFuture.completedFuture(chunk);
        return super.fillFromNoise(blender, random, structures, chunk).thenApply(filled -> this.carveFaces(filled, random));
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        if (this.inFootprint(chunk)) super.buildSurface(region, structures, random, chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        if (this.inFootprint(chunk)) super.applyCarvers(region, seed, random, biomes, structures, chunk, step);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        if (this.inFootprint(chunk)) super.applyBiomeDecoration(level, chunk, structures);
    }

    /** Barrier cells this far below the face plane are always bedrock: deep in the rock, nothing to match. */
    private static final int DEEP = 24;
    /** Barrier cells above this height are always edge air: no terrain reaches them. */
    private static final int SKY = 300;

    /** Puts each column's barrier cell in place and fills the rest of the column below it. */
    private ChunkAccess carveFaces(ChunkAccess chunk, RandomState random) {
        CubeGeometry geometry = this.geometry(chunk);
        ChunkPos pos = chunk.getPos();
        CubeFace face = geometry.faceAtChunk(pos.x, pos.z);
        if (face == null) return chunk;
        BlockState filler = CubeBlocks.FILLER.get().defaultBlockState();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = pos.getMinBlockX() + dx, z = pos.getMinBlockZ() + dz;
                int barrierY = geometry.barrierY(face, x, z);
                int fillTop = Math.min(barrierY, geometry.maxY);
                for (int y = geometry.minY; y < fillTop; y++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    section.setBlockState(dx, y & 15, dz, filler, false);
                }
                if (fillTop > geometry.minY) {
                    oceanFloor.update(dx, fillTop - 1, dz, filler);
                    surface.update(dx, fillTop - 1, dz, filler);
                }
                if (barrierY >= geometry.minY && barrierY < geometry.maxY) {
                    cursor.set(x, barrierY, z);
                    BlockState barrier = this.barrier(geometry, random, face, x, barrierY, z, chunk.getBlockState(cursor));
                    chunk.getSection(chunk.getSectionIndex(barrierY)).setBlockState(dx, barrierY & 15, dz, barrier, false);
                    oceanFloor.update(dx, barrierY, dz, barrier);
                    surface.update(dx, barrierY, dz, barrier);
                }
            }
        }
        return chunk;
    }

    /**
     * What a barrier cell becomes. Both faces store it, so both must decide alike: it is solid (bedrock) where the
     * mean of the two faces' terrain densities there is, and open (edge air) elsewhere. Each face evaluates the same
     * two densities, so they always agree. Edge air is waterlogged where this face has water there.
     */
    private BlockState barrier(CubeGeometry geometry, RandomState random, CubeFace face, int x, int y, int z, BlockState terrain) {
        if (y < geometry.planeY - DEEP) return Blocks.BEDROCK.defaultBlockState();
        BlockState open = CubeBlocks.EDGE_AIR.get().defaultBlockState()
            .setValue(EdgeAirBlock.WATERLOGGED, terrain.getFluidState().is(FluidTags.WATER));
        if (y > SKY) return open;
        CubeFace partner = geometry.barrierPartner(face, x, y, z);
        if (partner == null) return Blocks.BEDROCK.defaultBlockState();
        int[] there = geometry.transformBlock(face, partner, x, y, z);
        double density = 0.5 * density(random, x, y, z) + 0.5 * density(random, there[0], there[1], there[2]);
        return density > 0.0 ? Blocks.BEDROCK.defaultBlockState() : open;
    }

    private static double density(RandomState random, int x, int y, int z) {
        return random.router().finalDensity().compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
