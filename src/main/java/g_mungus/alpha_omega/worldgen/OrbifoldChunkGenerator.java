package g_mungus.alpha_omega.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import g_mungus.alpha_omega.AlphaOmegaMod;
import g_mungus.alpha_omega.config.AlphaOmegaConfig;
import g_mungus.alpha_omega.orbifold.OrbifoldGeometry;
import g_mungus.alpha_omega.orbifold.OrbifoldSettings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The overworld generator of an orbifold world. Its settings are the orbifold's shape, so a world is an orbifold world
 * exactly when its overworld uses this generator.
 *
 * <p>For now it generates like vanilla's noise generator everywhere. Phase 3 ({@code orbifold-implementation.md})
 * makes the tile vanilla terrain, the band and skirt empty until filled from their sources, and the rest void.
 */
public class OrbifoldChunkGenerator extends NoiseBasedChunkGenerator {

    public static final ResourceKey<WorldPreset> PRESET =
        ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath(AlphaOmegaMod.MOD_ID, "orbifold"));

    public static final MapCodec<OrbifoldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
        NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(NoiseBasedChunkGenerator::generatorSettings),
        OrbifoldSettings.fieldsCodec(AlphaOmegaConfig::defaults).forGetter(OrbifoldChunkGenerator::orbifold)
    ).apply(instance, OrbifoldChunkGenerator::new));

    private final OrbifoldSettings orbifold;
    private final OrbifoldGeometry geometry;

    public OrbifoldChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, OrbifoldSettings orbifold) {
        super(biomeSource, settings);
        this.orbifold = orbifold;
        this.geometry = orbifold.geometry();
    }

    public OrbifoldSettings orbifold() {
        return this.orbifold;
    }

    public OrbifoldGeometry geometry() {
        return this.geometry;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
