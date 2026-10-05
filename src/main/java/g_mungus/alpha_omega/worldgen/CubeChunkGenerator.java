package g_mungus.alpha_omega.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.cube.CubeGeometry;
import g_mungus.alpha_omega.cube.CubeSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The overworld generator of a cube world (design §4.1). Its settings are the cube's shape, so a world is a cube
 * world exactly when its overworld uses this generator.
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

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
